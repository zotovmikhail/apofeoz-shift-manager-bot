package com.apofeoz.shiftmanager.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf

class NetworkStatusRepository(
    context: Context,
    private val forceOfflineForTesting: Flow<Boolean> = flowOf(false),
) {
    private val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private fun realConnectivityFlow(): Flow<Boolean> = callbackFlow {
        fun isUsableInternet(network: Network): Boolean {
            val caps = cm.getNetworkCapabilities(network) ?: return false
            if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
            // VALIDATED = прошла проверка выхода в интернет (лучший сигнал в проде).
            // На многих эмуляторах VALIDATED долго не появляется — UI был бы вечно Offline.
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return true
            // Явный captive portal без валидации — не считаем «нормальным» онлайном.
            if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL)) return false
            return true
        }

        fun currentOnline(): Boolean {
            val active = cm.activeNetwork
            if (active != null && isUsableInternet(active)) return true
            return cm.allNetworks.any(::isUsableInternet)
        }

        trySend(currentOnline())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentOnline())
            }

            override fun onLost(network: Network) {
                trySend(currentOnline())
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                trySend(currentOnline())
            }
        }

        cm.registerDefaultNetworkCallback(callback)

        awaitClose { runCatching { cm.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    fun isOnlineFlow(): Flow<Boolean> =
        combine(realConnectivityFlow(), forceOfflineForTesting) { real, forcedOffline ->
            !forcedOffline && real
        }.distinctUntilChanged()
}
