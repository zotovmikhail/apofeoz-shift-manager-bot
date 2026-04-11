package com.apofeoz.backend.plugins

import com.apofeoz.backend.security.JwtService
import com.apofeoz.backend.security.JwtUserPrincipal
import com.auth0.jwt.exceptions.JWTVerificationException
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

fun Application.configureSecurity(jwtService: JwtService) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "apofeoz"
            verifier(jwtService.verifier())
            validate { credential ->
                try {
                    val sub = credential.payload.subject ?: return@validate null
                    val role = credential.payload.getClaim("role").asString() ?: return@validate null
                    JwtUserPrincipal(userId = java.util.UUID.fromString(sub), role = role)
                } catch (_: JWTVerificationException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        }
    }
}
