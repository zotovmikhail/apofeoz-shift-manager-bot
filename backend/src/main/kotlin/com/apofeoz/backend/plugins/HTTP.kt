package com.apofeoz.backend.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*
import java.net.URI

fun Application.configureCors() {
    val allowedOrigins = System.getenv("CORS_ALLOWED_ORIGINS")
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        .orEmpty()

    install(CORS) {
        if (allowedOrigins.isEmpty()) {
            anyHost()
        } else {
            allowedOrigins.forEach { origin ->
                runCatching {
                    val uri = URI(origin)
                    val scheme = uri.scheme ?: "https"
                    val hostWithPort = if (uri.port > 0) "${uri.host}:${uri.port}" else uri.host
                    if (!hostWithPort.isNullOrBlank()) {
                        allowHost(hostWithPort, schemes = listOf(scheme))
                    }
                }.getOrElse {
                    // Fallback for raw host entries without scheme.
                    allowHost(origin, schemes = listOf("https"))
                }
            }
        }
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }
}
