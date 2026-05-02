package com.apofeoz.shiftmanager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apofeoz.shiftmanager.data.remote.dto.WorkerResponseDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val Context.cachedWorkersDataStore: DataStore<Preferences> by preferencesDataStore("cached_workers")

@Serializable
data class CachedWorkers(
    val ownerUserId: String? = null,
    val items: List<WorkerResponseDto> = emptyList(),
    val fetchedAt: String? = null,
)

class CachedWorkersRepository(private val context: Context) {
    private val store = context.cachedWorkersDataStore
    private val key = stringPreferencesKey("workers")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(): CachedWorkers {
        val raw = store.data.first()[key] ?: return CachedWorkers()
        return runCatching { json.decodeFromString(CachedWorkers.serializer(), raw) }
            .getOrElse { CachedWorkers() }
    }

    suspend fun save(ownerUserId: String?, items: List<WorkerResponseDto>) {
        val cached = CachedWorkers(
            ownerUserId = ownerUserId,
            items = items,
            fetchedAt = OffsetDateTime.now(ZoneOffset.UTC).toString(),
        )
        store.edit { it[key] = json.encodeToString(CachedWorkers.serializer(), cached) }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }
}
