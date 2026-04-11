package com.apofeoz.shiftmanager.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.testingDataStore by preferencesDataStore("shift_testing")

/**
 * Только для тестов: симуляция offline при реальной сети (бригадир в профиле).
 */
class TestConnectivityOverrideRepository(private val context: Context) {
    private val store = context.testingDataStore
    private val forceOfflineKey = booleanPreferencesKey("force_offline_for_testing")

    val forceOfflineFlow: Flow<Boolean> = store.data.map { it[forceOfflineKey] ?: false }

    suspend fun setForceOffline(value: Boolean) {
        store.edit { it[forceOfflineKey] = value }
    }
}
