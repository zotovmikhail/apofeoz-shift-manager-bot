package com.apofeoz.backend.service

import com.apofeoz.backend.api.ApiException
import com.apofeoz.backend.api.PatchUserRequest
import com.apofeoz.backend.api.UserResponse
import com.apofeoz.backend.data.*
import com.apofeoz.backend.domain.Role
import com.apofeoz.backend.domain.SessionStatus
import com.apofeoz.backend.domain.UserStatus
import com.apofeoz.backend.domain.WorkerStatus
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class UserService(
    private val users: UserRepository,
    private val workers: WorkerRepository,
    private val refreshTokens: RefreshTokenRepository,
) {

    suspend fun me(userId: UUID): UserResponse {
        val u = users.findById(userId) ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "User not found")
        return u.toResponse()
    }

    suspend fun listUsers(): List<UserResponse> = users.listAll().map { it.toResponse() }

    suspend fun patchUser(actorId: UUID, targetId: UUID, req: PatchUserRequest): UserResponse {
        if (actorId != targetId) {
            val actor = users.findById(actorId) ?: throw forbidden()
            if (actor.role != Role.ADMIN) throw forbidden()
        } else {
            if (req.role != null || req.status != null) {
                throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Cannot change own role or status")
            }
        }
        val target = users.findById(targetId) ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "User not found")

        val newRole = req.role?.let { parseRole(it) }
        val newStatus = req.status?.let { parseUserStatus(it) }

        if (actorId == targetId) {
            val updated = users.update(targetId, firstName = req.firstName, lastName = req.lastName)
                ?: target
            syncLinkedWorkerNames(targetId, req)
            return updated.toResponse()
        }

        assertLastAdminRule(target, newRole, newStatus)

        val wasForeman = target.role == Role.FOREMAN && target.status == UserStatus.ACTIVE
        val becomesNonForeman = newRole != null && newRole != Role.FOREMAN
        val blocked = newStatus == UserStatus.INACTIVE

        if (wasForeman && (becomesNonForeman || blocked)) {
            ensureCanDemoteForeman(targetId)
            applyForemanDemotionSideEffects(targetId)
        }

        if (blocked && target.role == Role.ADMIN) {
            ensureLastAdmin(targetId)
        }

        val finalRole = newRole ?: target.role
        var after = users.update(
            targetId,
            role = newRole,
            status = newStatus,
            firstName = req.firstName,
            lastName = req.lastName,
        ) ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "User not found")

        if (after.role == Role.FOREMAN && after.status == UserStatus.ACTIVE) {
            ensureForemanWorkerCard(after)
            after = users.findById(targetId)!!
        }

        if (newStatus == UserStatus.INACTIVE || (newRole != null && newRole != target.role)) {
            refreshTokens.revokeAllForUser(targetId)
        }

        syncLinkedWorkerNames(targetId, req)
        return after.toResponse()
    }

    private suspend fun syncLinkedWorkerNames(userId: UUID, req: PatchUserRequest) {
        if (req.firstName == null && req.lastName == null) return
        workers.updateNamesForLinkedUser(userId, req.firstName, req.lastName)
    }

    private fun parseRole(raw: String): Role = try {
        Role.valueOf(raw)
    } catch (_: IllegalArgumentException) {
        throw ApiException(HttpStatusCode.BadRequest, "validation_error", "Invalid role: $raw")
    }

    private fun parseUserStatus(raw: String): UserStatus = try {
        UserStatus.valueOf(raw)
    } catch (_: IllegalArgumentException) {
        throw ApiException(HttpStatusCode.BadRequest, "validation_error", "Invalid status: $raw")
    }

    private suspend fun assertLastAdminRule(target: com.apofeoz.backend.data.UserEntity, newRole: Role?, newStatus: UserStatus?) {
        val isActiveAdmin = target.role == Role.ADMIN && target.status == UserStatus.ACTIVE
        if (!isActiveAdmin) return
        val removesAdmin = (newRole != null && newRole != Role.ADMIN) ||
            (newStatus == UserStatus.INACTIVE)
        if (removesAdmin) {
            ensureLastAdmin(target.id)
        }
    }

    private suspend fun ensureLastAdmin(excludeUserId: UUID) {
        val others = users.countActiveAdmins(excludeUserId)
        if (others == 0) {
            throw ApiException(
                HttpStatusCode.Conflict,
                "last_admin_protected",
                "Cannot remove or deactivate the last active administrator",
            )
        }
    }

    private suspend fun ensureCanDemoteForeman(foremanUserId: UUID) {
        val ids = workers.listActiveSubordinateWorkerIds(foremanUserId)
        if (ids.isEmpty()) return
        throw ApiException(
            HttpStatusCode.Conflict,
            "foreman_has_workers",
            "Reassign active workers to another foreman first",
            payload = mapOf(
                "workerIds" to JsonArray(ids.map { JsonPrimitive(it.toString()) }),
            ),
        )
    }

    private suspend fun applyForemanDemotionSideEffects(foremanUserId: UUID) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        // Одна транзакция: закрытие смен и отвязка карточки бригадира.
        newSuspendedTransaction(Dispatchers.IO) {
            Sessions.update({
                (Sessions.foremanId eq foremanUserId) and (Sessions.status eq SessionStatus.ACTIVE.name)
            }) {
                it[Sessions.endAt] = now
                it[Sessions.status] = SessionStatus.CLOSED.name
                it[Sessions.updatedAt] = now
            }
            Workers.update({
                (Workers.userId eq EntityID(foremanUserId, Users)) and
                    (Workers.foremanId eq EntityID(foremanUserId, Users))
            }) {
                it[Workers.userId] = null
                it[Workers.status] = WorkerStatus.INACTIVE.name
                it[Workers.updatedAt] = now
            }
        }
    }

    private suspend fun ensureForemanWorkerCard(u: com.apofeoz.backend.data.UserEntity) {
        workers.ensureForemanSelfCard(u.id, u.firstName, u.lastName)
    }

    private fun forbidden(): Nothing =
        throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Insufficient permissions")

    private fun com.apofeoz.backend.data.UserEntity.toResponse() = UserResponse(
        id = id.toString(),
        email = email,
        phone = phone,
        firstName = firstName,
        lastName = lastName,
        role = role.name,
        status = status.name,
    )
}
