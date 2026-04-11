package com.apofeoz.backend

import io.ktor.server.application.*

data class AppConfig(
    val jdbcUrl: String,
    val dbUser: String,
    val dbPassword: String,
    val closeDbOnStop: Boolean,
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val accessTokenMinutes: Long,
    val refreshTokenDays: Long,
    /** Календарные сутки отчётов (границы дней, разбивка смены по полуночи). По умолчанию Москва. */
    val reportTimeZone: String,
    val seedAdminEmail: String?,
    val seedAdminPassword: String?,
    val seedAdminPhone: String?,
) {
    companion object {
        fun load(app: Application): AppConfig {
            val c = app.environment.config.config("app")
            fun env(key: String) = System.getenv(key)?.takeIf { it.isNotBlank() }
            /** For integration tests: -DJDBC_URL=... etc. */
            fun prop(key: String) = System.getProperty(key)?.takeIf { it.isNotBlank() }
            fun envOrProp(vararg keys: String): String? =
                keys.firstNotNullOfOrNull { env(it) ?: prop(it) }
            fun cfg(key: String, default: String) = env(key.uppercase().replace('.', '_'))
                ?: c.propertyOrNull(key)?.getString()
                ?: default
            fun cfgLong(key: String, default: Long) =
                env(key.uppercase())?.toLongOrNull()
                    ?: c.propertyOrNull(key)?.getString()?.toLongOrNull()
                    ?: default
            fun cfgBool(key: String, default: Boolean) =
                env(key.uppercase())?.lowercase()?.let { it == "true" || it == "1" }
                    ?: c.propertyOrNull(key)?.getString()?.lowercase()?.let { it == "true" || it == "1" }
                    ?: default
            val accessTokenMinutes =
                env("ACCESS_TOKEN_MINUTES")?.toLongOrNull()
                    ?: cfgLong("accessTokenMinutes", 15)
            val refreshTokenDays =
                env("REFRESH_TOKEN_DAYS")?.toLongOrNull()
                    ?: cfgLong("refreshTokenDays", 30)
            return AppConfig(
                jdbcUrl = envOrProp("JDBC_URL") ?: cfg("jdbcUrl", "jdbc:postgresql://localhost:15432/apofeoz"),
                dbUser = envOrProp("DB_USER") ?: cfg("dbUser", "apofeoz"),
                dbPassword = envOrProp("DB_PASSWORD") ?: cfg("dbPassword", "apofeoz"),
                closeDbOnStop = cfgBool("closeDbOnStop", true),
                jwtSecret = envOrProp("JWT_SECRET") ?: cfg("jwtSecret", "dev-secret-change-in-production-min-32-chars!!"),
                jwtIssuer = envOrProp("JWT_ISSUER") ?: cfg("jwtIssuer", "apofeoz"),
                jwtAudience = envOrProp("JWT_AUDIENCE") ?: cfg("jwtAudience", "apofeoz-mobile"),
                accessTokenMinutes = accessTokenMinutes,
                refreshTokenDays = refreshTokenDays,
                reportTimeZone = envOrProp("REPORT_TIME_ZONE") ?: cfg("reportTimeZone", "Europe/Moscow"),
                seedAdminEmail = envOrProp("SEED_ADMIN_EMAIL")
                    ?: c.propertyOrNull("seedAdminEmail")?.getString()?.takeIf { it.isNotBlank() },
                seedAdminPassword = envOrProp("SEED_ADMIN_PASSWORD")
                    ?: c.propertyOrNull("seedAdminPassword")?.getString()?.takeIf { it.isNotBlank() },
                seedAdminPhone = envOrProp("SEED_ADMIN_PHONE")
                    ?: c.propertyOrNull("seedAdminPhone")?.getString()?.takeIf { it.isNotBlank() },
            )
        }
    }
}
