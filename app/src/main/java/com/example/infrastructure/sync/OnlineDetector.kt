package com.example.infrastructure.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
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

/**
 * Online detector — mirrors the desktop's `OnlineDetector`.
 *
 * Combines two signals into a single `online` flag:
 *   1. **ConnectivityManager** active-network callback (instant — fires when
 *      the OS hands us a network interface with INTERNET capability).
 *   2. **HTTP HEAD probe** to `${supabaseUrl}/auth/v1/health` (3-second
 *      timeout). This validates actual reachability — the OS may report an
 *      interface as "up" while DNS is broken or a captive portal is
 *      intercepting traffic.
 *
 * The combined flag is `online = connectivityActive && probeOk`. The probe
 * runs on app start, immediately after every network callback transition,
 * and on a 30-second periodic loop while [start] has been called.
 *
 * The state is exposed both synchronously ([isOnline]) and as a reactive
 * flow ([observeOnline]) so the SyncService, SyncWorker, and UI indicators
 * can each consume it in the most ergonomic way.
 *
 * @param context Application context — used to fetch ConnectivityManager.
 */
@Singleton
class OnlineDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(
        OnlineState(
            connectivityActive = false,
            probeOk = false,
            online = false,
            changedAt = System.currentTimeMillis(),
        ),
    )
    val state: StateFlow<OnlineState> = _state.asStateFlow()

    /** Backing scope for the periodic probe loop. SupervisorJob keeps one probe failure from cancelling the loop. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var probeJob: Job? = null
    private var registered = false

    /** Single shared OkHttp client with the 3-second timeouts required by the spec. */
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /** Resolved probe URL — `${supabaseUrl}/auth/v1/health`. */
    private val probeUrl: String by lazy {
        val raw = BuildConfig.SUPABASE_URL.trim().removeSurrounding("\"")
        val base = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://demo.supabase.co"
        base.removeSuffix("/") + "/auth/v1/health"
    }

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            updateState { it.copy(connectivityActive = true) }
            // Re-probe immediately when a network interface becomes available.
            scope.launch { probe() }
        }

        override fun onLost(network: Network) {
            // Losing the network ⇒ both connectivity and reachability drop.
            updateState { it.copy(connectivityActive = false, probeOk = false) }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            updateState { it.copy(connectivityActive = hasInternet) }
            if (hasInternet) scope.launch { probe() }
        }
    }

    /**
     * Start monitoring. Idempotent — safe to call multiple times.
     *
     * Registers the ConnectivityManager callback, performs an initial probe,
     * and launches the 30-second periodic probe loop. The loop runs on
     * `Dispatchers.IO` and is cancelled by [stop].
     */
    fun start() {
        if (registered) return
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        // Initial connectivity state (synchronous read).
        val activeNetwork = cm.activeNetwork
        val capabilities = cm.getNetworkCapabilities(activeNetwork)
        val hasInternet = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        updateState { it.copy(connectivityActive = hasInternet) }

        // Immediate probe so we don't trust the initial CM state alone.
        scope.launch { probe() }

        // Periodic re-probe loop — runs while [start]ed.
        probeJob = scope.launch {
            while (isActive) {
                delay(PROBE_INTERVAL_MS)
                probe()
            }
        }
        registered = true
    }

    /**
     * Stop monitoring. Unregisters the ConnectivityManager callback and
     * cancels the periodic probe loop. Idempotent.
     */
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

    /**
     * Perform a single HEAD probe to the Supabase health endpoint.
     *
     * On HTTP 200 the probe is considered successful; on any other status
     * code, exception, or timeout the probe is considered failed. The
     * combined `online` flag is updated as a side-effect.
     *
     * @return `true` if the probe succeeded (HTTP 200), `false` otherwise.
     */
    suspend fun probe(): Boolean = withContext(Dispatchers.IO) {
        val ok = try {
            val request = Request.Builder().url(probeUrl).head().build()
            httpClient.newCall(request).execute().use { response ->
                response.code == 200
            }
        } catch (_: Exception) {
            false
        }
        updateState { it.copy(probeOk = ok) }
        ok
    }

    /** Synchronous snapshot — true only when connectivity is active AND the last probe succeeded. */
    fun isOnline(): Boolean = _state.value.online

    /** Reactive flow of the combined online flag — distinct consecutive values are deduped. */
    fun observeOnline(): Flow<Boolean> = _state.map { it.online }.distinctUntilChanged()

    /** Apply a transform to the state and recompute the combined `online` flag. */
    private fun updateState(transform: (OnlineState) -> OnlineState) {
        val current = _state.value
        val next = transform(current).copy(changedAt = System.currentTimeMillis())
        val combined = next.copy(online = next.connectivityActive && next.probeOk)
        _state.value = combined
    }

    /**
     * Snapshot of the detector's state.
     *
     * @property connectivityActive True when the OS reports an active network with INTERNET capability.
     * @property probeOk True when the last HEAD probe returned HTTP 200.
     * @property online Combined flag: `connectivityActive && probeOk`.
     * @property changedAt Wall-clock millis of the last state change.
     */
    data class OnlineState(
        val connectivityActive: Boolean,
        val probeOk: Boolean,
        val online: Boolean,
        val changedAt: Long,
    )

    private companion object {
        const val PROBE_TIMEOUT_SECONDS = 3L
        const val PROBE_INTERVAL_MS = 30_000L
    }
}
