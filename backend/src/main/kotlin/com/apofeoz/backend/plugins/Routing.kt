package com.apofeoz.backend.plugins

import com.apofeoz.backend.api.*
import com.apofeoz.backend.domain.Role
import com.apofeoz.backend.security.JwtUserPrincipal
import com.apofeoz.backend.service.*
import io.ktor.http.*
import io.ktor.server.application.*
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.*

fun Application.configureRouting(
    authService: AuthService,
    userService: UserService,
    workerService: WorkerService,
    sessionService: SessionService,
    syncService: SyncService,
    reportService: ReportService,
) {
    routing {
        route("/api/v1") {
            post("/auth/register") {
                val body = call.receive<RegisterRequest>()
                call.respond(HttpStatusCode.Created, authService.register(body))
            }
            post("/auth/login") {
                val body = call.receive<LoginRequest>()
                call.respond(authService.login(body))
            }
            post("/auth/refresh") {
                val body = call.receive<RefreshRequest>()
                call.respond(authService.refresh(body.refreshToken))
            }

            authenticate("auth-jwt") {
                post("/auth/logout") {
                    val body = call.receive<RefreshRequest>()
                    authService.logout(body.refreshToken)
                    call.respond(HttpStatusCode.NoContent)
                }

                get("/users/me") {
                    val p = call.jwtPrincipal()
                    call.respond(userService.me(p.userId))
                }

                get("/users") {
                    val p = call.jwtPrincipal()
                    p.requireRoles(Role.ADMIN)
                    call.respond(userService.listUsers())
                }

                patch("/users/{id}") {
                    val p = call.jwtPrincipal()
                    val id = UUID.fromString(call.parameters["id"]!!)
                    if (p.userId != id) {
                        p.requireRoles(Role.ADMIN)
                    }
                    val body = call.receive<PatchUserRequest>()
                    call.respond(userService.patchUser(p.userId, id, body))
                }

                get("/workers") {
                    val p = call.jwtPrincipal()
                    call.respond(workerService.list(p.userId, Role.valueOf(p.role)))
                }

                post("/workers") {
                    val p = call.jwtPrincipal()
                    val body = call.receive<CreateWorkerRequest>()
                    call.respond(
                        HttpStatusCode.Created,
                        workerService.create(p.userId, Role.valueOf(p.role), body),
                    )
                }

                patch("/workers/{id}") {
                    val p = call.jwtPrincipal()
                    val id = UUID.fromString(call.parameters["id"]!!)
                    val body = call.receive<PatchWorkerRequest>()
                    call.respond(workerService.patch(p.userId, Role.valueOf(p.role), id, body))
                }

                get("/sessions/active") {
                    val p = call.jwtPrincipal()
                    p.requireRoles(Role.FOREMAN, Role.ADMIN)
                    call.respond(sessionService.listActive(p.userId, Role.valueOf(p.role)))
                }

                post("/sync/batch") {
                    val p = call.jwtPrincipal()
                    p.requireRoles(Role.FOREMAN)
                    val body = call.receive<SyncBatchRequest>()
                    call.respond(syncService.applyBatch(p.userId, body))
                }

                get("/sync/failed-batches") {
                    val p = call.jwtPrincipal()
                    call.respond(syncService.listFailed(p.userId))
                }

                get("/sync/failed-batches/{id}") {
                    val p = call.jwtPrincipal()
                    val id = UUID.fromString(call.parameters["id"]!!)
                    call.respond(syncService.getFailedDetail(p.userId, id))
                }

                delete("/sync/failed-batches/{id}") {
                    val p = call.jwtPrincipal()
                    val id = UUID.fromString(call.parameters["id"]!!)
                    val ok = syncService.deleteFailed(p.userId, id)
                    if (ok) call.respond(HttpStatusCode.NoContent)
                    else call.respond(HttpStatusCode.NotFound, ErrorResponse("not_found", "Record not found"))
                }

                get("/reports/hours-by-worker-previous-day") {
                    val p = call.jwtPrincipal()
                    val date = call.request.queryParameters["date"]
                    call.respond(reportService.hoursByWorkerPreviousDay(Role.valueOf(p.role), date))
                }

                get("/reports/hours-by-worker-range") {
                    val p = call.jwtPrincipal()
                    val from = call.request.queryParameters["from"]
                    val to = call.request.queryParameters["to"]
                    call.respond(reportService.hoursByWorkerRange(Role.valueOf(p.role), from, to))
                }

                get("/reports/timesheet") {
                    val p = call.jwtPrincipal()
                    val from = call.request.queryParameters["from"]
                    val to = call.request.queryParameters["to"]
                    call.respond(reportService.timesheet(Role.valueOf(p.role), from, to))
                }

                get("/reports/timesheet.xlsx") {
                    val p = call.jwtPrincipal()
                    p.requireRoles(Role.ADMIN)
                    val from = call.request.queryParameters["from"]
                    val to = call.request.queryParameters["to"]
                    val bytes = reportService.timesheetXlsx(Role.valueOf(p.role), from, to)
                    val safeFrom = from?.trim()?.replace(Regex("[^0-9\\-]"), "_") ?: "from"
                    val safeTo = to?.trim()?.replace(Regex("[^0-9\\-]"), "_") ?: "to"
                    call.response.headers.append(
                        HttpHeaders.ContentDisposition,
                        "attachment; filename=\"tabel_${safeFrom}_${safeTo}.xlsx\"",
                    )
                    call.respondBytes(
                        bytes,
                        ContentType.parse("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
                    )
                }
            }
        }

        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        get("/openapi.yaml") {
            val stream = call.application.environment.classLoader.getResourceAsStream("openapi.yaml")
                ?: return@get call.respond(HttpStatusCode.NotFound)
            val text = InputStreamReader(stream, StandardCharsets.UTF_8).use { it.readText() }
            call.respondText(text, ContentType.parse("application/yaml"))
        }
    }
}

private fun ApplicationCall.jwtPrincipal(): JwtUserPrincipal =
    principal<JwtUserPrincipal>()
        ?: throw ApiException(HttpStatusCode.Unauthorized, "unauthorized", "Invalid or missing token")

private fun JwtUserPrincipal.requireRoles(vararg allowed: Role) {
    val r = Role.valueOf(role)
    if (r !in allowed) {
        throw ApiException(HttpStatusCode.Forbidden, "forbidden", "Insufficient permissions")
    }
}
