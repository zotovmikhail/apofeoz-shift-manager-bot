package com.apofeoz.shiftmanager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

private val Context.localFailedBatchesDataStore: DataStore<Preferences> by preferencesDataStore("local_failed_batches")

@Serializable
data class LocalFailedBatch(
    val id: String = UUID.randomUUID().toString(),
    val httpCode: Int,
    val message: String,
    val submittedAt: String,
    val bodyJson: String,
    val failedIndex: Int? = null,
    val reason: String? = null,
    val failedEventType: String? = null,
)

@Serializable
private data class LocalFailedBatchState(val items: List<LocalFailedBatch> = emptyList())

class LocalFailedOutboundBatchesRepository(
    private val context: Context,
    private val db: ShiftDatabase,
) {
    private val store = context.localFailedBatchesDataStore
    private val key = stringPreferencesKey("failed")
    private val json = Json { ignoreUnknownKeys = true }
    private val dao get() = db.localFailedDao()

    suspend fun list(): List<LocalFailedBatch> {
        migrateLegacyIfNeeded()
        return dao.list().map { it.toModel() }
    }

    suspend fun add(item: LocalFailedBatch) {
        migrateLegacyIfNeeded()
        dao.insert(item.toEntity())
    }

    suspend fun addAll(items: List<LocalFailedBatch>) {
        if (items.isEmpty()) return
        migrateLegacyIfNeeded()
        dao.insertAll(items.map { it.toEntity() })
    }

    suspend fun remove(id: String) {
        if (id.isBlank()) return
        migrateLegacyIfNeeded()
        dao.deleteById(id)
    }

    suspend fun clear() {
        dao.clear()
        store.edit { it.remove(key) }
    }

    private suspend fun migrateLegacyIfNeeded() {
        if (dao.count() > 0) return
        val prefs = store.data.first()
        val raw = prefs[key] ?: return
        val legacy = runCatching { json.decodeFromString(LocalFailedBatchState.serializer(), raw).items }
            .getOrElse { emptyList() }
        if (legacy.isNotEmpty()) {
            dao.insertAll(legacy.map { it.toEntity() })
        }
        store.edit { it.remove(key) }
    }

    private fun LocalFailedBatch.toEntity(): LocalFailedBatchEntity = LocalFailedBatchEntity(
        id = id,
        httpCode = httpCode,
        message = message,
        submittedAt = submittedAt,
        bodyJson = bodyJson,
        failedIndex = failedIndex,
        reason = reason,
        failedEventType = failedEventType,
        createdAt = OffsetDateTime.now(ZoneOffset.UTC).toString(),
    )

    private fun LocalFailedBatchEntity.toModel(): LocalFailedBatch = LocalFailedBatch(
        id = id,
        httpCode = httpCode,
        message = message,
        submittedAt = submittedAt,
        bodyJson = bodyJson,
        failedIndex = failedIndex,
        reason = reason,
        failedEventType = failedEventType,
    )
}

