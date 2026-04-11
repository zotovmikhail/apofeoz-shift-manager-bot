package com.apofeoz.backend.data

import com.apofeoz.backend.domain.WorkerStatus
import java.time.OffsetDateTime
import java.util.*

data class WorkerEntity(
    val id: UUID,
    val userId: UUID?,
    val foremanId: UUID,
    val firstName: String,
    val lastName: String,
    val position: String?,
    val status: WorkerStatus,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)
