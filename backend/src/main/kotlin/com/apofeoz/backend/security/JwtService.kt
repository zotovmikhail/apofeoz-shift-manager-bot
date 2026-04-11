package com.apofeoz.backend.security

import com.apofeoz.backend.AppConfig
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import java.util.*

class JwtService(private val cfg: AppConfig) {
    private val algorithm: Algorithm = Algorithm.HMAC256(cfg.jwtSecret.toByteArray(Charsets.UTF_8))

    fun verifier(): JWTVerifier = JWT.require(algorithm)
        .withIssuer(cfg.jwtIssuer)
        .withAudience(cfg.jwtAudience)
        .build()

    fun decode(token: String): DecodedJWT = verifier().verify(token)

    fun accessToken(userId: UUID, role: String): String {
        val now = Date()
        val exp = Date(now.time + cfg.accessTokenMinutes * 60_000)
        return JWT.create()
            .withIssuer(cfg.jwtIssuer)
            .withAudience(cfg.jwtAudience)
            .withSubject(userId.toString())
            .withClaim("role", role)
            .withIssuedAt(now)
            .withExpiresAt(exp)
            .sign(algorithm)
    }
}
