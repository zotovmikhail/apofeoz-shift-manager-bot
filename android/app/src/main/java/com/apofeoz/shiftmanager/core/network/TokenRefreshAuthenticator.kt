package com.apofeoz.shiftmanager.core.network

import com.apofeoz.shiftmanager.core.di.AppContainer
import com.apofeoz.shiftmanager.data.local.TokenRepository
import com.apofeoz.shiftmanager.data.remote.dto.RefreshRequestDto
import com.apofeoz.shiftmanager.data.remote.dto.TokenResponseDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * При 401: POST /auth/refresh отдельным клиентом, сохранение токенов, повтор запроса.
 * Синхронизация [lock] — один refresh при параллельных 401.
 */
class TokenRefreshAuthenticator(
    private val baseUrl: String,
    private val tokens: TokenRepository,
    private val json: Json,
) : Authenticator {

    private val refreshClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (path.contains("/auth/login") ||
            path.contains("/auth/register") ||
            path.contains("/auth/refresh")
        ) {
            return null
        }
        if (response.priorResponse != null) {
            return null
        }
        synchronized(lock) {
            val refreshToken = runBlocking { tokens.getRefreshToken() } ?: run {
                runBlocking { tokens.clear() }
                AppContainer.notifySessionExpired()
                return null
            }
            val result = refreshTokens(refreshToken)
            val pair = when (result) {
                is RefreshResult.Success -> result.accessToken to result.refreshToken
                RefreshResult.AuthRejected -> {
                    runBlocking { tokens.clear() }
                    AppContainer.notifySessionExpired()
                    return null
                }
                RefreshResult.NetworkFailure -> return null
            }
            runBlocking { tokens.save(pair.first, pair.second) }
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${pair.first}")
                .build()
        }
    }

    private fun refreshTokens(refreshToken: String): RefreshResult {
        val url = "${baseUrl.trimEnd('/')}/api/v1/auth/refresh"
        val bodyJson = json.encodeToString(RefreshRequestDto(refreshToken))
        val body = bodyJson.toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Content-Type", "application/json")
            .build()
        return try {
            refreshClient.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) return RefreshResult.AuthRejected
                if (!resp.isSuccessful) return RefreshResult.NetworkFailure
                val text = resp.body?.string() ?: return RefreshResult.NetworkFailure
                val parsed = runCatching {
                    json.decodeFromString(TokenResponseDto.serializer(), text)
                }.getOrNull() ?: return RefreshResult.NetworkFailure
                RefreshResult.Success(parsed.accessToken, parsed.refreshToken)
            }
        } catch (_: IOException) {
            RefreshResult.NetworkFailure
        } catch (_: Exception) {
            RefreshResult.NetworkFailure
        }
    }

    private sealed interface RefreshResult {
        data class Success(val accessToken: String, val refreshToken: String) : RefreshResult
        data object AuthRejected : RefreshResult
        data object NetworkFailure : RefreshResult
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
