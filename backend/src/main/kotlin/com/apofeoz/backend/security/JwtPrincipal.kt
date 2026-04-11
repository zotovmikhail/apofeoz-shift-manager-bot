package com.apofeoz.backend.security

import io.ktor.server.auth.*
import java.util.*

data class JwtUserPrincipal(val userId: UUID, val role: String) : Principal
