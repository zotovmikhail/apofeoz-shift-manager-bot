package com.apofeoz.shiftmanager.core.network



import com.apofeoz.shiftmanager.data.local.TokenRepository

import com.apofeoz.shiftmanager.data.remote.ApofeozApi

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory

import kotlinx.serialization.json.Json

import okhttp3.MediaType.Companion.toMediaType

import okhttp3.OkHttpClient

import okhttp3.logging.HttpLoggingInterceptor

import retrofit2.Retrofit

import java.util.concurrent.TimeUnit



object ApiClient {

    fun create(

        baseUrl: String,

        tokens: TokenRepository,

        json: Json,

        debug: Boolean,

    ): ApofeozApi {

        val normalizedBase = baseUrl.trimEnd('/') + "/"

        val logging = HttpLoggingInterceptor().apply {

            level = if (debug) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE

        }

        val authenticator = TokenRefreshAuthenticator(normalizedBase, tokens, json)

        val client = OkHttpClient.Builder()

            .connectTimeout(30, TimeUnit.SECONDS)

            .readTimeout(60, TimeUnit.SECONDS)

            .writeTimeout(60, TimeUnit.SECONDS)

            .addInterceptor(AuthInterceptor(tokens))

            .authenticator(authenticator)

            .apply { if (debug) addInterceptor(logging) }

            .build()

        val contentType = "application/json".toMediaType()

        return Retrofit.Builder()

            .baseUrl(normalizedBase)

            .client(client)

            .addConverterFactory(json.asConverterFactory(contentType))

            .build()

            .create(ApofeozApi::class.java)

    }

}

