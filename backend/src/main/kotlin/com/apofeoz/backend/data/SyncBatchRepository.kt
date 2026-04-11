package com.apofeoz.backend.data

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class SyncBatchRepository {

    suspend fun findResultByBatchUid(batchUid: String): String? = newSuspendedTransaction(Dispatchers.IO) {
        SyncBatches.selectAll().where { SyncBatches.batchUid eq batchUid }
            .map { it[SyncBatches.resultJson] }
            .singleOrNull()
    }

    suspend fun insertBatchWithEvents(
        batchUid: String,
        userId: UUID,
        submittedAt: OffsetDateTime,
        events: List<Pair<String, String>>, // type, payload json
        resultJson: String,
    ) = newSuspendedTransaction(Dispatchers.IO) {
        val batchId = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        SyncBatches.insert {
            it[SyncBatches.id] = batchId
            it[SyncBatches.userId] = EntityID(userId, Users)
            it[SyncBatches.batchUid] = batchUid
            it[SyncBatches.submittedAt] = submittedAt
            it[SyncBatches.appliedAt] = now
            it[SyncBatches.resultJson] = resultJson
        }
        for ((type, payload) in events) {
            val evId = UUID.randomUUID()
            SyncEvents.insert {
                it[SyncEvents.id] = evId
                it[SyncEvents.batchId] = EntityID(batchId, SyncBatches)
                it[SyncEvents.type] = type
                it[SyncEvents.payload] = payload
                it[SyncEvents.createdAt] = now
            }
        }
    }

    suspend fun insertFailed(
        userId: UUID,
        batchUid: String,
        submittedAt: OffsetDateTime,
        eventsSnapshot: String,
        failedIndex: Int,
        reason: String,
    ) = newSuspendedTransaction(Dispatchers.IO) {
        val id = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        FailedSyncBatches.insert {
            it[FailedSyncBatches.id] = id
            it[FailedSyncBatches.userId] = EntityID(userId, Users)
            it[FailedSyncBatches.batchUid] = batchUid
            it[FailedSyncBatches.submittedAt] = submittedAt
            it[FailedSyncBatches.eventsSnapshot] = eventsSnapshot
            it[FailedSyncBatches.failedIndex] = failedIndex
            it[FailedSyncBatches.reason] = reason
            it[FailedSyncBatches.createdAt] = now
        }
    }

    suspend fun listFailed(userId: UUID): List<FailedBatchRow> = newSuspendedTransaction(Dispatchers.IO) {
        FailedSyncBatches.selectAll().where { FailedSyncBatches.userId eq EntityID(userId, Users) }
            .orderBy(FailedSyncBatches.createdAt, SortOrder.DESC)
            .map {
                FailedBatchRow(
                    id = it[FailedSyncBatches.id].value,
                    batchUid = it[FailedSyncBatches.batchUid],
                    submittedAt = it[FailedSyncBatches.submittedAt],
                    failedIndex = it[FailedSyncBatches.failedIndex],
                    reason = it[FailedSyncBatches.reason],
                    eventsSnapshot = it[FailedSyncBatches.eventsSnapshot],
                )
            }
    }

    suspend fun deleteFailed(id: UUID, userId: UUID): Boolean = newSuspendedTransaction(Dispatchers.IO) {
        FailedSyncBatches.deleteWhere {
            (FailedSyncBatches.id eq id) and (FailedSyncBatches.userId eq EntityID(userId, Users))
        } > 0
    }

    suspend fun findFailedById(id: UUID, userId: UUID): FailedBatchRow? = newSuspendedTransaction(Dispatchers.IO) {
        FailedSyncBatches.selectAll().where {
            (FailedSyncBatches.id eq id) and (FailedSyncBatches.userId eq EntityID(userId, Users))
        }.map {
            FailedBatchRow(
                id = it[FailedSyncBatches.id].value,
                batchUid = it[FailedSyncBatches.batchUid],
                submittedAt = it[FailedSyncBatches.submittedAt],
                failedIndex = it[FailedSyncBatches.failedIndex],
                reason = it[FailedSyncBatches.reason],
                eventsSnapshot = it[FailedSyncBatches.eventsSnapshot],
            )
        }.singleOrNull()
    }
}

data class FailedBatchRow(
    val id: UUID,
    val batchUid: String,
    val submittedAt: OffsetDateTime,
    val failedIndex: Int,
    val reason: String,
    val eventsSnapshot: String,
)
