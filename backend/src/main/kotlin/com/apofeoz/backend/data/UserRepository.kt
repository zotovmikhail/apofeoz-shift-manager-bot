package com.apofeoz.backend.data

import com.apofeoz.backend.domain.Role
import com.apofeoz.backend.domain.UserStatus
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.neq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*

class UserRepository {

    private fun ResultRow.toUser() = UserEntity(
        id = this[Users.id].value,
        email = this[Users.email],
        phone = this[Users.phone],
        firstName = this[Users.firstName],
        lastName = this[Users.lastName],
        passwordHash = this[Users.passwordHash],
        role = Role.valueOf(this[Users.role]),
        status = UserStatus.valueOf(this[Users.status]),
        createdAt = this[Users.createdAt],
        updatedAt = this[Users.updatedAt],
    )

    suspend fun insert(
        id: UUID,
        email: String?,
        phone: String?,
        firstName: String,
        lastName: String,
        passwordHash: String,
        role: Role,
        status: UserStatus,
    ): UserEntity = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        Users.insert {
            it[Users.id] = id
            it[Users.email] = email
            it[Users.phone] = phone
            it[Users.firstName] = firstName
            it[Users.lastName] = lastName
            it[Users.passwordHash] = passwordHash
            it[Users.role] = role.name
            it[Users.status] = status.name
            it[Users.createdAt] = now
            it[Users.updatedAt] = now
        }
        Users.selectAll().where { Users.id eq id }.single().toUser()
    }

    suspend fun findById(id: UUID): UserEntity? = newSuspendedTransaction(Dispatchers.IO) {
        Users.selectAll().where { Users.id eq id }.map { it.toUser() }.singleOrNull()
    }

    suspend fun findByEmail(email: String): UserEntity? = newSuspendedTransaction(Dispatchers.IO) {
        Users.selectAll().where { Users.email eq email }.map { it.toUser() }.singleOrNull()
    }

    suspend fun findByPhone(phone: String): UserEntity? = newSuspendedTransaction(Dispatchers.IO) {
        Users.selectAll().where { Users.phone eq phone }.map { it.toUser() }.singleOrNull()
    }

    suspend fun findByLogin(login: String): UserEntity? {
        val trimmed = login.trim()
        return findByEmail(trimmed) ?: findByPhone(trimmed)
    }

    suspend fun listAll(): List<UserEntity> = newSuspendedTransaction(Dispatchers.IO) {
        Users.selectAll().orderBy(Users.createdAt, SortOrder.DESC).map { it.toUser() }
    }

    suspend fun update(
        id: UUID,
        role: Role? = null,
        status: UserStatus? = null,
        firstName: String? = null,
        lastName: String? = null,
    ): UserEntity? = newSuspendedTransaction(Dispatchers.IO) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        val updated = Users.update({ Users.id eq id }) {
            role?.let { r -> it[Users.role] = r.name }
            status?.let { s -> it[Users.status] = s.name }
            firstName?.let { f -> it[Users.firstName] = f }
            lastName?.let { l -> it[Users.lastName] = l }
            it[Users.updatedAt] = now
        }
        if (updated == 0) null
        else Users.selectAll().where { Users.id eq id }.singleOrNull()?.toUser()
    }

    suspend fun countActiveAdmins(excludeUserId: UUID? = null): Int = newSuspendedTransaction(Dispatchers.IO) {
        var cond: Op<Boolean> = (Users.role eq Role.ADMIN.name) and (Users.status eq UserStatus.ACTIVE.name)
        if (excludeUserId != null) {
            cond = cond and (Users.id neq excludeUserId)
        }
        Users.selectAll().where { cond }.count().toInt()
    }
}
