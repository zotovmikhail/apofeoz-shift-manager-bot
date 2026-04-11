package com.apofeoz.backend.data

import com.apofeoz.backend.domain.WorkerStatus
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class WorkerRepository {

    private fun ResultRow.toWorker() = WorkerEntity(
        id = this[Workers.id].value,
        userId = this[Workers.userId]?.value,
        foremanId = this[Workers.foremanId].value,
        firstName = this[Workers.firstName],
        lastName = this[Workers.lastName],
        position = this[Workers.position],
        status = WorkerStatus.valueOf(this[Workers.status]),
        createdAt = this[Workers.createdAt],
        updatedAt = this[Workers.updatedAt],
    )

    suspend fun insert(
        id: UUID,
        userId: UUID?,
        foremanId: UUID,
        firstName: String,
        lastName: String,
        position: String?,
        status: WorkerStatus,
    ): WorkerEntity = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Workers.insert {
            it[Workers.id] = id
            it[Workers.userId] = userId?.let { uid -> EntityID(uid, Users) }
            it[Workers.foremanId] = EntityID(foremanId, Users)
            it[Workers.firstName] = firstName
            it[Workers.lastName] = lastName
            it[Workers.position] = position
            it[Workers.status] = status.name
            it[Workers.createdAt] = now
            it[Workers.updatedAt] = now
        }
        Workers.selectAll().where { Workers.id eq id }.single().toWorker()
    }

    suspend fun findById(id: UUID): WorkerEntity? = newSuspendedTransaction(Dispatchers.IO) {
        Workers.selectAll().where { Workers.id eq id }.map { it.toWorker() }.singleOrNull()
    }

    suspend fun findByUserId(userId: UUID): WorkerEntity? = newSuspendedTransaction(Dispatchers.IO) {
        Workers.selectAll().where { Workers.userId eq EntityID(userId, Users) }.map { it.toWorker() }.singleOrNull()
    }

    suspend fun listByForeman(foremanId: UUID): List<WorkerEntity> = newSuspendedTransaction(Dispatchers.IO) {
        Workers.selectAll().where { Workers.foremanId eq EntityID(foremanId, Users) }
            .orderBy(Workers.lastName, SortOrder.ASC)
            .map { it.toWorker() }
    }

    suspend fun listAll(): List<WorkerEntity> = newSuspendedTransaction(Dispatchers.IO) {
        Workers.selectAll().orderBy(Workers.lastName, SortOrder.ASC).map { it.toWorker() }
    }

    suspend fun listActive(): List<WorkerEntity> = newSuspendedTransaction(Dispatchers.IO) {
        Workers.selectAll().where { Workers.status eq WorkerStatus.ACTIVE.name }
            .orderBy(Workers.lastName, SortOrder.ASC)
            .map { it.toWorker() }
    }

    suspend fun countActiveSubordinates(foremanUserId: UUID): Int = newSuspendedTransaction(Dispatchers.IO) {
        Workers.selectAll().where {
            (Workers.foremanId eq EntityID(foremanUserId, Users)) and
                (Workers.status eq WorkerStatus.ACTIVE.name) and
                ((Workers.userId.isNull()) or (Workers.userId neq EntityID(foremanUserId, Users)))
        }.count().toInt()
    }

    /** Активные подчинённые, не считая собственную карточку бригадира (`user_id = foreman`). */
    suspend fun listActiveSubordinateWorkerIds(foremanUserId: UUID): List<UUID> = newSuspendedTransaction(Dispatchers.IO) {
        Workers.selectAll().where {
            (Workers.foremanId eq EntityID(foremanUserId, Users)) and
                (Workers.status eq WorkerStatus.ACTIVE.name) and
                ((Workers.userId.isNull()) or (Workers.userId neq EntityID(foremanUserId, Users)))
        }.map { it[Workers.id].value }
    }

    suspend fun update(
        id: UUID,
        foremanId: UUID? = null,
        status: WorkerStatus? = null,
    ): WorkerEntity? = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val n = Workers.update({ Workers.id eq id }) {
            foremanId?.let { f -> it[Workers.foremanId] = EntityID(f, Users) }
            status?.let { s -> it[Workers.status] = s.name }
            it[Workers.updatedAt] = now
        }
        if (n == 0) null else Workers.selectAll().where { Workers.id eq id }.singleOrNull()?.toWorker()
    }

    suspend fun unlinkUserFromForemanWorker(userId: UUID) = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Workers.update({
            (Workers.userId eq EntityID(userId, Users)) and (Workers.foremanId eq EntityID(userId, Users))
        }) {
            it[Workers.userId] = null
            it[Workers.status] = WorkerStatus.INACTIVE.name
            it[Workers.updatedAt] = now
        }
    }

    /** После понижения бригадира: восстановить при повторном назначении FOREMAN. */
    /** Обновить ФИО у строки `workers`, привязанной к аккаунту (карточка бригадира). */
    suspend fun updateNamesForLinkedUser(
        userId: UUID,
        firstName: String?,
        lastName: String?,
    ) = newSuspendedTransaction(Dispatchers.IO) {
        if (firstName == null && lastName == null) return@newSuspendedTransaction
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Workers.update({ Workers.userId eq EntityID(userId, Users) }) {
            firstName?.let { f -> it[Workers.firstName] = f.trim() }
            lastName?.let { l -> it[Workers.lastName] = l.trim() }
            it[Workers.updatedAt] = now
        }
    }

    /** Повторное назначение FOREMAN: активировать сохранённую карточку и выровнять ФИО с `users`. */
    suspend fun reactivateForemanSelfCard(id: UUID, firstName: String, lastName: String) =
        newSuspendedTransaction(Dispatchers.IO) {
            val now = OffsetDateTime.now(ZoneOffset.UTC)
            Workers.update({ Workers.id eq id }) {
                it[Workers.status] = WorkerStatus.ACTIVE.name
                it[Workers.firstName] = firstName
                it[Workers.lastName] = lastName
                it[Workers.updatedAt] = now
            }
        }

    suspend fun reattachInactiveForemanSelfWorker(
        foremanUserId: UUID,
        firstName: String,
        lastName: String,
    ): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        val row = Workers.selectAll().where {
            (Workers.foremanId eq EntityID(foremanUserId, Users)) and
                (Workers.userId.isNull()) and
                (Workers.status eq WorkerStatus.INACTIVE.name)
        }.orderBy(Workers.updatedAt, SortOrder.DESC).firstOrNull() ?: return@newSuspendedTransaction false
        val wid = row[Workers.id].value
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Workers.update({ Workers.id eq wid }) {
            it[Workers.userId] = EntityID(foremanUserId, Users)
            it[Workers.firstName] = firstName
            it[Workers.lastName] = lastName
            it[Workers.status] = WorkerStatus.ACTIVE.name
            it[Workers.updatedAt] = now
        }
        true
    }
}
