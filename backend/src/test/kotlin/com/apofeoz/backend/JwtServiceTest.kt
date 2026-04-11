package com.apofeoz.backend

import com.apofeoz.backend.security.JwtService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

class JwtServiceTest {

    private val cfg = AppConfig(
        jdbcUrl = "jdbc:postgresql://localhost:5432/x",
        dbUser = "x",
        dbPassword = "x",
        closeDbOnStop = true,
        jwtSecret = "unit-test-secret-min-32-characters!!",
        jwtIssuer = "apofeoz",
        jwtAudience = "apofeoz-mobile",
        accessTokenMinutes = 15,
        refreshTokenDays = 30,
        reportTimeZone = "UTC",
        seedAdminEmail = null,
        seedAdminPassword = null,
        seedAdminPhone = null,
    )

    @Test
    fun accessTokenRoundTrip() {
        val jwt = JwtService(cfg)
        val uid = UUID.randomUUID()
        val token = jwt.accessToken(uid, "ADMIN")
        val decoded = jwt.decode(token)
        assertEquals(uid.toString(), decoded.subject)
        assertEquals("ADMIN", decoded.getClaim("role").asString())
    }
}
