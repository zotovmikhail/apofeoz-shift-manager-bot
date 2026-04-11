package com.apofeoz.backend.service

import com.apofeoz.backend.api.SessionResponse
import com.apofeoz.backend.data.SessionRepository
import com.apofeoz.backend.domain.Role
import java.util.UUID

class SessionService(private val sessions: SessionRepository) {
    suspend fun listActive(actorId: UUID, role: Role): List<SessionResponse> {
        return when (role) {
            Role.FOREMAN -> sessions.listActiveByForeman(actorId).map { it.toResponse() }
            Role.ADMIN -> emptyList() // пока не нужно в UI
            Role.USER -> emptyList()
        }
    }

    private fun com.apofeoz.backend.data.SessionEntity.toResponse() = SessionResponse(
        id = id.toString(),
        workerId = workerId.toString(),
        foremanId = foremanId.toString(),
        startAt = startAt.toString(),
        endAt = endAt?.toString(),
        status = status.name,
    )
}

