package com.apofeoz.shiftmanager.core.network

import com.apofeoz.shiftmanager.data.local.TokenRepository
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val tokens: TokenRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        if (path.contains("/auth/register") ||
            path.contains("/auth/login") ||
            path.contains("/auth/refresh")
        ) {
            return chain.proceed(original)
        }
        val access = runBlocking { tokens.getAccessToken() } ?: return chain.proceed(original)
        val request = original.newBuilder()
            .header("Authorization", "Bearer $access")
            .build()
        return chain.proceed(request)
    }
}
