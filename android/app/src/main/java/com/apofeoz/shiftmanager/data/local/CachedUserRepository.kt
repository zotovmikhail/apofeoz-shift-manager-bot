package com.apofeoz.shiftmanager.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apofeoz.shiftmanager.data.remote.dto.UserResponseDto
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.OffsetDateTime
import java.time.ZoneOffset

private val Context.cachedUserDataStore: DataStore<Preferences> by preferencesDataStore("cached_user")

@Serializable
data class CachedUser(
    val id: String,
    val email: String? = null,
    val phone: String? = null,
    val firstName: String,
    val lastName: String,
    val role: String,
    val status: String,
    val lastVerifiedAt: String,
)

class CachedUserRepository(private val context: Context) {
    private val store = context.cachedUserDataStore
    private val key = stringPreferencesKey("user")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(): CachedUser? {
        val raw = store.data.first()[key] ?: return null
        return runCatching { json.decodeFromString(CachedUser.serializer(), raw) }.getOrNull()
    }

    suspend fun save(user: UserResponseDto) {
        val cached = CachedUser(
            id = user.id,
            email = user.email,
            phone = user.phone,
            firstName = user.firstName,
            lastName = user.lastName,
            role = user.role,
            status = user.status,
            lastVerifiedAt = OffsetDateTime.now(ZoneOffset.UTC).toString(),
        )
        store.edit { it[key] = json.encodeToString(CachedUser.serializer(), cached) }
    }

    suspend fun clear() {
        store.edit { it.remove(key) }
    }
}

fun CachedUser.toUserResponseDto(): UserResponseDto = UserResponseDto(
    id = id,
    email = email,
    phone = phone,
    firstName = firstName,
    lastName = lastName,
    role = role,
    status = status,
)
