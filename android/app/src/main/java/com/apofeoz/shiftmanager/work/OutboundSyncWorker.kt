package com.apofeoz.shiftmanager.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apofeoz.shiftmanager.core.di.AppContainer
import com.apofeoz.shiftmanager.data.local.LocalFailedBatch
import com.apofeoz.shiftmanager.data.remote.dto.ErrorResponseDto
import com.apofeoz.shiftmanager.data.remote.dto.SyncBatchRequestDto
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.time.OffsetDateTime
import java.time.ZoneOffset

class OutboundSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun doWork(): Result {
        if (AppContainer.testConnectivityOverride.forceOfflineFlow.first()) {
            return Result.success()
        }
        val db = AppContainer.database
        val api = AppContainer.api
        val dao = db.outboundDao()
        val queue = AppContainer.batchQueue
        val syncStatus = AppContainer.syncStatusRepository
        val sessions = AppContainer.sessionStateRepository
        val pendingActions = AppContainer.pendingSessionActions
        val failedLocal = AppContainer.localFailedBatches
        val row = queue.claimNextBatch() ?: return Result.success()
        return try {
            val body = json.decodeFromString(SyncBatchRequestDto.serializer(), row.bodyJson)
            api.syncBatch(body)
            dao.deleteById(row.id)
            // Apply local side-effects after server accepted the batch:
            // - END_SESSION removes local active session
            body.events.forEach { ev ->
                when (ev.type) {
                    "END_SESSION" -> {
                        val sid = ev.payload.jsonObject["sessionId"]?.jsonPrimitive?.content
                        if (!sid.isNullOrBlank()) {
                            sessions.removeBySessionId(sid)
                            pendingActions.removeEnding(sid)
                            pendingActions.clearBlockedForSession(sid)
                        }
                    }
                    "START_SESSION" -> {
                        val wid = ev.payload.jsonObject["workerId"]?.jsonPrimitive?.content
                        if (!wid.isNullOrBlank()) pendingActions.clearBlockedForWorker(wid)
                    }
                }
            }
            syncStatus.setLastSyncAt(OffsetDateTime.now(ZoneOffset.UTC))
            if (dao.countPending() > 0) {
                OutboundSyncScheduler.schedule(applicationContext)
            }
            Result.success()
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> {
                    // Auth is invalid; keep batch pending, force re-login.
                    dao.markBlockedAuth(row.id, e.code(), e.message())
                    AppContainer.notifySessionExpired()
                    Result.failure()
                }
                409 -> {
                    val details = parseError(e)
                    val body = runCatching { json.decodeFromString(SyncBatchRequestDto.serializer(), row.bodyJson) }.getOrNull()
                    val failedIndex = details?.details?.get("failedEventIndex")?.jsonPrimitive?.content?.toIntOrNull()
                    val failedEventType = details?.details?.get("failedEventType")?.jsonPrimitive?.content
                    val reason = details?.details?.get("reason")?.jsonPrimitive?.content ?: details?.message ?: e.message()
                    val failedEvent = failedIndex?.let { idx -> body?.events?.getOrNull(idx) }
                    val failedWorkerId = if (failedEvent == null) null else workerIdForEvent(failedEvent)
                    if (failedEvent != null) {
                        blockAffectedEntity(failedEvent)
                    }
                    failedLocal.add(
                        LocalFailedBatch(
                            httpCode = e.code(),
                            message = e.message(),
                            submittedAt = row.submittedAt,
                            bodyJson = row.bodyJson,
                            failedIndex = failedIndex,
                            reason = reason,
                            failedEventType = failedEventType ?: failedEvent?.type,
                        ),
                    )
                    quarantinePendingWorkerBatches(
                        afterId = row.id,
                        workerId = failedWorkerId,
                        httpCode = e.code(),
                        message = "Отложено из-за предыдущего конфликта",
                        reason = "blocked_by_previous_conflict: $reason",
                    )
                    dao.deleteById(row.id)
                    syncStatus.setLastSyncAt(OffsetDateTime.now(ZoneOffset.UTC))
                    if (dao.countPending() > 0) {
                        OutboundSyncScheduler.schedule(applicationContext)
                    }
                    Result.success()
                }
                400, 403 -> {
                    // Fatal: can't apply automatically. Save locally, block affected worker/session, and remove from queue
                    val body = runCatching { json.decodeFromString(SyncBatchRequestDto.serializer(), row.bodyJson) }.getOrNull()
                    val failedWorkerId = body?.let { workerIdForEvents(it.events) }
                    if (body != null) {
                        body.events.forEach { ev ->
                            blockAffectedEntity(ev)
                        }
                    }
                    val details = parseError(e)
                    failedLocal.add(
                        LocalFailedBatch(
                            httpCode = e.code(),
                            message = e.message(),
                            submittedAt = row.submittedAt,
                            bodyJson = row.bodyJson,
                            reason = details?.message ?: e.message(),
                        ),
                    )
                    quarantinePendingWorkerBatches(
                        afterId = row.id,
                        workerId = failedWorkerId,
                        httpCode = e.code(),
                        message = "Отложено из-за предыдущей ошибки",
                        reason = "blocked_by_previous_error: ${details?.message ?: e.message()}",
                    )
                    dao.deleteById(row.id)
                    if (dao.countPending() > 0) {
                        OutboundSyncScheduler.schedule(applicationContext)
                    }
                    Result.success()
                }
                in 500..599 -> {
                    dao.resetPending(row.id)
                    Result.retry()
                }
                else -> {
                    dao.resetPending(row.id)
                    Result.failure()
                }
            }
        } catch (_: Exception) {
            dao.resetPending(row.id)
            Result.retry()
        }
    }

    private suspend fun blockAffectedEntity(ev: com.apofeoz.shiftmanager.data.remote.dto.SyncEventDto) {
        val pendingActions = AppContainer.pendingSessionActions
        when (ev.type) {
            "START_SESSION" -> {
                val wid = workerIdForEvent(ev)
                if (!wid.isNullOrBlank()) pendingActions.addBlockedWorker(wid)
            }
            "END_SESSION" -> {
                val sid = ev.payload.jsonObject["sessionId"]?.jsonPrimitive?.content
                if (!sid.isNullOrBlank()) pendingActions.addBlockedSession(sid)
            }
        }
    }

    private suspend fun quarantinePendingWorkerBatches(
        afterId: Long,
        workerId: String?,
        httpCode: Int,
        message: String,
        reason: String,
    ) {
        if (workerId.isNullOrBlank()) return
        AppContainer.pendingSessionActions.addBlockedWorker(workerId)
        AppContainer.batchQueue.quarantinePendingForWorkerAfter(
            afterId = afterId,
            workerId = workerId,
            httpCode = httpCode,
            message = message,
            reason = reason,
        )
    }

    private suspend fun workerIdForEvents(events: List<com.apofeoz.shiftmanager.data.remote.dto.SyncEventDto>): String? {
        for (event in events) {
            val workerId = workerIdForEvent(event)
            if (!workerId.isNullOrBlank()) return workerId
        }
        return null
    }

    private suspend fun workerIdForEvent(ev: com.apofeoz.shiftmanager.data.remote.dto.SyncEventDto): String? =
        when (ev.type) {
            "START_SESSION" -> ev.payload.jsonObject["workerId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            "END_SESSION" -> {
                val sessionId = ev.payload.jsonObject["sessionId"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                if (sessionId == null) {
                    null
                } else {
                    AppContainer.sessionStateRepository.getActiveSessions().firstOrNull { it.sessionId == sessionId }?.workerId
                        ?: AppContainer.activeSessionsCache.get().byWorkerId.entries.firstOrNull { it.value == sessionId }?.key
                }
            }
            else -> null
        }

    private fun parseError(e: HttpException): ErrorResponseDto? {
        val raw = e.response()?.errorBody()?.string() ?: return null
        return runCatching { json.decodeFromString(ErrorResponseDto.serializer(), raw) }.getOrNull()
    }
}
