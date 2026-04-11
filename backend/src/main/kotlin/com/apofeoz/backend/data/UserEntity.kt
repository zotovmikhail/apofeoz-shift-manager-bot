package com.apofeoz.backend.data

import com.apofeoz.backend.domain.Role
import com.apofeoz.backend.domain.UserStatus
import java.time.OffsetDateTime
import java.util.*

data class UserEntity(
    val id: UUID,
    val email: String?,
    val phone: String?,
    val firstName: String,
    val lastName: String,
    val passwordHash: String,
    val role: Role,
    val status: UserStatus,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
