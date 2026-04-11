package com.apofeoz.backend.api

import io.ktor.http.*
import kotlinx.serialization.json.JsonElement

class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val payload: Map<String, JsonElement> = emptyMap(),
) : RuntimeException(message)
