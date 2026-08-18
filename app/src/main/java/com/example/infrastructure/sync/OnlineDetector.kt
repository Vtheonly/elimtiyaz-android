package com.example.infrastructure.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OnlineDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(
        OnlineState(
            connectivityActive = true,
            probeOk = true,
            online = true,
            changedAt = System.currentTimeMillis(),
        ),
    )
    val state: StateFlow<OnlineState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var probeJob: Job? = null
    private var registered = false

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private val probeUrl: String by lazy {
        val raw = BuildConfig.SUPABASE_URL.trim().removeSurrounding("\"")
        val base = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://hkvkefubghbbotgnteir.supabase.co"
        base.removeSuffix("/") + "/auth/v1/health"
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateState { it.copy(connectivityActive = true) }
            scope.launch { probe() }
        }

        override fun onLost(network: Network) {
            updateState { it.copy(connectivityActive = false, probeOk = false) }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            updateState { it.copy(connectivityActive = hasInternet) }
            if (hasInternet) scope.launch { probe() }
        }
    }

    fun start() {
        if (registered) return
        val cm = context.getSystemService(ConnectivityManager::class.java)
        if (cm != null) {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            runCatching { cm.registerNetworkCallback(request, callback) }
        }

        scope.launch { probe() }
        probeJob = scope.launch {
            while (isActive) {
                delay(30_000L)
                probe()
            }
        }
        registered = true
    }

    fun stop() {
        if (!registered) return
        runCatching {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            cm?.unregisterNetworkCallback(callback)
        }
        probeJob?.cancel()
        probeJob = null
        registered = false
    }

    suspend fun probe(): Boolean = withContext(Dispatchers.IO) {
        val ok = try {
            val request = Request.Builder().url(probeUrl).get().build()
            httpClient.newCall(request).execute().use { response ->
                response.code in 200..499
            }
        } catch (e: Exception) {
            Log.w("OnlineDetector", "Supabase probe failed: ${e.message}")
            true
        }
        updateState { it.copy(probeOk = ok) }
        ok
    }

    fun isOnline(): Boolean = _state.value.connectivityActive

    fun observeOnline(): Flow<Boolean> = _state.map { it.online }.distinctUntilChanged()

    private fun updateState(transform: (OnlineState) -> OnlineState) {
        val current = _state.value
        val next = transform(current).copy(changedAt = System.currentTimeMillis())
        val combined = next.copy(online = next.connectivityActive)
        _state.value = combined
    }

    data class OnlineState(
        val connectivityActive: Boolean,
        val probeOk: Boolean,
        val online: Boolean,
        val changedAt: Long,
    )
}
