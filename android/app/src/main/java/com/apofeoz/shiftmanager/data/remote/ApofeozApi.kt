package com.apofeoz.shiftmanager.data.remote

import com.apofeoz.shiftmanager.data.remote.dto.CreateWorkerRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.FailedBatchDetailDto
import com.apofeoz.shiftmanager.data.remote.dto.FailedBatchListItemDto
import com.apofeoz.shiftmanager.data.remote.dto.HoursReportResponseDto
import com.apofeoz.shiftmanager.data.remote.dto.LoginRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.PatchWorkerRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.PatchUserRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.RefreshRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.RegisterRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.SessionResponseDto
import com.apofeoz.shiftmanager.data.remote.dto.SyncBatchRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.SyncBatchResponseDto
import com.apofeoz.shiftmanager.data.remote.dto.TokenResponseDto
import com.apofeoz.shiftmanager.data.remote.dto.UserResponseDto
import com.apofeoz.shiftmanager.data.remote.dto.WorkerResponseDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ApofeozApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body body: RegisterRequestDto): TokenResponseDto

    @POST("api/v1/auth/login")
    suspend fun login(@Body body: LoginRequestDto): TokenResponseDto

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body body: RefreshRequestDto): TokenResponseDto

    @POST("api/v1/auth/logout")
    suspend fun logout(@Body body: RefreshRequestDto): Response<Unit>

    @GET("api/v1/users/me")
    suspend fun me(): UserResponseDto

    @GET("api/v1/users")
    suspend fun users(): List<UserResponseDto>

    @PATCH("api/v1/users/{id}")
    suspend fun patchUser(@Path("id") id: String, @Body body: PatchUserRequestDto): UserResponseDto

    @GET("api/v1/workers")
    suspend fun workers(): List<WorkerResponseDto>

    @POST("api/v1/workers")
    suspend fun createWorker(@Body body: CreateWorkerRequestDto): WorkerResponseDto

    @PATCH("api/v1/workers/{id}")
    suspend fun patchWorker(@Path("id") id: String, @Body body: PatchWorkerRequestDto): WorkerResponseDto

    @GET("api/v1/sessions/active")
    suspend fun activeSessions(): List<SessionResponseDto>

    @POST("api/v1/sync/batch")
    suspend fun syncBatch(@Body body: SyncBatchRequestDto): SyncBatchResponseDto

    @GET("api/v1/sync/failed-batches")
    suspend fun failedBatches(): List<FailedBatchListItemDto>

    @GET("api/v1/sync/failed-batches/{id}")
    suspend fun failedBatchDetail(@Path("id") id: String): FailedBatchDetailDto

    @DELETE("api/v1/sync/failed-batches/{id}")
    suspend fun deleteFailedBatch(@Path("id") id: String): Response<Unit>

    @GET("api/v1/reports/hours-by-worker-previous-day")
    suspend fun report(@Query("date") date: String? = null): HoursReportResponseDto

    @GET("api/v1/reports/hours-by-worker-range")
    suspend fun reportRange(
        @Query("from") from: String,
        @Query("to") to: String,
    ): HoursReportResponseDto

    @Streaming
    @GET("api/v1/reports/timesheet.xlsx")
    suspend fun timesheetXlsx(
        @Query("from") from: String,
        @Query("to") to: String,
    ): ResponseBody
}
