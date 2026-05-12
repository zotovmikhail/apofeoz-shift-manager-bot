package com.apofeoz.backend.service

import com.apofeoz.backend.api.ApiException
import com.apofeoz.backend.api.CreateWorkerRequest
import com.apofeoz.backend.api.PatchWorkerRequest
import com.apofeoz.backend.api.WorkerResponse
import com.apofeoz.backend.data.UserRepository
import com.apofeoz.backend.data.WorkerRepository
import com.apofeoz.backend.data.SessionRepository
import com.apofeoz.backend.domain.Role
import com.apofeoz.backend.domain.SessionStatus
import com.apofeoz.backend.domain.UserStatus
import com.apofeoz.backend.domain.WorkerStatus
import io.ktor.http.*
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class WorkerService(
    private val users: UserRepository,
    private val workers: WorkerRepository,
    private val sessions: SessionRepository,
) {

    suspend fun list(actorId: UUID, role: Role): List<WorkerResponse> {
        val list = when (role) {
            Role.ADMIN -> workers.listAll()
            Role.FOREMAN -> {
                val actor = users.findById(actorId)
                    ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "User not found")
                if (actor.status != UserStatus.ACTIVE) {
                    throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Inactive foreman")
                }
                workers.ensureForemanSelfCard(actor.id, actor.firstName, actor.lastName)
                workers.listByForeman(actorId)
            }
            else -> throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Cannot list workers")
        }
        return list.map { it.toResponse(users) }
    }

    suspend fun create(actorId: UUID, role: Role, req: CreateWorkerRequest): WorkerResponse {
        val foremanId = when (role) {
            Role.ADMIN -> {
                val fid = req.foremanId?.let { uuidFromString(it, "foremanId") }
                    ?: throw ApiException(HttpStatusCode.BadRequest, "validation_error", "foremanId required for admin")
                val f = users.findById(fid)
                    ?: throw ApiException(HttpStatusCode.BadRequest, "not_found", "Foreman not found")
                if (f.role != Role.FOREMAN || f.status != com.apofeoz.backend.domain.UserStatus.ACTIVE) {
                    throw ApiException(HttpStatusCode.BadRequest, "invalid_foreman", "Target user is not an active foreman")
                }
                fid
            }
            else -> throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Cannot create workers")
        }
        if (req.firstName.isBlank() || req.lastName.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "validation_error", "firstName and lastName required")
        }
        val w = workers.insert(
            id = UUID.randomUUID(),
            userId = null,
            foremanId = foremanId,
            firstName = req.firstName.trim(),
            lastName = req.lastName.trim(),
            position = req.position?.trim()?.takeIf { it.isNotEmpty() },
            status = WorkerStatus.ACTIVE,
        )
        return w.toResponse(users)
    }

    suspend fun patch(actorId: UUID, role: Role, workerId: UUID, req: PatchWorkerRequest): WorkerResponse {
        val w = workers.findById(workerId) ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Worker not found")
        when (role) {
            Role.FOREMAN -> {
                if (w.foremanId != actorId) throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Not your worker")
                if (req.foremanId != null) throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Foreman cannot reassign to another foreman via API (use admin)")
            }
            Role.ADMIN -> { /* ok */ }
            else -> throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Cannot patch workers")
        }
        val newForeman = req.foremanId?.let { uuidFromString(it, "foremanId") }?.also { fid ->
            val f = users.findById(fid)
                ?: throw ApiException(HttpStatusCode.BadRequest, "not_found", "Foreman not found")
            if (f.role != Role.FOREMAN || f.status != com.apofeoz.backend.domain.UserStatus.ACTIVE) {
                throw ApiException(HttpStatusCode.BadRequest, "invalid_foreman", "Target is not active foreman")
            }
        }
        val newStatus = req.status?.let { parseWorkerStatus(it) }

        if (newStatus == WorkerStatus.INACTIVE && w.status == WorkerStatus.ACTIVE) {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            sessions.closeAllActiveForWorker(workerId, now)
        }

        val updated = workers.update(workerId, foremanId = newForeman, status = newStatus)
            ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Worker not found")
        return updated.toResponse(users)
    }

    private fun uuidFromString(raw: String, field: String): UUID = try {
        UUID.fromString(raw)
    } catch (_: IllegalArgumentException) {
        throw ApiException(HttpStatusCode.BadRequest, "validation_error", "Invalid $field (expected UUID)")
    }

    private fun parseWorkerStatus(raw: String): WorkerStatus = try {
        WorkerStatus.valueOf(raw)
    } catch (_: IllegalArgumentException) {
        throw ApiException(HttpStatusCode.BadRequest, "validation_error", "Invalid worker status: $raw")
    }

    private suspend fun com.apofeoz.backend.data.WorkerEntity.toResponse(users: UserRepository): WorkerResponse {
        val foreman = users.findById(foremanId)
        val foremanDisplayName = foreman?.let { "${it.firstName} ${it.lastName}" }
        return WorkerResponse(
            id = id.toString(),
            userId = userId?.toString(),
            foremanId = foremanId.toString(),
            foremanDisplayName = foremanDisplayName,
            firstName = firstName,
            lastName = lastName,
            position = position,
            status = status.name,
        )
    }
}
