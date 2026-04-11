package com.apofeoz.backend.service

import org.mindrot.jbcrypt.BCrypt
import com.apofeoz.backend.AppConfig
import com.apofeoz.backend.api.ApiException
import com.apofeoz.backend.api.LoginRequest
import com.apofeoz.backend.api.RegisterRequest
import com.apofeoz.backend.api.TokenResponse
import com.apofeoz.backend.data.RefreshTokenRepository
import com.apofeoz.backend.data.UserRepository
import com.apofeoz.backend.domain.Role
import com.apofeoz.backend.domain.UserStatus
import com.apofeoz.backend.security.JwtService
import io.ktor.http.*
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class AuthService(
    private val cfg: AppConfig,
    private val users: UserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val jwt: JwtService,
) {

    suspend fun register(req: RegisterRequest): TokenResponse {
        val email = req.email?.trim()?.takeIf { it.isNotEmpty() }
        val phone = req.phone?.trim()?.takeIf { it.isNotEmpty() }
        if (email == null && phone == null) {
            throw ApiException(HttpStatusCode.BadRequest, "validation_error", "email or phone required")
        }
        if (req.firstName.isBlank() || req.lastName.isBlank() || req.password.length < 8) {
            throw ApiException(HttpStatusCode.BadRequest, "validation_error", "invalid name or password (min 8 chars)")
        }
        email?.let { if (users.findByEmail(it) != null) throw conflict("email_taken") }
        phone?.let { if (users.findByPhone(it) != null) throw conflict("phone_taken") }
        val id = UUID.randomUUID()
        val hash = BCrypt.hashpw(req.password, BCrypt.gensalt(12))
        users.insert(id, email, phone, req.firstName.trim(), req.lastName.trim(), hash, Role.USER, UserStatus.ACTIVE)
        return issueTokens(id, Role.USER.name)
    }

    suspend fun login(req: LoginRequest): TokenResponse {
        val login = req.login.trim()
        if (login.isEmpty() || req.password.isEmpty()) {
            throw ApiException(HttpStatusCode.BadRequest, "validation_error", "login and password required")
        }
        val u = users.findByLogin(login)
            ?: throw ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Invalid login or password")
        if (u.status != UserStatus.ACTIVE) {
            throw ApiException(HttpStatusCode.Unauthorized, "account_inactive", "Account is inactive")
        }
        val ok = BCrypt.checkpw(req.password, u.passwordHash)
        if (!ok) throw ApiException(HttpStatusCode.Unauthorized, "invalid_credentials", "Invalid login or password")
        return issueTokens(u.id, u.role.name)
    }

    suspend fun refresh(rawToken: String): TokenResponse {
        val hash = refreshTokens.hashToken(rawToken)
        val userId = refreshTokens.findValidByHash(hash)
            ?: throw ApiException(HttpStatusCode.Unauthorized, "invalid_refresh", "Invalid or expired refresh token")
        val u = users.findById(userId)
            ?: throw ApiException(HttpStatusCode.Unauthorized, "invalid_refresh", "User not found")
        if (u.status != UserStatus.ACTIVE) {
            throw ApiException(HttpStatusCode.Unauthorized, "account_inactive", "Account is inactive")
        }
        refreshTokens.revokeByHash(hash)
        return issueTokens(u.id, u.role.name)
    }

    suspend fun logout(rawToken: String) {
        if (rawToken.isBlank()) return
        val hash = refreshTokens.hashToken(rawToken)
        refreshTokens.revokeByHash(hash)
    }

    private suspend fun issueTokens(userId: UUID, role: String): TokenResponse {
        val access = jwt.accessToken(userId, role)
        val raw = refreshTokens.newRawToken()
        val hash = refreshTokens.hashToken(raw)
        val exp = OffsetDateTime.now(ZoneOffset.UTC).plusDays(cfg.refreshTokenDays)
        refreshTokens.insert(userId, hash, exp)
        return TokenResponse(accessToken = access, refreshToken = raw)
    }

    private fun conflict(code: String): Nothing =
        throw ApiException(HttpStatusCode.Conflict, code, "Already exists")

    suspend fun seedAdminIfNeeded() {
        val email = cfg.seedAdminEmail ?: return
        val password = cfg.seedAdminPassword ?: return
        if (users.findByEmail(email) != null) return
        val id = UUID.randomUUID()
        val hash = BCrypt.hashpw(password, BCrypt.gensalt(12))
        val phone = cfg.seedAdminPhone?.trim()?.takeIf { it.isNotEmpty() }
        users.insert(
            id = id,
            email = email,
            phone = phone,
            firstName = "Admin",
            lastName = "User",
            passwordHash = hash,
            role = Role.ADMIN,
            status = UserStatus.ACTIVE,
        )
    }
}
