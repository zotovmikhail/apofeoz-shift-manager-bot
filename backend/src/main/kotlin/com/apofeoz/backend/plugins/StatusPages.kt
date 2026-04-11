package com.apofeoz.backend.plugins

import com.apofeoz.backend.api.ApiException
import com.apofeoz.backend.api.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import kotlin.coroutines.cancellation.CancellationException

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(
                cause.status,
                ErrorResponse(code = cause.code, message = cause.message, details = cause.payload),
            )
        }
        exception<Throwable> { call, cause ->
            if (cause is CancellationException) throw cause
            call.application.environment.log.error("Unhandled exception", cause)
            if (!call.response.isSent) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse("internal_error", "Internal server error"),
                )
            }
        }
    }
}
