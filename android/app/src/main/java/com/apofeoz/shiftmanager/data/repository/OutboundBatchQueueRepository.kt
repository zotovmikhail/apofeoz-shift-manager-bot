package com.apofeoz.shiftmanager.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.apofeoz.shiftmanager.data.local.OutboundBatchEntity
import com.apofeoz.shiftmanager.data.local.ShiftDatabase
import com.apofeoz.shiftmanager.data.remote.dto.SyncBatchRequestDto
import com.apofeoz.shiftmanager.work.OutboundSyncScheduler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class OutboundBatchQueueRepository(
    private val context: Context,
    private val db: ShiftDatabase,
    private val json: Json,
) {
    private val dao get() = db.outboundDao()

    suspend fun claimNextBatch(): OutboundBatchEntity? = db.withTransaction {
        val id = dao.findFirstPendingId() ?: return@withTransaction null
        if (dao.markInFlight(id) == 0) return@withTransaction null
        dao.getById(id)
    }

    suspend fun enqueue(batch: SyncBatchRequestDto) {
        val body = json.encodeToString(SyncBatchRequestDto.serializer(), batch)
        dao.insert(
            OutboundBatchEntity(
                batchUid = batch.batchUid,
                submittedAt = batch.submittedAt,
                bodyJson = body,
            ),
        )
        OutboundSyncScheduler.schedule(context)
    }

    suspend fun pendingCount(): Int = dao.countPending()

    /**
     * Returns true when queue still contains START_SESSION for this worker/session.
     * Used by UI reconciliation to keep optimistic local state only while start event is unsent.
     */
    suspend fun hasPendingStartFor(workerId: String, sessionId: String): Boolean {
        if (workerId.isBlank() || sessionId.isBlank()) return false
        val bodies = dao.listOpenBatchBodies()
        for (bodyJson in bodies) {
            val dto = runCatching { json.decodeFromString(SyncBatchRequestDto.serializer(), bodyJson) }.getOrNull() ?: continue
            val hasMatch = dto.events.any { ev ->
                if (ev.type != "START_SESSION") return@any false
                val payload = ev.payload.jsonObject
                val wid = payload["workerId"]?.jsonPrimitive?.content
                val sid = payload["sessionId"]?.jsonPrimitive?.content
                wid == workerId && sid == sessionId
            }
            if (hasMatch) return true
        }
        return false
    }
}
