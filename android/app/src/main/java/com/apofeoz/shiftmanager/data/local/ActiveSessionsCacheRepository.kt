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

private val Context.activeSessionsCacheDataStore: DataStore<Preferences> by preferencesDataStore("active_sessions_cache")

@Serializable
data class CachedActiveSessions(
    val byWorkerId: Map<String, String> = emptyMap(), // workerId -> sessionId
    val fetchedAt: String? = null, // ISO-8601
)

class ActiveSessionsCacheRepository(private val context: Context) {
    private val store = context.activeSessionsCacheDataStore
    private val key = stringPreferencesKey("cache")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(): CachedActiveSessions {
        val prefs = store.data.first()
        val raw = prefs[key] ?: return CachedActiveSessions()
        return runCatching { json.decodeFromString(CachedActiveSessions.serializer(), raw) }
            .getOrElse { CachedActiveSessions() }
    }

    suspend fun set(value: CachedActiveSessions) {
        store.edit { it[key] = json.encodeToString(CachedActiveSessions.serializer(), value) }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }
}
