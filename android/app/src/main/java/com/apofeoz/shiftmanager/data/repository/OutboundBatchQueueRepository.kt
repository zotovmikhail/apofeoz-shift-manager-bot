package com.apofeoz.shiftmanager.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.apofeoz.shiftmanager.core.di.AppContainer
import com.apofeoz.shiftmanager.data.local.LocalFailedBatchEntity
import com.apofeoz.shiftmanager.data.local.OutboundBatchEntity
import com.apofeoz.shiftmanager.data.local.ShiftDatabase
import com.apofeoz.shiftmanager.data.remote.dto.SyncBatchRequestDto
import com.apofeoz.shiftmanager.work.OutboundSyncScheduler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OutboundBatchQueueRepository(
    private val context: Context,
    private val db: ShiftDatabase,
    private val json: Json,
) {
    private val dao get() = db.outboundDao()

    suspend fun claimNextBatch(): OutboundBatchEntity? = db.withTransaction {
        val ownerUserId = AppContainer.cachedUserRepository.get()?.id ?: return@withTransaction null
        val id = dao.findFirstPendingIdForOwner(ownerUserId) ?: return@withTransaction null
        if (dao.markInFlight(id, OffsetDateTime.now(ZoneOffset.UTC).toString()) == 0) return@withTransaction null
        dao.getById(id)
    }

    suspend fun enqueue(batch: SyncBatchRequestDto) {
        val cachedUser = AppContainer.cachedUserRepository.get()
        val device = AppContainer.deviceRepository.getDeviceInfo()
        val metadata = extractMetadata(batch)
        val enriched = batch.copy(
            deviceId = device.deviceId,
            appVersion = device.appVersion,
            platform = device.platform,
            deviceModel = device.deviceModel,
            osVersion = device.osVersion,
        )
        val body = json.encodeToString(SyncBatchRequestDto.serializer(), enriched)
        dao.insert(
            OutboundBatchEntity(
                batchUid = enriched.batchUid,
                submittedAt = enriched.submittedAt,
                bodyJson = body,
                ownerUserId = cachedUser?.id,
                workerId = metadata.workerId,
                sessionId = metadata.sessionId,
                eventTypes = metadata.eventTypes,
                deviceId = device.deviceId,
                appVersion = device.appVersion,
            ),
        )
        OutboundSyncScheduler.schedule(context)
    }

    suspend fun pendingCount(): Int = dao.countPending()

    suspend fun blockedAuthCount(): Int = dao.countBlockedAuth()

    suspend fun unblockAuthForCurrentUser(): Int {
        val ownerUserId = AppContainer.cachedUserRepository.get()?.id ?: return 0
        return dao.unblockAuthForOwner(ownerUserId)
    }

    suspend fun quarantinePendingForWorkerAfter(
        afterId: Long,
        workerId: String,
        httpCode: Int,
        message: String,
        reason: String,
    ): Int = db.withTransaction {
        if (workerId.isBlank()) return@withTransaction 0
        val rows = dao.listPendingAfter(afterId)
        val matched = rows.filter { row ->
            row.workerId == workerId || row.workerId.isNullOrBlank() && bodyBelongsToWorker(row.bodyJson, workerId)
        }
        if (matched.isNotEmpty()) {
            db.localFailedDao().insertAll(
                matched.map { row ->
                    LocalFailedBatchEntity(
                        httpCode = httpCode,
                        message = message,
                        submittedAt = row.submittedAt,
                        bodyJson = row.bodyJson,
                        reason = reason,
                        failedEventType = row.eventTypes,
                        createdAt = OffsetDateTime.now(ZoneOffset.UTC).toString(),
                    )
                },
            )
            dao.deleteByIds(matched.map { it.id })
        }
        matched.size
    }

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

    private suspend fun extractMetadata(batch: SyncBatchRequestDto): BatchMetadata {
        val eventTypes = batch.events.joinToString(",") { it.type }.ifBlank { null }
        val workerIds = batch.events.mapNotNull { eventWorkerId(it.type, it.payload) }.distinct()
        val sessionIds = batch.events.mapNotNull { eventSessionId(it.payload) }.distinct()
        val resolvedWorkerId = workerIds.singleOrNull()
            ?: sessionIds.firstNotNullOfOrNull { sessionId -> workerIdForSession(sessionId) }
        return BatchMetadata(
            workerId = resolvedWorkerId,
            sessionId = sessionIds.singleOrNull(),
            eventTypes = eventTypes,
        )
    }

    private suspend fun workerIdForSession(sessionId: String): String? {
        val local = AppContainer.sessionStateRepository.getActiveSessions().firstOrNull { it.sessionId == sessionId }?.workerId
        if (!local.isNullOrBlank()) return local
        return AppContainer.activeSessionsCache.get().byWorkerId.entries.firstOrNull { it.value == sessionId }?.key
    }

    private fun bodyBelongsToWorker(bodyJson: String, workerId: String): Boolean {
        val dto = runCatching { json.decodeFromString(SyncBatchRequestDto.serializer(), bodyJson) }.getOrNull() ?: return false
        return dto.events.any { eventWorkerId(it.type, it.payload) == workerId }
    }

    private fun eventWorkerId(type: String, payload: JsonElement): String? =
        when (type) {
            "START_SESSION" -> runCatching { payload.jsonObject["workerId"]?.jsonPrimitive?.content }.getOrNull()
            else -> null
        }?.takeIf { it.isNotBlank() }

    private fun eventSessionId(payload: JsonElement): String? =
        runCatching { payload.jsonObject["sessionId"]?.jsonPrimitive?.content }.getOrNull()?.takeIf { it.isNotBlank() }

    private data class BatchMetadata(
        val workerId: String?,
        val sessionId: String?,
        val eventTypes: String?,
    )
}
