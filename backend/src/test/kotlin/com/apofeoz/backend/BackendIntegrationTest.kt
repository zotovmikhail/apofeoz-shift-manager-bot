package com.apofeoz.backend

import com.apofeoz.backend.api.CreateWorkerRequest
import com.apofeoz.backend.api.DeviceResponse
import com.apofeoz.backend.api.ErrorResponse
import com.apofeoz.backend.api.FailedBatchDetailResponse
import com.apofeoz.backend.api.HoursReportResponse
import com.apofeoz.backend.api.LoginRequest
import com.apofeoz.backend.api.PatchUserRequest
import com.apofeoz.backend.api.PatchWorkerRequest
import com.apofeoz.backend.api.RegisterRequest
import com.apofeoz.backend.api.SyncBatchRequest
import com.apofeoz.backend.api.SyncBatchResponse
import com.apofeoz.backend.api.SyncEventInput
import com.apofeoz.backend.api.TimesheetReportResponse
import com.apofeoz.backend.api.TokenResponse
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers(disabledWithoutDocker = true)
@Execution(ExecutionMode.SAME_THREAD)
class BackendIntegrationTest {

    companion object {
        @Container
        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16-alpine")
            .withDatabaseName("apofeoz")
            .withUsername("apofeoz")
            .withPassword("apofeoz")

        private val jsonParser = Json { ignoreUnknownKeys = true; prettyPrint = false }

        fun appConfig(): MapApplicationConfig = MapApplicationConfig().apply {
            put("ktor.application.modules.0", "com.apofeoz.backend.ApplicationKt.module")
            put("ktor.deployment.port", "0")
            put("app.jdbcUrl", postgres.jdbcUrl)
            put("app.dbUser", postgres.username)
            put("app.dbPassword", postgres.password)
            put("app.closeDbOnStop", "false")
            put("app.jwtSecret", "integration-test-jwt-secret-32b++++++++")
            put("app.jwtIssuer", "apofeoz")
            put("app.jwtAudience", "apofeoz-mobile")
            put("app.reportTimeZone", "UTC")
            put("app.seedAdminEmail", "admin@test.local")
            put("app.seedAdminPassword", "AdminPass12345!")
        }
    }

    @Test
    fun health() = runBlocking {
        testApplication {
            environment { config = appConfig() }
            application { module() }
            val response = client.get("/health")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("ok"))
        }
    }

    @Test
    fun registerLoginMe() = runBlocking {
        testApplication {
            environment { config = appConfig() }
            application { module() }
            val c = createClient { install(ContentNegotiation) { json(jsonParser) } }
            // дождаться старта приложения (и сид-админа в ApplicationStarted)
            c.get("/health")
            val email = "u-${UUID.randomUUID()}@test.local"
            val reg = c.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterRequest(
                        email = email,
                        phone = null,
                        firstName = "U",
                        lastName = "Ser",
                        password = "password123",
                    ),
                )
            }
            assertEquals(HttpStatusCode.Created, reg.status, reg.bodyAsText())
            val tok = reg.body<TokenResponse>()
            val me = c.get("/api/v1/users/me") { bearerAuth(tok.accessToken) }
            assertEquals(HttpStatusCode.OK, me.status)
            val body = me.body<com.apofeoz.backend.api.UserResponse>()
            assertEquals(email, body.email)
            assertEquals("USER", body.role)
        }
    }

    @Test
    fun syncBatchIdempotentAndReport() = runBlocking {
        testApplication {
            environment { config = appConfig() }
            application { module() }
            val c = createClient { install(ContentNegotiation) { json(jsonParser) } }
            c.get("/health")

            val adminLogin = c.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(login = "admin@test.local", password = "AdminPass12345!"))
            }
            assertEquals(HttpStatusCode.OK, adminLogin.status, adminLogin.bodyAsText())
            val adminTok = adminLogin.body<TokenResponse>()

            val foremanEmail = "fm-${UUID.randomUUID()}@test.local"
            val regForeman = c.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterRequest(
                        email = foremanEmail,
                        firstName = "Fore",
                        lastName = "Man",
                        password = "password123",
                    ),
                )
            }
            assertEquals(HttpStatusCode.Created, regForeman.status)
            val foremanUserId = regForeman.body<TokenResponse>().let { t ->
                val me = c.get("/api/v1/users/me") { bearerAuth(t.accessToken) }
                me.body<com.apofeoz.backend.api.UserResponse>().id
            }

            val patch = c.patch("/api/v1/users/$foremanUserId") {
                bearerAuth(adminTok.accessToken)
                contentType(ContentType.Application.Json)
                setBody(PatchUserRequest(role = "FOREMAN"))
            }
            assertEquals(HttpStatusCode.OK, patch.status, patch.bodyAsText())

            val loginDeviceId = "login-device-${UUID.randomUUID()}"
            val foremanLogin = c.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(
                    LoginRequest(
                        login = foremanEmail,
                        password = "password123",
                        deviceId = loginDeviceId,
                        appVersion = "0.1.login",
                        platform = "android",
                        deviceModel = "Login Device",
                        osVersion = "16",
                    ),
                )
            }
            val foremanTok = foremanLogin.body<TokenResponse>()

            val worker = c.post("/api/v1/workers") {
                bearerAuth(adminTok.accessToken)
                contentType(ContentType.Application.Json)
                setBody(CreateWorkerRequest(firstName = "W", lastName = "One", foremanId = foremanUserId))
            }
            assertEquals(HttpStatusCode.Created, worker.status, worker.bodyAsText())
            val workerId = worker.body<com.apofeoz.backend.api.WorkerResponse>().id

            val reportDay = "2025-06-10"
            val startAt = "${reportDay}T08:00:00Z"
            val endAt = "${reportDay}T16:00:00Z"
            val sessionId = UUID.randomUUID().toString()
            val batchUid = "batch-${UUID.randomUUID()}"

            val batch = SyncBatchRequest(
                batchUid = batchUid,
                submittedAt = "${reportDay}T18:00:00Z",
                deviceId = "device-${UUID.randomUUID()}",
                appVersion = "0.1.test",
                platform = "android",
                deviceModel = "Test Device",
                osVersion = "15",
                events = listOf(
                    SyncEventInput(
                        "START_SESSION",
                        buildJsonObject {
                            put("sessionId", JsonPrimitive(sessionId))
                            put("workerId", JsonPrimitive(workerId))
                            put("startAt", JsonPrimitive(startAt))
                        },
                        operationId = "op-start-${UUID.randomUUID()}",
                    ),
                    SyncEventInput(
                        "END_SESSION",
                        buildJsonObject {
                            put("sessionId", JsonPrimitive(sessionId))
                            put("endAt", JsonPrimitive(endAt))
                        },
                        operationId = "op-end-${UUID.randomUUID()}",
                    ),
                ),
            )

            val s1 = c.post("/api/v1/sync/batch") {
                bearerAuth(foremanTok.accessToken)
                contentType(ContentType.Application.Json)
                setBody(batch)
            }
            assertEquals(HttpStatusCode.OK, s1.status, s1.bodyAsText())
            val r1 = s1.body<SyncBatchResponse>()
            assertTrue(r1.applied)
            assertEquals(2, r1.sessions.size)

            val s2 = c.post("/api/v1/sync/batch") {
                bearerAuth(foremanTok.accessToken)
                contentType(ContentType.Application.Json)
                setBody(batch)
            }
            assertEquals(HttpStatusCode.OK, s2.status)
            val r2 = s2.body<SyncBatchResponse>()
            assertEquals(r1.sessions.map { it.id }, r2.sessions.map { it.id })

            val rep = c.get("/api/v1/reports/hours-by-worker-previous-day?date=$reportDay") {
                bearerAuth(adminTok.accessToken)
            }
            assertEquals(HttpStatusCode.OK, rep.status, rep.bodyAsText())
            val report = rep.body<HoursReportResponse>()
            val row = report.rows.find { it.workerId == workerId }
            assertTrue(row != null, "worker row in report")
            assertEquals(8.0, row!!.hours, 0.01)
            assertEquals(1.0, row.shiftEquivalent, 0.01)

            val xlsx = c.get("/api/v1/reports/timesheet.xlsx?from=$reportDay&to=$reportDay") {
                bearerAuth(adminTok.accessToken)
            }
            assertEquals(HttpStatusCode.OK, xlsx.status)
            val raw = xlsx.readBytes()
            assertTrue(raw.size > 200)
            assertEquals(0x50.toByte(), raw[0])
            assertEquals(0x4B.toByte(), raw[1])

            val devices = c.get("/api/v1/devices") { bearerAuth(adminTok.accessToken) }
            assertEquals(HttpStatusCode.OK, devices.status, devices.bodyAsText())
            val deviceItems = devices.body<List<DeviceResponse>>()
            val savedDevice = deviceItems.single { it.deviceId == batch.deviceId }
            assertEquals(foremanUserId, savedDevice.lastUserId)
            assertEquals("0.1.test", savedDevice.appVersion)
            assertEquals("Test Device", savedDevice.deviceModel)
            val loginDevice = deviceItems.single { it.deviceId == loginDeviceId }
            assertEquals(foremanUserId, loginDevice.lastUserId)
            assertEquals("0.1.login", loginDevice.appVersion)
            assertEquals("Login Device", loginDevice.deviceModel)
            assertTrue(loginDevice.lastLoginAt != null, "lastLoginAt should be set on mobile login")
        }
    }

    @Test
    fun syncConflictStoresFailedBatch() = runBlocking {
        testApplication {
            environment { config = appConfig() }
            application { module() }
            val c = createClient { install(ContentNegotiation) { json(jsonParser) } }
            c.get("/health")

            val adminTok = c.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(login = "admin@test.local", password = "AdminPass12345!"))
            }.body<TokenResponse>()

            val foremanEmail = "fm2-${UUID.randomUUID()}@test.local"
            c.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterRequest(email = foremanEmail, firstName = "F", lastName = "M", password = "password123"),
                )
            }
            val foremanId = c.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(login = foremanEmail, password = "password123"))
            }.let { r ->
                val t = r.body<TokenResponse>()
                c.get("/api/v1/users/me") { bearerAuth(t.accessToken) }.body<com.apofeoz.backend.api.UserResponse>().id
            }

            c.patch("/api/v1/users/$foremanId") {
                bearerAuth(adminTok.accessToken)
                contentType(ContentType.Application.Json)
                setBody(PatchUserRequest(role = "FOREMAN"))
            }

            val foremanTok = c.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(login = foremanEmail, password = "password123"))
            }.body<TokenResponse>()

            val workerId = c.post("/api/v1/workers") {
                bearerAuth(adminTok.accessToken)
                contentType(ContentType.Application.Json)
                setBody(CreateWorkerRequest(firstName = "W", lastName = "Two", foremanId = foremanId))
            }.body<com.apofeoz.backend.api.WorkerResponse>().id

            val badBatch = SyncBatchRequest(
                batchUid = "bad-${UUID.randomUUID()}",
                submittedAt = "2025-06-10T10:00:00Z",
                events = listOf(
                    SyncEventInput(
                        "START_SESSION",
                        buildJsonObject {
                            put("sessionId", JsonPrimitive(UUID.randomUUID().toString()))
                            put("workerId", JsonPrimitive(workerId))
                            put("startAt", JsonPrimitive("2025-06-10T08:00:00Z"))
                        },
                    ),
                    SyncEventInput(
                        "START_SESSION",
                        buildJsonObject {
                            put("sessionId", JsonPrimitive(UUID.randomUUID().toString()))
                            put("workerId", JsonPrimitive(workerId))
                            put("startAt", JsonPrimitive("2025-06-10T09:00:00Z"))
                        },
                    ),
                ),
            )

            val conflict = c.post("/api/v1/sync/batch") {
                bearerAuth(foremanTok.accessToken)
                contentType(ContentType.Application.Json)
                setBody(badBatch)
            }
            assertEquals(HttpStatusCode.Conflict, conflict.status)
            val err = conflict.body<ErrorResponse>()
            assertEquals("sync_conflict", err.code)
            assertEquals("1", err.details["failedEventIndex"]!!.jsonPrimitive.content)

            val failed = c.get("/api/v1/sync/failed-batches") { bearerAuth(foremanTok.accessToken) }
            assertEquals(HttpStatusCode.OK, failed.status)
            val items = failed.body<List<com.apofeoz.backend.api.FailedBatchListItem>>()
            assertTrue(items.any { it.batchUid == badBatch.batchUid })
            val failedId = items.first { it.batchUid == badBatch.batchUid }.id
            val detail = c.get("/api/v1/sync/failed-batches/$failedId") { bearerAuth(foremanTok.accessToken) }
            assertEquals(HttpStatusCode.OK, detail.status)
            assertTrue(detail.body<FailedBatchDetailResponse>().eventsSnapshot.size >= 2)

            val adminFailed = c.get("/api/v1/sync/failed-batches") { bearerAuth(adminTok.accessToken) }
            assertEquals(HttpStatusCode.OK, adminFailed.status)
            assertTrue(adminFailed.body<List<com.apofeoz.backend.api.FailedBatchListItem>>().any { it.batchUid == badBatch.batchUid })
            val adminDetail = c.get("/api/v1/sync/failed-batches/$failedId") { bearerAuth(adminTok.accessToken) }
            assertEquals(HttpStatusCode.OK, adminDetail.status)
            assertEquals(failedId, adminDetail.body<FailedBatchDetailResponse>().id)

            val del = c.delete("/api/v1/sync/failed-batches/$failedId") {
                bearerAuth(adminTok.accessToken)
            }
            assertEquals(HttpStatusCode.NoContent, del.status)
        }
    }

    @Test
    fun rangeReportAndTimesheetIncludeInactiveWorkerHistoryAndClipByRange() = runBlocking {
        testApplication {
            environment { config = appConfig() }
            application { module() }
            val c = createClient { install(ContentNegotiation) { json(jsonParser) } }
            c.get("/health")

            val adminTok = c.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(login = "admin@test.local", password = "AdminPass12345!"))
            }.body<TokenResponse>()

            val foremanEmail = "fm3-${UUID.randomUUID()}@test.local"
            val regForeman = c.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(email = foremanEmail, firstName = "Fore", lastName = "Three", password = "password123"))
            }
            val foremanUserId = regForeman.body<TokenResponse>().let { t ->
                c.get("/api/v1/users/me") { bearerAuth(t.accessToken) }.body<com.apofeoz.backend.api.UserResponse>().id
            }

            c.patch("/api/v1/users/$foremanUserId") {
                bearerAuth(adminTok.accessToken)
                contentType(ContentType.Application.Json)
                setBody(PatchUserRequest(role = "FOREMAN"))
            }

            val foremanTok = c.post("/api/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(login = foremanEmail, password = "password123"))
            }.body<TokenResponse>()

            val worker = c.post("/api/v1/workers") {
                bearerAuth(adminTok.accessToken)
                contentType(ContentType.Application.Json)
                setBody(CreateWorkerRequest(firstName = "Hist", lastName = "Worker", foremanId = foremanUserId))
            }.body<com.apofeoz.backend.api.WorkerResponse>()

            val workerId = worker.id
            val sessionId = UUID.randomUUID().toString()
            val batchUid = "batch-hist-${UUID.randomUUID()}"

            val batch = SyncBatchRequest(
                batchUid = batchUid,
                submittedAt = "2025-06-11T01:40:00Z",
                events = listOf(
                    SyncEventInput(
                        "START_SESSION",
                        buildJsonObject {
                            put("sessionId", JsonPrimitive(sessionId))
                            put("workerId", JsonPrimitive(workerId))
                            put("startAt", JsonPrimitive("2025-06-10T22:00:00Z"))
                        },
                    ),
                    SyncEventInput(
                        "END_SESSION",
                        buildJsonObject {
                            put("sessionId", JsonPrimitive(sessionId))
                            put("endAt", JsonPrimitive("2025-06-11T01:30:00Z"))
                        },
                    ),
                ),
            )

            val sync = c.post("/api/v1/sync/batch") {
                bearerAuth(foremanTok.accessToken)
                contentType(ContentType.Application.Json)
                setBody(batch)
            }
            assertEquals(HttpStatusCode.OK, sync.status, sync.bodyAsText())

            val deactivateWorker = c.patch("/api/v1/workers/$workerId") {
                bearerAuth(adminTok.accessToken)
                contentType(ContentType.Application.Json)
                setBody(PatchWorkerRequest(status = "INACTIVE"))
            }
            assertEquals(HttpStatusCode.OK, deactivateWorker.status, deactivateWorker.bodyAsText())

            val report = c.get("/api/v1/reports/hours-by-worker-range?from=2025-06-11&to=2025-06-11") {
                bearerAuth(adminTok.accessToken)
            }
            assertEquals(HttpStatusCode.OK, report.status, report.bodyAsText())
            val reportBody = report.body<HoursReportResponse>()
            val row = reportBody.rows.find { it.workerId == workerId }
            assertTrue(row != null, "inactive worker with history must remain in range report")
            assertEquals(1.5, row!!.hours, 0.01)
            assertEquals(0.188, row.shiftEquivalent, 0.001)

            val timesheet = c.get("/api/v1/reports/timesheet?from=2025-06-11&to=2025-06-11") {
                bearerAuth(adminTok.accessToken)
            }
            assertEquals(HttpStatusCode.OK, timesheet.status, timesheet.bodyAsText())
            val timesheetBody = timesheet.body<TimesheetReportResponse>()
            assertTrue(timesheetBody.workers.any { it.workerId == workerId }, "inactive worker with history must remain in timesheet")
            val dayRow = timesheetBody.rows.single { it.date == "2025-06-11" }
            val dayCell = dayRow.cells.single { it.workerId == workerId }
            assertEquals(1.5, dayCell.hours, 0.01)
            assertEquals(0.188, dayCell.shiftEquivalent, 0.001)

            val xlsx = c.get("/api/v1/reports/timesheet.xlsx?from=2025-06-11&to=2025-06-11") {
                bearerAuth(adminTok.accessToken)
            }
            assertEquals(HttpStatusCode.OK, xlsx.status)
            val raw = xlsx.readBytes()
            assertTrue(raw.size > 200)
            assertEquals(0x50.toByte(), raw[0])
            assertEquals(0x4B.toByte(), raw[1])
        }
    }

    @Test
    fun plainUserCannotCallSyncBatch() = runBlocking {
        testApplication {
            environment { config = appConfig() }
            application { module() }
            val c = createClient { install(ContentNegotiation) { json(jsonParser) } }
            c.get("/health")
            val email = "plain-${UUID.randomUUID()}@test.local"
            val tok = c.post("/api/v1/auth/register") {
                contentType(ContentType.Application.Json)
                setBody(RegisterRequest(email = email, firstName = "P", lastName = "L", password = "password123"))
            }.body<TokenResponse>()

            val resp = c.post("/api/v1/sync/batch") {
                bearerAuth(tok.accessToken)
                contentType(ContentType.Application.Json)
                setBody(
                    SyncBatchRequest(
                        batchUid = "x",
                        submittedAt = "2025-01-01T00:00:00Z",
                        events = emptyList(),
                    ),
                )
            }
            assertEquals(HttpStatusCode.Forbidden, resp.status)
        }
    }
}
