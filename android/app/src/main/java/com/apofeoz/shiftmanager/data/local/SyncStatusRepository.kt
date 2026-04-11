package com.apofeoz.shiftmanager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

private val Context.syncStatusDataStore: DataStore<Preferences> by preferencesDataStore("sync_status")

class SyncStatusRepository(private val context: Context) {
    private val store = context.syncStatusDataStore

    private val lastSyncAtKey = stringPreferencesKey("lastSyncAt")

    suspend fun getLastSyncAt(): OffsetDateTime? {
        val raw = store.data.map { it[lastSyncAtKey] }.first() ?: return null
        return try {
            OffsetDateTime.parse(raw)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    suspend fun setLastSyncAt(now: OffsetDateTime) {
        store.edit { prefs -> prefs[lastSyncAtKey] = now.toString() }
    }
}

