package com.apofeoz.backend.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
data class ErrorResponse(val code: String, val message: String, val details: Map<String, JsonElement> = emptyMap())

@Serializable
data class RegisterRequest(
    val email: String? = null,
    val phone: String? = null,
    val firstName: String,
    val lastName: String,
    val password: String,
)

@Serializable
data class LoginRequest(val login: String, val password: String)

@Serializable
data class TokenResponse(val accessToken: String, val refreshToken: String, val tokenType: String = "Bearer")

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class UserResponse(
    val id: String,
    val email: String?,
    val phone: String?,
    val firstName: String,
    val lastName: String,
    val role: String,
    val status: String,
)

@Serializable
data class PatchUserRequest(
    val role: String? = null,
    val status: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
)

@Serializable
data class CreateWorkerRequest(
    val firstName: String,
    val lastName: String,
    val position: String? = null,
    val foremanId: String? = null,
)

@Serializable
data class PatchWorkerRequest(
    val foremanId: String? = null,
    val status: String? = null,
)

@Serializable
data class WorkerResponse(
    val id: String,
    val userId: String?,
    val foremanId: String,
    /** Имя бригадира (пользователь `foremanId`) для списков у ADMIN. */
    val foremanDisplayName: String? = null,
    val firstName: String,
    val lastName: String,
    val position: String?,
    val status: String,
)

@Serializable
data class SessionResponse(
    val id: String,
    val workerId: String,
    val foremanId: String,
    val startAt: String,
    val endAt: String?,
    val status: String,
)

@Serializable
data class SyncBatchRequest(
    val batchUid: String,
    val submittedAt: String,
    val events: List<SyncEventInput>,
)

@Serializable
data class SyncEventInput(val type: String, val payload: JsonElement)

@Serializable
data class SyncBatchResponse(val applied: Boolean, val sessions: List<SessionResponse>)

@Serializable
data class ConflictResponse(
    val failedEventIndex: Int,
    val reason: String,
    val failedEventType: String? = null,
)

@Serializable
data class ReportRowResponse(
    val workerId: String,
    val firstName: String,
    val lastName: String,
    val foremanId: String,
    val foremanDisplayName: String?,
    val hours: Double,
    val shiftEquivalent: Double,
)

@Serializable
data class ReportTotals(val hours: Double, val shiftEquivalent: Double)

@Serializable
data class HoursReportResponse(
    val reportDate: String,
    val fromDate: String? = null,
    val toDate: String? = null,
    val timezone: String,
    val shiftNormHours: Int = 8,
    val rows: List<ReportRowResponse>,
    val totals: ReportTotals,
)

@Serializable
data class FailedBatchListItem(
    val id: String,
    val batchUid: String,
    val submittedAt: String,
    val failedIndex: Int,
    val reason: String,
)

@Serializable
data class FailedBatchDetailResponse(
    val id: String,
    val batchUid: String,
    val submittedAt: String,
    val failedIndex: Int,
    val reason: String,
    val eventsSnapshot: JsonArray,
)
