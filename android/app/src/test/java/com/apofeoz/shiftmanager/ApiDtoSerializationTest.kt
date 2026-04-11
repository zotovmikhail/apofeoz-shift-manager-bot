package com.apofeoz.shiftmanager

import com.apofeoz.shiftmanager.data.remote.dto.SyncBatchRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.SyncEventDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class ApiDtoSerializationTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun syncBatch_roundTrip_preservesEvents() {
        val req = SyncBatchRequestDto(
            batchUid = "batch-1",
            submittedAt = "2025-03-05T12:00:00Z",
            events = listOf(
                SyncEventDto(
                    type = "START_SESSION",
                    payload = buildJsonObject {
                        put("sessionId", "s-1")
                        put("workerId", "w-1")
                        put("startAt", "2025-03-05T08:00:00Z")
                    },
                ),
            ),
        )
        val encoded = json.encodeToString(SyncBatchRequestDto.serializer(), req)
        val decoded = json.decodeFromString(SyncBatchRequestDto.serializer(), encoded)
        assertEquals(req.batchUid, decoded.batchUid)
        assertEquals(req.submittedAt, decoded.submittedAt)
        assertEquals(1, decoded.events.size)
        assertEquals("START_SESSION", decoded.events[0].type)
    }
}
