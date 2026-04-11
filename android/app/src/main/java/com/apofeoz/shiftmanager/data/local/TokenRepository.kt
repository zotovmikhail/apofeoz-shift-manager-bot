package com.apofeoz.shiftmanager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore: DataStore<Preferences> by preferencesDataStore("shift_auth")

class TokenRepository(private val context: Context) {
    private val store = context.authDataStore
    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")

    val accessTokenFlow: Flow<String?> = store.data.map { it[accessKey] }

    suspend fun getAccessToken(): String? = store.data.map { it[accessKey] }.first()

    suspend fun getRefreshToken(): String? = store.data.map { it[refreshKey] }.first()

    suspend fun save(access: String, refresh: String) {
        store.edit {
            it[accessKey] = access
            it[refreshKey] = refresh
        }
    }

    suspend fun clear() {
        store.edit {
            it.remove(accessKey)
            it.remove(refreshKey)
        }
    }
}
