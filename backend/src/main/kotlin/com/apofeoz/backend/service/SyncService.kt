package com.apofeoz.backend.service

import com.apofeoz.backend.api.*
import com.apofeoz.backend.data.*
import com.apofeoz.backend.domain.SessionStatus
import com.apofeoz.backend.domain.WorkerStatus
import io.ktor.http.*
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

private val jsonFmt = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

class SyncService(
    private val users: UserRepository,
    private val workers: WorkerRepository,
    private val syncRepo: SyncBatchRepository,
) {

    private val log = LoggerFactory.getLogger(SyncService::class.java)

    suspend fun applyBatch(userId: UUID, req: SyncBatchRequest): SyncBatchResponse {
        if (req.batchUid.isBlank()) {
            throw ApiException(HttpStatusCode.BadRequest, "validation_error", "batchUid required")
        }
        if (req.events.isEmpty()) {
            throw ApiException(HttpStatusCode.BadRequest, "validation_error", "events must not be empty")
        }
        if (req.events.size > 100) {
            throw ApiException(HttpStatusCode.BadRequest, "batch_too_large", "Max 100 events per batch")
        }
        syncRepo.findResultByBatchUid(req.batchUid)?.let { cached ->
            return jsonFmt.decodeFromString<SyncBatchResponse>(cached)
        }

        val submittedAt = try {
            OffsetDateTime.parse(req.submittedAt)
        } catch (_: Exception) {
            throw ApiException(HttpStatusCode.BadRequest, "invalid_date", "submittedAt must be ISO-8601")
        }

        val eventsForStorage = req.events.map { it.type to it.payload.toString() }
        val snapshot = jsonFmt.encodeToString(req.events)

        val foreman = users.findById(userId) ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "User not found")
        if (foreman.role != com.apofeoz.backend.domain.Role.FOREMAN || foreman.status != com.apofeoz.backend.domain.UserStatus.ACTIVE) {
            throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Only active foreman can sync batches")
        }

        log.info(
            "sync_batch_request userId={} batchUid={} eventsCount={} firstEventType={}",
            userId,
            req.batchUid,
            req.events.size,
            req.events.firstOrNull()?.type ?: "—",
        )

        try {
            val result = applyBatchTransaction(userId, req.batchUid, submittedAt, req.events, eventsForStorage)
            log.info("sync_batch_applied userId={} batchUid={}", userId, req.batchUid)
            return result
        } catch (e: SyncApplyException) {
            log.warn(
                "sync_batch_conflict userId={} batchUid={} failedIndex={} reason={}",
                userId,
                req.batchUid,
                e.index,
                e.reason,
            )
            syncRepo.insertFailed(
                userId = userId,
                batchUid = req.batchUid,
                submittedAt = submittedAt,
                eventsSnapshot = snapshot,
                failedIndex = e.index,
                reason = e.reason,
            )
            throw ApiException(
                HttpStatusCode.Conflict,
                "sync_conflict",
                e.reason,
                payload = buildMap {
                    put("failedEventIndex", JsonPrimitive(e.index))
                    put("reason", JsonPrimitive(e.reason))
                    e.eventType?.let { put("failedEventType", JsonPrimitive(it)) }
                },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log.error(
                "sync_batch_unexpected_error userId={} batchUid={} message={}",
                userId,
                req.batchUid,
                e.message,
                e,
            )
            throw e
        }
    }

    private suspend fun applyBatchTransaction(
        userId: UUID,
        batchUid: String,
        submittedAt: OffsetDateTime,
        events: List<SyncEventInput>,
        eventsForStorage: List<Pair<String, String>>,
    ): SyncBatchResponse = newSuspendedTransaction(Dispatchers.IO) {
        val dup = SyncBatches.selectAll().where { SyncBatches.batchUid eq batchUid }.singleOrNull()
        if (dup != null) {
            val r = dup[SyncBatches.resultJson]
                ?: throw IllegalStateException("batch exists without result")
            return@newSuspendedTransaction jsonFmt.decodeFromString<SyncBatchResponse>(r)
        }

        val clientSessionMap = mutableMapOf<String, UUID>()
        val outSessions = mutableListOf<SessionResponse>()
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        fun rowToSessionResponse(
            id: UUID,
            workerId: UUID,
            foremanId: UUID,
            startAt: OffsetDateTime,
            endAt: OffsetDateTime?,
            status: SessionStatus,
        ) = SessionResponse(
            id = id.toString(),
            workerId = workerId.toString(),
            foremanId = foremanId.toString(),
            startAt = startAt.toString(),
            endAt = endAt?.toString(),
            status = status.name,
        )

        events.forEachIndexed { index, ev ->
            when (ev.type) {
                "START_SESSION" -> {
                    val o = ev.payload.jsonObject
                    val sessionId = o["sessionId"]?.jsonPrimitive?.content?.let { UUID.fromString(it) }
                        ?: throw SyncApplyException(index, "START_SESSION: sessionId required", ev.type)
                    val workerId = o["workerId"]?.jsonPrimitive?.content?.let { UUID.fromString(it) }
                        ?: throw SyncApplyException(index, "START_SESSION: workerId required", ev.type)
                    val startAtStr = o["startAt"]?.jsonPrimitive?.content
                        ?: throw SyncApplyException(index, "START_SESSION: startAt required", ev.type)
                    val startAt = try {
                        OffsetDateTime.parse(startAtStr)
                    } catch (_: Exception) {
                        throw SyncApplyException(index, "START_SESSION: invalid startAt", ev.type)
                    }
                    val sid = sessionId

                    val w = Workers.selectAll().where { Workers.id eq workerId }.singleOrNull()
                        ?: throw SyncApplyException(index, "worker not found", ev.type)
                    if (w[Workers.status] != WorkerStatus.ACTIVE.name) {
                        throw SyncApplyException(index, "worker not active", ev.type)
                    }
                    if (w[Workers.foremanId].value != userId) {
                        throw SyncApplyException(index, "worker not in your crew", ev.type)
                    }

                    // Idempotency for client-generated sessionId:
                    // if the session already exists, just return it (or conflict if it doesn't belong to actor).
                    val existingById = Sessions.selectAll().where { Sessions.id eq sid }.singleOrNull()
                    if (existingById != null) {
                        if (existingById[Sessions.foremanId].value != userId) {
                            throw SyncApplyException(index, "session belongs to another foreman", ev.type)
                        }
                        if (existingById[Sessions.workerId].value != workerId) {
                            throw SyncApplyException(index, "sessionId already used for another worker", ev.type)
                        }
                        outSessions.add(
                            rowToSessionResponse(
                                sid,
                                workerId,
                                userId,
                                existingById[Sessions.startAt],
                                existingById[Sessions.endAt],
                                SessionStatus.valueOf(existingById[Sessions.status]),
                            ),
                        )
                        return@forEachIndexed
                    }

                    val active = Sessions.selectAll().where {
                        (Sessions.workerId eq EntityID(workerId, Workers)) and (Sessions.status eq SessionStatus.ACTIVE.name)
                    }.singleOrNull()
                    if (active != null) {
                        throw SyncApplyException(index, "worker already has active session", ev.type)
                    }

                    Sessions.insert {
                        it[Sessions.id] = sid
                        it[Sessions.workerId] = EntityID(workerId, Workers)
                        it[Sessions.foremanId] = EntityID(userId, Users)
                        it[Sessions.startAt] = startAt
                        it[Sessions.endAt] = null
                        it[Sessions.status] = SessionStatus.ACTIVE.name
                        it[Sessions.createdAt] = now
                        it[Sessions.updatedAt] = now
                    }
                    outSessions.add(
                        rowToSessionResponse(sid, workerId, userId, startAt, null, SessionStatus.ACTIVE),
                    )
                }

                "END_SESSION" -> {
                    val o = ev.payload.jsonObject
                    val endAtStr = o["endAt"]?.jsonPrimitive?.content
                        ?: throw SyncApplyException(index, "END_SESSION: endAt required", ev.type)
                    val endAt = try {
                        OffsetDateTime.parse(endAtStr)
                    } catch (_: Exception) {
                        throw SyncApplyException(index, "END_SESSION: invalid endAt", ev.type)
                    }
                    val sessionId = o["sessionId"]?.jsonPrimitive?.content?.let { UUID.fromString(it) }
                        ?: throw SyncApplyException(index, "END_SESSION: sessionId required", ev.type)

                    val row = Sessions.selectAll().where { Sessions.id eq sessionId }.singleOrNull()
                        ?: throw SyncApplyException(index, "session not found", ev.type)
                    if (row[Sessions.foremanId].value != userId) {
                        throw SyncApplyException(index, "session belongs to another foreman", ev.type)
                    }
                    if (row[Sessions.status] != SessionStatus.ACTIVE.name) {
                        throw SyncApplyException(index, "session already closed", ev.type)
                    }
                    Sessions.update({ Sessions.id eq sessionId }) {
                        it[Sessions.endAt] = endAt
                        it[Sessions.status] = SessionStatus.CLOSED.name
                        it[Sessions.updatedAt] = now
                    }
                    outSessions.add(
                        rowToSessionResponse(
                            sessionId,
                            row[Sessions.workerId].value,
                            row[Sessions.foremanId].value,
                            row[Sessions.startAt],
                            endAt,
                            SessionStatus.CLOSED,
                        ),
                    )
                }

                else -> throw SyncApplyException(index, "unknown event type: ${ev.type}", ev.type)
            }
        }

        val response = SyncBatchResponse(applied = true, sessions = outSessions)
        val resultJson = jsonFmt.encodeToString(response)
        val batchId = UUID.randomUUID()
        SyncBatches.insert {
            it[SyncBatches.id] = batchId
            it[SyncBatches.userId] = userId
            it[SyncBatches.batchUid] = batchUid
            it[SyncBatches.submittedAt] = submittedAt
            it[SyncBatches.appliedAt] = now
            it[SyncBatches.resultJson] = resultJson
        }
        eventsForStorage.forEach { (type, payload) ->
            val evId = UUID.randomUUID()
            SyncEvents.insert {
                it[SyncEvents.id] = evId
                it[SyncEvents.batchId] = EntityID(batchId, SyncBatches)
                it[SyncEvents.type] = type
                it[SyncEvents.payload] = payload
                it[SyncEvents.createdAt] = now
            }
        }

        response
    }

    suspend fun listFailed(userId: UUID): List<FailedBatchListItem> =
        syncRepo.listFailed(userId).map {
            FailedBatchListItem(
                id = it.id.toString(),
                batchUid = it.batchUid,
                submittedAt = it.submittedAt.toString(),
                failedIndex = it.failedIndex,
                reason = it.reason,
            )
        }

    suspend fun getFailedDetail(userId: UUID, id: UUID): FailedBatchDetailResponse {
        val row = syncRepo.findFailedById(id, userId)
            ?: throw ApiException(HttpStatusCode.NotFound, "not_found", "Failed batch not found")
        val eventsJson = try {
            when (val el = Json.parseToJsonElement(row.eventsSnapshot)) {
                is JsonArray -> el
                else -> JsonArray(emptyList())
            }
        } catch (_: Exception) {
            JsonArray(emptyList())
        }
        return FailedBatchDetailResponse(
            id = row.id.toString(),
            batchUid = row.batchUid,
            submittedAt = row.submittedAt.toString(),
            failedIndex = row.failedIndex,
            reason = row.reason,
            eventsSnapshot = eventsJson,
        )
    }

    suspend fun deleteFailed(userId: UUID, id: UUID): Boolean = syncRepo.deleteFailed(id, userId)
}

private class SyncApplyException(val index: Int, val reason: String, val eventType: String?) : Exception(reason)
