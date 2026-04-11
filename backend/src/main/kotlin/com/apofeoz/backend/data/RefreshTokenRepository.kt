package com.apofeoz.backend.data

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class RefreshTokenRepository {

    fun hashToken(raw: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun newRawToken(): String {
        val b = ByteArray(48).also { SecureRandom().nextBytes(it) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b)
    }

    suspend fun insert(userId: UUID, tokenHash: String, expiresAt: OffsetDateTime) = newSuspendedTransaction(Dispatchers.IO) {
        val id = UUID.randomUUID()
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        RefreshTokens.insert {
            it[RefreshTokens.id] = id
            it[RefreshTokens.userId] = EntityID(userId, Users)
            it[RefreshTokens.tokenHash] = tokenHash
            it[RefreshTokens.expiresAt] = expiresAt
            it[RefreshTokens.revokedAt] = null
            it[RefreshTokens.createdAt] = now
        }
    }

    suspend fun findValidByHash(tokenHash: String): UUID? = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        RefreshTokens.select {
                (RefreshTokens.tokenHash eq tokenHash) and
                    (RefreshTokens.revokedAt.isNull()) and
                    (RefreshTokens.expiresAt greaterEq now)
            }
            .map { it[RefreshTokens.userId].value }
            .singleOrNull()
    }

    suspend fun revokeByHash(tokenHash: String) = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        RefreshTokens.update({ RefreshTokens.tokenHash eq tokenHash }) {
            it[RefreshTokens.revokedAt] = now
        }
    }

    suspend fun revokeAllForUser(userId: UUID) = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        RefreshTokens.update({ RefreshTokens.userId eq EntityID(userId, Users) }) {
            it[RefreshTokens.revokedAt] = now
        }
    }

    suspend fun deleteExpired() = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        RefreshTokens.deleteWhere { RefreshTokens.expiresAt lessEq now }
    }
}
