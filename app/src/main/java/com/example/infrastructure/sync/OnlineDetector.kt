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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OnlineDetector — connectivity + HTTP-probe gate for the sync engine.
 *
 * T-050 (WEAK-009 / SEC-006) semantics:
 *  - **Fail-closed.** The initial state is OFFLINE; `isOnline()` returns the
 *    COMBINED state (connectivity AND last probe result); a failed/throwing
 *    probe means OFFLINE (the old catch-all-returns-true is gone).
 *  - **Probe target = OUR backend.** When `BuildConfig.SUPABASE_URL` is a real
 *    URL, the probe hits `<supabase-url>/auth/v1/health`. When it is blank or
 *    a placeholder (fresh checkout / demo mode) the detector NEVER probes —
 *    there is no third-party fallback host (the old fallback leaked the
 *    user's IP + app fingerprint every 30 seconds; that traffic now cannot
 *    happen).
 *  - **Captive-portal-proof.** The probe client does not follow redirects, so
 *    a portal's 302 login page stays a 302 (rejected). Only HTTP 200 (healthy)
 *    or 401 (reachable, unauthenticated) count as "the real backend answered".
 *  - **Throttled.** `onAvailable`/`onCapabilitiesChanged` storms are deduped by
 *    a minimum probe interval + an in-flight guard.
 */
@Singleton
class OnlineDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _state = MutableStateFlow(
        // FAIL-CLOSED initial state (WEAK-009 bug 1: was optimistic true).
        OnlineState(
            connectivityActive = false,
            probeOk = false,
            online = false,
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
            // A captive portal answers with a redirect to its login page —
            // following it would land on a 200 HTML page and read as "online".
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }

    /**
     * Probe endpoint, or null when the backend is not configured. Null means
     * "connectivity-only mode": no network probing at all (SEC-006).
     */
    internal val probeUrl: String? by lazy { resolveProbeUrl(BuildConfig.SUPABASE_URL) }

    /** Minimum spacing between probes (storms from ConnectivityManager callbacks). */
    private val lastProbeAtMs = AtomicLong(0)
    private val probeInFlight = AtomicBoolean(false)

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

        if (probeUrl != null) {
            scope.launch { probe() }
            probeJob = scope.launch {
                while (isActive) {
                    delay(PROBE_PERIOD_MS)
                    probe()
                }
            }
        } else {
            // Unconfigured (demo mode): connectivity-only — trust the
            // ConnectivityManager, never touch a third-party host, and mark
            // the probe signal satisfied so the combined state follows
            // connectivity alone.
            updateState { it.copy(probeOk = true) }
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

    /**
     * Runs one probe. Fail-closed: DNS failure / timeout / refused connection
     * all return false. Throttled unless [force].
     */
    suspend fun probe(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        val url = probeUrl
        if (url == null) {
            // Unconfigured: no network traffic (SEC-006); connectivity-only.
            updateState { it.copy(probeOk = true) }
            return@withContext true
        }
        val now = System.currentTimeMillis()
        if (!force &&
            (probeInFlight.get() || now - lastProbeAtMs.get() < PROBE_MIN_INTERVAL_MS)
        ) {
            return@withContext _state.value.probeOk
        }
        if (!probeInFlight.compareAndSet(false, true)) {
            return@withContext _state.value.probeOk
        }
        lastProbeAtMs.set(now)
        try {
            val ok = try {
                val request = Request.Builder().url(url).get().build()
                httpClient.newCall(request).execute().use { response ->
                    probeAccepts(response.code)
                }
            } catch (e: Exception) {
                // WEAK-009 bug 3: the old catch block returned TRUE here —
                // an unreachable network read as "online". Fail closed.
                Log.w("OnlineDetector", "Supabase probe failed: ${e.message}")
                false
            }
            updateState { it.copy(probeOk = ok) }
            ok
        } finally {
            probeInFlight.set(false)
        }
    }

    /** Combined online gate — connectivity AND probe (WEAK-009 bug 2). */
    fun isOnline(): Boolean = _state.value.online

    fun observeOnline(): Flow<Boolean> = _state.map { it.online }.distinctUntilChanged()

    private fun updateState(transform: (OnlineState) -> OnlineState) {
        val current = _state.value
        val next = transform(current).copy(changedAt = System.currentTimeMillis())
        // WEAK-009 bug 4: the old combine ignored probeOk entirely.
        val combined = next.copy(online = combineOnline(next.connectivityActive, next.probeOk))
        _state.value = combined
    }

    data class OnlineState(
        val connectivityActive: Boolean,
        val probeOk: Boolean,
        val online: Boolean,
        val changedAt: Long,
    )

    companion object {
        /** Periodic probe cadence (unchanged from the original design). */
        const val PROBE_PERIOD_MS: Long = 30_000L

        /** Minimum spacing between probes — throttles callback storms (SEC-006 battery note). */
        const val PROBE_MIN_INTERVAL_MS: Long = 10_000L

        /**
         * Probe verdict: 200 = healthy; 401 = reachable but unauthenticated.
         * Both prove the real Supabase auth service answered. Anything else
         * (redirect to a captive-portal login, 5xx, HTML page) is offline.
         */
        fun probeAccepts(httpCode: Int): Boolean = httpCode == 200 || httpCode == 401

        /** URL -> probe endpoint; null for blank/placeholder (unconfigured). */
        fun resolveProbeUrl(rawUrl: String): String? {
            val raw = rawUrl.trim().removeSurrounding("\"")
            if (!(raw.startsWith("http://") || raw.startsWith("https://"))) return null
            // Placeholder detection must survive the underscore variant
            // ("your_project" / "YOUR_PROJECT") — the hyphen-only check was
            // defeated by .env.example values (SEC-005/T-064 note).
            val normalized = raw.lowercase().replace('_', '-')
            val isPlaceholder = normalized.contains("your-project") ||
                normalized.contains("your-anon-key") ||
                normalized.contains("placeholder") ||
                normalized.contains("example")
            return if (!isPlaceholder) raw.removeSuffix("/") + "/auth/v1/health" else null
        }

        /** The combined gate: both signals must be positive. */
        fun combineOnline(connectivityActive: Boolean, probeOk: Boolean): Boolean =
            connectivityActive && probeOk
    }
}
