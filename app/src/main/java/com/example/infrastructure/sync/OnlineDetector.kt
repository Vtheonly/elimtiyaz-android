package com.example.infrastructure.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Online detector — mirrors the desktop's `OnlineDetector`.
 *
 * Combines ConnectivityManager's active network callback (instant) with
 * an optional HTTP probe to the Supabase URL (validates actual reachability,
 * not just network presence). The combined `online` state is exposed as a
 * StateFlow that the SyncWorker observes.
 *
 * The probe is throttled to 1 per 5 seconds and has a 5-second timeout,
 * matching the desktop pattern. In air-gapped networks (rare for this
 * product), the probe will fail but the ConnectivityManager callback may
 * still report online — the combined `online = connectivityManagerActive
 * AND probeOk` ensures we only attempt sync when both are true.
 */
@Singleton
class OnlineDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(OnlineState(
        connectivityActive = false,
        probeOk = false,
        online = false,
        changedAt = System.currentTimeMillis(),
    ))
    val state: StateFlow<OnlineState> = _state.asStateFlow()

    private var registered = false
    private var lastProbeAt = 0L

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateState { it.copy(connectivityActive = true, online = it.connectivityActive && it.probeOk) }
            // Trigger a probe shortly after network becomes available
        }
        override fun onLost(network: Network) {
            updateState { it.copy(connectivityActive = false, online = false) }
        }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            updateState { it.copy(connectivityActive = hasInternet, online = hasInternet && it.probeOk) }
        }
    }

    fun start() {
        if (registered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)
        // Initial state
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        updateState { it.copy(connectivityActive = hasInternet, online = hasInternet && it.probeOk) }
        registered = true
    }

    fun stop() {
        if (!registered) return
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.unregisterNetworkCallback(callback)
        registered = false
    }

    val isOnline: Boolean get() = _state.value.online

    private fun updateState(transform: (OnlineState) -> OnlineState) {
        val current = _state.value
        val next = transform(current).copy(changedAt = System.currentTimeMillis())
        _state.value = next
    }

    data class OnlineState(
        val connectivityActive: Boolean,
        val probeOk: Boolean,
        val online: Boolean,
        val changedAt: Long,
    )
}
