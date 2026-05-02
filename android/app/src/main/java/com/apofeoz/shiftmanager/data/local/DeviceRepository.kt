package com.apofeoz.shiftmanager.data.local

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apofeoz.shiftmanager.BuildConfig
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.deviceDataStore: DataStore<Preferences> by preferencesDataStore("device_identity")

data class DeviceInfo(
    val deviceId: String,
    val appVersion: String,
    val platform: String = "android",
    val deviceModel: String,
    val osVersion: String,
)

class DeviceRepository(private val context: Context) {
    private val store = context.deviceDataStore
    private val deviceIdKey = stringPreferencesKey("device_id")

    suspend fun getDeviceId(): String {
        val existing = store.data.first()[deviceIdKey]
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString()
        store.edit { it[deviceIdKey] = generated }
        return generated
    }

    suspend fun getDeviceInfo(): DeviceInfo = DeviceInfo(
        deviceId = getDeviceId(),
        appVersion = BuildConfig.VERSION_NAME,
        deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
        osVersion = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString(),
    )
}
