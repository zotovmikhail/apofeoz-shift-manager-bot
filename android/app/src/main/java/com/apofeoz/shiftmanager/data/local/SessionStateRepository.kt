package com.apofeoz.shiftmanager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore("shift_session")

@Serializable
data class LocalActiveSession(
    val workerId: String,
    val sessionId: String,
    val startAt: String,
    val state: String = SessionStateRepository.STATE_ACTIVE,
)

class SessionStateRepository(private val context: Context) {
    private val store = context.sessionDataStore
    private val legacyKey = stringPreferencesKey("active")
    private val sessionsKey = stringPreferencesKey("activeSessions")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getActiveSessions(): List<LocalActiveSession> {
        val prefs = store.data.first()
        val raw = prefs[sessionsKey]
        if (!raw.isNullOrBlank()) {
            return runCatching { json.decodeFromString<List<LocalActiveSession>>(raw) }
                .getOrElse {
                    // back-compat: older stored format included clientSessionId (ignored)
                    runCatching {
                        val legacyList = json.decodeFromString<List<LocalActiveSessionLegacy>>(raw)
                        legacyList.map {
                            LocalActiveSession(workerId = it.workerId, sessionId = it.sessionId, startAt = it.startAt)
                        }
                    }.getOrElse { emptyList() }
                }
        }
        // миграция со старого формата (одна активная смена)
        val legacyRaw = prefs[legacyKey] ?: return emptyList()
        val migrated = runCatching { json.decodeFromString<LocalActiveSession>(legacyRaw) }.getOrNull()
            ?.let { listOf(it) }
            ?: runCatching { json.decodeFromString<LocalActiveSessionLegacy>(legacyRaw) }.getOrNull()
                ?.let { listOf(LocalActiveSession(workerId = it.workerId, sessionId = it.sessionId, startAt = it.startAt)) }
            ?: return emptyList()
        setActiveSessions(migrated)
        store.edit { it.remove(legacyKey) }
        return migrated
    }

    @Serializable
    private data class LocalActiveSessionLegacy(
        val workerId: String,
        val sessionId: String = "",
        val clientSessionId: String = "",
        val startAt: String,
    )

    suspend fun setActiveSessions(sessions: List<LocalActiveSession>) {
        store.edit { prefs ->
            if (sessions.isEmpty()) {
                prefs.remove(sessionsKey)
            } else {
                prefs[sessionsKey] = json.encodeToString(sessions)
            }
        }
    }

    suspend fun getActiveFor(workerId: String): LocalActiveSession? =
        getActiveSessions().firstOrNull { it.workerId == workerId }

    suspend fun upsert(session: LocalActiveSession) {
        val cur = getActiveSessions().filterNot { it.workerId == session.workerId }
        setActiveSessions(cur + session)
    }

    suspend fun remove(workerId: String) {
        val cur = getActiveSessions()
        setActiveSessions(cur.filterNot { it.workerId == workerId })
    }

    suspend fun removeBySessionId(sessionId: String) {
        if (sessionId.isBlank()) return
        val cur = getActiveSessions()
        setActiveSessions(cur.filterNot { it.sessionId == sessionId })
    }

    suspend fun markEnding(workerId: String) {
        val cur = getActiveSessions()
        val updated = cur.map {
            if (it.workerId == workerId) it.copy(state = STATE_ENDING) else it
        }
        setActiveSessions(updated)
    }

    // Back-compat: ранее хранилась только одна активная смена.
    suspend fun getActive(): LocalActiveSession? = getActiveSessions().firstOrNull()

    // Back-compat: null очищает всё, иначе делает её единственной активной.
    suspend fun setActive(session: LocalActiveSession?) {
        setActiveSessions(if (session == null) emptyList() else listOf(session))
    }

    companion object {
        const val STATE_ACTIVE = "ACTIVE"
        const val STATE_ENDING = "ENDING"
    }
}
