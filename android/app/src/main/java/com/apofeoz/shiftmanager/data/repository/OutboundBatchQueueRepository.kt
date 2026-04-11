package com.apofeoz.shiftmanager.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.apofeoz.shiftmanager.data.local.OutboundBatchEntity
import com.apofeoz.shiftmanager.data.local.ShiftDatabase
import com.apofeoz.shiftmanager.data.remote.dto.SyncBatchRequestDto
import com.apofeoz.shiftmanager.work.OutboundSyncScheduler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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
}
