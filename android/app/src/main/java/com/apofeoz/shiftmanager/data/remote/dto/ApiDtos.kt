package com.apofeoz.shiftmanager.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement

@Serializable
data class TokenResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
)

@Serializable
data class RegisterRequestDto(
    val email: String? = null,
    val phone: String? = null,
    val firstName: String,
    val lastName: String,
    val password: String,
)

@Serializable
data class LoginRequestDto(val login: String, val password: String)

@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class UserResponseDto(
    val id: String,
    val email: String? = null,
    val phone: String? = null,
    val firstName: String,
    val lastName: String,
    val role: String,
    val status: String,
)

@Serializable
data class PatchUserRequestDto(
    val role: String? = null,
    val status: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
)

@Serializable
data class ErrorResponseDto(
    val code: String,
    val message: String,
    val details: Map<String, JsonElement> = emptyMap(),
)

@Serializable
data class CreateWorkerRequestDto(
    val firstName: String,
    val lastName: String,
    val position: String? = null,
    val foremanId: String? = null,
)

@Serializable
data class PatchWorkerRequestDto(
    val foremanId: String? = null,
    val status: String? = null,
)

@Serializable
data class WorkerResponseDto(
    val id: String,
    val userId: String? = null,
    val foremanId: String,
    val foremanDisplayName: String? = null,
    val firstName: String,
    val lastName: String,
    val position: String? = null,
    val status: String,
)

@Serializable
data class SyncEventDto(val type: String, val payload: JsonElement)

@Serializable
data class SyncBatchRequestDto(
    val batchUid: String,
    val submittedAt: String,
    val events: List<SyncEventDto>,
)

@Serializable
data class SessionResponseDto(
    val id: String,
    val workerId: String,
    val foremanId: String,
    val startAt: String,
    val endAt: String? = null,
    val status: String,
)

@Serializable
data class SyncBatchResponseDto(
    val applied: Boolean,
    val sessions: List<SessionResponseDto> = emptyList(),
)

@Serializable
data class FailedBatchListItemDto(
    val id: String,
    val batchUid: String,
    val submittedAt: String,
    val failedIndex: Int,
    val reason: String,
)

@Serializable
data class FailedBatchDetailDto(
    val id: String,
    val batchUid: String,
    val submittedAt: String,
    val failedIndex: Int,
    val reason: String,
    val eventsSnapshot: JsonArray,
)

@Serializable
data class ReportRowDto(
    val workerId: String,
    val firstName: String,
    val lastName: String,
    val foremanId: String,
    val foremanDisplayName: String? = null,
    val hours: Double,
    val shiftEquivalent: Double,
)

@Serializable
data class ReportTotalsDto(val hours: Double, val shiftEquivalent: Double)

@Serializable
data class HoursReportResponseDto(
    val reportDate: String,
    val fromDate: String? = null,
    val toDate: String? = null,
    val timezone: String,
    val shiftNormHours: Int = 8,
    val rows: List<ReportRowDto> = emptyList(),
    val totals: ReportTotalsDto,
)
