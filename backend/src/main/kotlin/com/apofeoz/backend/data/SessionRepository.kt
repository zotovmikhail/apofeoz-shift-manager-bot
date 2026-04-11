package com.apofeoz.backend.data

import com.apofeoz.backend.domain.SessionStatus
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class SessionRepository {

    private fun ResultRow.toSession() = SessionEntity(
        id = this[Sessions.id].value,
        workerId = this[Sessions.workerId].value,
        foremanId = this[Sessions.foremanId].value,
        startAt = this[Sessions.startAt],
        endAt = this[Sessions.endAt],
        status = SessionStatus.valueOf(this[Sessions.status]),
        createdAt = this[Sessions.createdAt],
        updatedAt = this[Sessions.updatedAt],
    )

    suspend fun insert(
        id: UUID,
        workerId: UUID,
        foremanId: UUID,
        startAt: OffsetDateTime,
        status: SessionStatus,
    ): SessionEntity = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Sessions.insert {
            it[Sessions.id] = id
            it[Sessions.workerId] = EntityID(workerId, Workers)
            it[Sessions.foremanId] = EntityID(foremanId, Users)
            it[Sessions.startAt] = startAt
            it[Sessions.endAt] = null
            it[Sessions.status] = status.name
            it[Sessions.createdAt] = now
            it[Sessions.updatedAt] = now
        }
        Sessions.selectAll().where { Sessions.id eq id }.single().toSession()
    }

    suspend fun findById(id: UUID): SessionEntity? = newSuspendedTransaction(Dispatchers.IO) {
        Sessions.selectAll().where { Sessions.id eq id }.map { it.toSession() }.singleOrNull()
    }

    suspend fun findActiveByWorker(workerId: UUID): SessionEntity? = newSuspendedTransaction(Dispatchers.IO) {
        Sessions.selectAll().where {
            (Sessions.workerId eq EntityID(workerId, Workers)) and (Sessions.status eq SessionStatus.ACTIVE.name)
        }.map { it.toSession() }.singleOrNull()
    }

    suspend fun closeSession(id: UUID, endAt: OffsetDateTime) = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Sessions.update({ Sessions.id eq id }) {
            it[Sessions.endAt] = endAt
            it[Sessions.status] = SessionStatus.CLOSED.name
            it[Sessions.updatedAt] = now
        }
    }

    suspend fun closeAllActiveForForeman(foremanId: UUID, endAt: OffsetDateTime) = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Sessions.update({
            (Sessions.foremanId eq EntityID(foremanId, Users)) and (Sessions.status eq SessionStatus.ACTIVE.name)
        }) {
            it[Sessions.endAt] = endAt
            it[Sessions.status] = SessionStatus.CLOSED.name
            it[Sessions.updatedAt] = now
        }
    }

    suspend fun closeAllActiveForWorker(workerId: UUID, endAt: OffsetDateTime) = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Sessions.update({
            (Sessions.workerId eq EntityID(workerId, Workers)) and (Sessions.status eq SessionStatus.ACTIVE.name)
        }) {
            it[Sessions.endAt] = endAt
            it[Sessions.status] = SessionStatus.CLOSED.name
            it[Sessions.updatedAt] = now
        }
    }

    suspend fun listActiveByForeman(foremanId: UUID): List<SessionEntity> = newSuspendedTransaction(Dispatchers.IO) {
        Sessions.selectAll().where {
            (Sessions.foremanId eq EntityID(foremanId, Users)) and (Sessions.status eq SessionStatus.ACTIVE.name)
        }.map { it.toSession() }
    }

    suspend fun sumClosedHoursForWorkerOnDay(
        workerId: UUID,
        dayStart: OffsetDateTime,
        dayEnd: OffsetDateTime,
    ): Double = newSuspendedTransaction(Dispatchers.IO) {
        val rows = Sessions.selectAll().where {
            (Sessions.workerId eq EntityID(workerId, Workers)) and
                (Sessions.status eq SessionStatus.CLOSED.name) and
                (Sessions.endAt.isNotNull()) and
                (Sessions.endAt greaterEq dayStart) and
                (Sessions.endAt less dayEnd)
        }
        var totalSeconds = 0.0
        for (r in rows) {
            val start = r[Sessions.startAt]
            val end = r[Sessions.endAt]!!
            totalSeconds += java.time.Duration.between(start, end).seconds.toDouble()
        }
        // Round minutes for reporting simplicity.
        val roundedSeconds = kotlin.math.round(totalSeconds / 60.0) * 60.0
        roundedSeconds / 3600.0
    }

    suspend fun sumClosedHoursForWorkerInRange(
        workerId: UUID,
        startInclusive: OffsetDateTime,
        endExclusive: OffsetDateTime,
    ): Double = newSuspendedTransaction(Dispatchers.IO) {
        val rows = Sessions.selectAll().where {
            (Sessions.workerId eq EntityID(workerId, Workers)) and
                (Sessions.status eq SessionStatus.CLOSED.name) and
                (Sessions.endAt.isNotNull()) and
                (Sessions.endAt greaterEq startInclusive) and
                (Sessions.endAt less endExclusive)
        }
        var totalSeconds = 0.0
        for (r in rows) {
            val start = r[Sessions.startAt]
            val end = r[Sessions.endAt]!!
            totalSeconds += java.time.Duration.between(start, end).seconds.toDouble()
        }
        val roundedSeconds = kotlin.math.round(totalSeconds / 60.0) * 60.0
        roundedSeconds / 3600.0
    }

    /** Закрытые смены, пересекающиеся с полуинтервалом [startInclusive, endExclusive) по времени UTC. */
    suspend fun listClosedSessionsOverlapping(
        startInclusive: OffsetDateTime,
        endExclusive: OffsetDateTime,
    ): List<SessionEntity> = newSuspendedTransaction(Dispatchers.IO) {
        Sessions.selectAll().where {
            (Sessions.status eq SessionStatus.CLOSED.name) and
                Sessions.endAt.isNotNull() and
                (Sessions.startAt less endExclusive) and
                (Sessions.endAt greater startInclusive)
        }.map { it.toSession() }
    }
}
