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

class LocalFailedOutboundBatchesRepository(private val context: Context) {
    private val store = context.localFailedBatchesDataStore
    private val key = stringPreferencesKey("failed")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun list(): List<LocalFailedBatch> {
        val prefs = store.data.first()
        val raw = prefs[key] ?: return emptyList()
        return runCatching { json.decodeFromString(LocalFailedBatchState.serializer(), raw).items }
            .getOrElse { emptyList() }
    }

    suspend fun add(item: LocalFailedBatch) {
        val cur = list()
        val capped = (listOf(item) + cur).take(50)
        store.edit { it[key] = json.encodeToString(LocalFailedBatchState.serializer(), LocalFailedBatchState(capped)) }
    }

    suspend fun remove(id: String) {
        if (id.isBlank()) return
        val cur = list()
        val next = cur.filterNot { it.id == id }
        store.edit { prefs ->
            if (next.isEmpty()) {
                prefs.remove(key)
            } else {
                prefs[key] = json.encodeToString(LocalFailedBatchState.serializer(), LocalFailedBatchState(next))
            }
        }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }
}

