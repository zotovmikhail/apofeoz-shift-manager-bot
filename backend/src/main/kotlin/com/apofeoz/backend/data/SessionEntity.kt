package com.apofeoz.backend.data

import com.apofeoz.backend.domain.SessionStatus
import java.time.OffsetDateTime
import java.util.*

data class SessionEntity(
    val id: UUID,
    val workerId: UUID,
    val foremanId: UUID,
    val startAt: OffsetDateTime,
    val endAt: OffsetDateTime?,
    val status: SessionStatus,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
