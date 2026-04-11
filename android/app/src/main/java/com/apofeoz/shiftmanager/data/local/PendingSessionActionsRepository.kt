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

private val Context.pendingSessionActionsDataStore: DataStore<Preferences> by preferencesDataStore("pending_session_actions")

@Serializable
private data class PendingSessionActions(
    val endingSessionIds: Set<String> = emptySet(),
    val blockedWorkerIds: Set<String> = emptySet(),
    val blockedSessionIds: Set<String> = emptySet(),
)

class PendingSessionActionsRepository(private val context: Context) {
    private val store = context.pendingSessionActionsDataStore
    private val key = stringPreferencesKey("pending")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getEndingSessionIds(): Set<String> {
        val prefs = store.data.first()
        val raw = prefs[key] ?: return emptySet()
        return runCatching { json.decodeFromString(PendingSessionActions.serializer(), raw).endingSessionIds }
            .getOrElse { emptySet() }
    }

    suspend fun getBlockedWorkerIds(): Set<String> {
        val prefs = store.data.first()
        val raw = prefs[key] ?: return emptySet()
        return runCatching { json.decodeFromString(PendingSessionActions.serializer(), raw).blockedWorkerIds }
            .getOrElse { emptySet() }
    }

    suspend fun getBlockedSessionIds(): Set<String> {
        val prefs = store.data.first()
        val raw = prefs[key] ?: return emptySet()
        return runCatching { json.decodeFromString(PendingSessionActions.serializer(), raw).blockedSessionIds }
            .getOrElse { emptySet() }
    }

    suspend fun addEnding(sessionId: String) {
        if (sessionId.isBlank()) return
        val cur = getEndingSessionIds()
        if (sessionId in cur) return
        val st = getState()
        setState(st.copy(endingSessionIds = st.endingSessionIds + sessionId))
    }

    suspend fun removeEnding(sessionId: String) {
        if (sessionId.isBlank()) return
        val st = getState()
        if (sessionId !in st.endingSessionIds) return
        setState(st.copy(endingSessionIds = st.endingSessionIds - sessionId))
    }

    suspend fun addBlockedWorker(workerId: String) {
        if (workerId.isBlank()) return
        val st = getState()
        if (workerId in st.blockedWorkerIds) return
        setState(st.copy(blockedWorkerIds = st.blockedWorkerIds + workerId))
    }

    suspend fun addBlockedSession(sessionId: String) {
        if (sessionId.isBlank()) return
        val st = getState()
        if (sessionId in st.blockedSessionIds) return
        setState(st.copy(blockedSessionIds = st.blockedSessionIds + sessionId))
    }

    suspend fun clearBlockedForWorker(workerId: String) {
        if (workerId.isBlank()) return
        val st = getState()
        if (workerId !in st.blockedWorkerIds) return
        setState(st.copy(blockedWorkerIds = st.blockedWorkerIds - workerId))
    }

    suspend fun clearBlockedForSession(sessionId: String) {
        if (sessionId.isBlank()) return
        val st = getState()
        if (sessionId !in st.blockedSessionIds) return
        setState(st.copy(blockedSessionIds = st.blockedSessionIds - sessionId))
    }

    private suspend fun getState(): PendingSessionActions {
        val prefs = store.data.first()
        val raw = prefs[key] ?: return PendingSessionActions()
        return runCatching { json.decodeFromString(PendingSessionActions.serializer(), raw) }
            .getOrElse { PendingSessionActions() }
    }

    private suspend fun setState(value: PendingSessionActions) {
        store.edit { prefs ->
            val isEmpty = value.endingSessionIds.isEmpty() &&
                value.blockedWorkerIds.isEmpty() &&
                value.blockedSessionIds.isEmpty()
            if (isEmpty) {
                prefs.remove(key)
            } else {
                prefs[key] = json.encodeToString(PendingSessionActions.serializer(), value)
            }
        }
    }
}

