package com.apofeoz.shiftmanager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.authStateDataStore: DataStore<Preferences> by preferencesDataStore("auth_state")

class AuthStateRepository(private val context: Context) {
    private val store = context.authStateDataStore
    private val authRejectedKey = booleanPreferencesKey("auth_rejected")

    suspend fun isAuthRejected(): Boolean = store.data.first()[authRejectedKey] == true

    suspend fun setAuthRejected(value: Boolean) {
        store.edit { prefs ->
            if (value) {
                prefs[authRejectedKey] = true
            } else {
                prefs.remove(authRejectedKey)
            }
        }
    }
}
