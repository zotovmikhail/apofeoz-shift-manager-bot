package com.apofeoz.shiftmanager.core.di

import android.app.Application
import androidx.room.Room
import com.apofeoz.shiftmanager.BuildConfig
import com.apofeoz.shiftmanager.core.network.ApiClient
import com.apofeoz.shiftmanager.core.network.NetworkStatusRepository
import com.apofeoz.shiftmanager.data.local.ActiveSessionsCacheRepository
import com.apofeoz.shiftmanager.data.local.AuthStateRepository
import com.apofeoz.shiftmanager.data.local.CachedWorkersRepository
import com.apofeoz.shiftmanager.data.local.CachedUserRepository
import com.apofeoz.shiftmanager.data.local.DeviceRepository
import com.apofeoz.shiftmanager.data.local.LocalFailedOutboundBatchesRepository
import com.apofeoz.shiftmanager.data.local.PendingSessionActionsRepository
import com.apofeoz.shiftmanager.data.local.ShiftDatabase
import com.apofeoz.shiftmanager.data.local.SessionStateRepository
import com.apofeoz.shiftmanager.data.local.SyncStatusRepository
import com.apofeoz.shiftmanager.data.local.TestConnectivityOverrideRepository
import com.apofeoz.shiftmanager.data.local.TokenRepository
import com.apofeoz.shiftmanager.data.remote.ApofeozApi
import com.apofeoz.shiftmanager.data.repository.OutboundBatchQueueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.json.Json

object AppContainer {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    fun notifySessionExpired() {
        _sessionExpired.tryEmit(Unit)
    }

    lateinit var tokenRepository: TokenRepository
    lateinit var sessionStateRepository: SessionStateRepository
    lateinit var syncStatusRepository: SyncStatusRepository
    lateinit var database: ShiftDatabase
    lateinit var api: ApofeozApi
    lateinit var jsonFormat: Json
    lateinit var batchQueue: OutboundBatchQueueRepository
    lateinit var networkStatus: NetworkStatusRepository
    lateinit var testConnectivityOverride: TestConnectivityOverrideRepository
    lateinit var activeSessionsCache: ActiveSessionsCacheRepository
    lateinit var pendingSessionActions: PendingSessionActionsRepository
    lateinit var localFailedBatches: LocalFailedOutboundBatchesRepository
    lateinit var cachedUserRepository: CachedUserRepository
    lateinit var cachedWorkersRepository: CachedWorkersRepository
    lateinit var authStateRepository: AuthStateRepository
    lateinit var deviceRepository: DeviceRepository

    fun init(app: Application) {
        jsonFormat = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
        tokenRepository = TokenRepository(app)
        sessionStateRepository = SessionStateRepository(app)
        syncStatusRepository = SyncStatusRepository(app)
        activeSessionsCache = ActiveSessionsCacheRepository(app)
        pendingSessionActions = PendingSessionActionsRepository(app)
        cachedUserRepository = CachedUserRepository(app)
        cachedWorkersRepository = CachedWorkersRepository(app)
        authStateRepository = AuthStateRepository(app)
        deviceRepository = DeviceRepository(app)
        database = Room.databaseBuilder(app, ShiftDatabase::class.java, "shift.db")
            .addMigrations(ShiftDatabase.MIGRATION_1_2, ShiftDatabase.MIGRATION_2_3, ShiftDatabase.MIGRATION_3_4)
            .build()
        localFailedBatches = LocalFailedOutboundBatchesRepository(app, database)
        val baseUrl = BuildConfig.API_BASE_URL.trimEnd('/') + "/"
        api = ApiClient.create(baseUrl, tokenRepository, jsonFormat, BuildConfig.DEBUG)
        batchQueue = OutboundBatchQueueRepository(app, database, jsonFormat)
        testConnectivityOverride = TestConnectivityOverrideRepository(app)
        networkStatus = NetworkStatusRepository(app, testConnectivityOverride.forceOfflineFlow)
    }
}
