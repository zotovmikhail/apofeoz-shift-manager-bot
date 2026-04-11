package com.apofeoz.shiftmanager.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apofeoz.shiftmanager.core.di.AppContainer
import com.apofeoz.shiftmanager.data.local.LocalFailedBatch
import com.apofeoz.shiftmanager.data.remote.dto.SyncBatchRequestDto
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
                    dao.resetPending(row.id)
                    AppContainer.notifySessionExpired()
                    Result.failure()
                }
                409 -> {
                    // Conflict: server didn't apply batch; still clear local state to avoid UI deadlock.
                    runCatching {
                        val body = json.decodeFromString(SyncBatchRequestDto.serializer(), row.bodyJson)
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
                    }
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
                    if (body != null) {
                        body.events.forEach { ev ->
                            when (ev.type) {
                                "START_SESSION" -> {
                                    val wid = ev.payload.jsonObject["workerId"]?.jsonPrimitive?.content
                                    if (!wid.isNullOrBlank()) pendingActions.addBlockedWorker(wid)
                                }
                                "END_SESSION" -> {
                                    val sid = ev.payload.jsonObject["sessionId"]?.jsonPrimitive?.content
                                    if (!sid.isNullOrBlank()) pendingActions.addBlockedSession(sid)
                                }
                            }
                        }
                    }
                    runCatching {
                        failedLocal.add(
                            LocalFailedBatch(
                                httpCode = e.code(),
                                message = e.message(),
                                submittedAt = row.submittedAt,
                                bodyJson = row.bodyJson,
                            ),
                        )
                    }
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
}
