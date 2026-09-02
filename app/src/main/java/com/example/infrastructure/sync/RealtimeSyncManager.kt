package com.example.infrastructure.sync

import android.util.Log
import com.example.core.Result
import com.example.session.SessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A postgres-change event delivered by [RealtimeEventSource] for one table.
 *
 * The payload is deliberately minimal: this manager does not read rows —
 * it TRIGGERS the existing granular pulls (the Android equivalent of the
 * website's "invalidate the TanStack query, refetch"). Row-level detail
 * would only invite client-side re-implementation of server logic.
 */
data class TableChanged(val table: String)

/**
 * The SDK-facing seam for realtime table events. Single production
 * implementation: [com.example.infrastructure.supabase.SupabaseRealtimeEventSource].
 *
 * This interface exists so the manager's logic (table routing, debouncing,
 * session gating) is unit-testable without websockets — NOT as a second
 * realtime implementation. Do not add another production implementation.
 */
interface RealtimeEventSource {
    /** True when the underlying client has a real URL + key configured. */
    val isConfigured: Boolean

    /**
     * Cold flow of change events for [table] (INSERT/UPDATE/DELETE, event *).
     * Collecting joins the channel; cancelling removes it. Delivered events
     * are scoped by the caller's RLS SELECT policies — exactly the rows the
     * signed-in user may see (same contract as the website's hooks).
     */
    fun changes(table: String): kotlinx.coroutines.flow.Flow<TableChanged>
}

/**
 * The pull surface realtime events trigger. Single production
 * implementation: [PullSyncRepository] (the granular pull methods it already
 * exposes). Declared as an interface for the same testability reason as
 * [RealtimeEventSource] — one impl, no parallel implementation.
 */
interface RealtimePullTarget {
    suspend fun pullPayments(sinceIso: String? = null): Result<Int>
    suspend fun pullInstallments(): Result<Int>
    suspend fun pullNotifications(): Result<Int>
    suspend fun pullHomework(): Result<Int>
}

/** Online gate — provided from [OnlineDetector] in production. */
fun interface OnlineGate {
    fun isOnline(): Boolean
}

/**
 * RealtimeSyncManager — T-069 / REALTIME-104.
 *
 * THE FRESHNESS BACKBONE: subscribes Android to Supabase Realtime
 * postgres-changes on the canonical tables so server-side writes (a payment
 * recorded on the desktop, a notification broadcast, homework pushed by a
 * teacher) reach this device within seconds instead of waiting for the
 * 15-minute [SyncWorker] cycle. The periodic cycle REMAINS as the fallback
 * (a dead websocket, background kills, or a missed event still converge
 * within 15 minutes).
 *
 * Mirrors the website's hooks (`use-realtime.ts`, T-032) table for table:
 *   payments, installments, notifications, homework.
 *
 * DEVIATION from the task text (recorded in the task registry, 20th
 * session): `chat_messages` is NOT subscribed yet — Android has no chat
 * read-side (T-102-follow-up, deferred). Subscribing with no consumer would
 * be dead traffic: the events would trigger pulls of tables whose rows did
 * not change. The routing map below makes adding chat a one-line change
 * when T-102 lands.
 *
 * Event → pull routing (the Android equivalent of the website's
 * invalidation keys):
 *   payments       → pullPayments
 *   installments   → pullInstallments + pullPayments  (the website's
 *                    useFinancialRealtime cross-invalidates both — an
 *                    installment change can reallocate payments through the
 *                    waterfall, so both refresh)
 *   notifications  → pullNotifications
 *   homework       → pullHomework
 *
 * Lifecycle: `start()` observes [SessionManager.state]; subscriptions
 * activate when a session appears and deactivate when it disappears — the
 * same reactive pattern the Application class uses for FCM topics. A
 * debounced trigger collapses event bursts (one pull per burst per table
 * instead of one per row).
 */
@Singleton
class RealtimeSyncManager @Inject constructor(
    private val eventSource: RealtimeEventSource,
    private val sessionManager: SessionManager,
    private val pulls: RealtimePullTarget,
    private val onlineGate: OnlineGate,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var started = false

    private var sessionJob: Job? = null
    private val tableJobs = mutableMapOf<String, Job>()

    /**
     * Table → pull actions to run (debounced) when a change event arrives.
     *
     * T-102-follow-up (21st session): `chat_channels` + `chat_messages`
     * are SUBSCRIBED with empty pull lists — chat is online-only in v1
     * (no Room cache, deliberate scope decision recorded in the task
     * entry), so a chat event triggers NO store pull; instead the raw
     * debounced event is emitted on [tableEvents] and the chat
     * ViewModels refresh their in-memory state directly. This is the
     * "one-line routing entry" the 20th session predicted, plus the event
     * tap that makes it useful without a Room layer.
     */
    private val routing: Map<String, List<suspend () -> Result<Int>>> = mapOf(
        "payments" to listOf<suspend () -> Result<Int>> { pulls.pullPayments() },
        "installments" to listOf(
            suspend { pulls.pullInstallments() },
            suspend { pulls.pullPayments() },
        ),
        "notifications" to listOf<suspend () -> Result<Int>> { pulls.pullNotifications() },
        "homework" to listOf<suspend () -> Result<Int>> { pulls.pullHomework() },
        "chat_channels" to emptyList(),
        "chat_messages" to emptyList(),
    )

    /**
     * T-102-follow-up — raw (debounced) table-change events, for screens
     * that keep their OWN state instead of the Room store (chat v1).
     * Emits the table name; collect `tableEvents` and filter for the
     * tables you care about. Replay-free: subscribers only see events
     * that arrive AFTER subscription (a screen refreshes once on open,
     * then reacts to events).
     */
    private val _tableEvents = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val tableEvents: SharedFlow<String> = _tableEvents

    /**
     * Debounce window (ms) — collapses a burst of events into one pull pass
     * per table. Package-visible so tests can shorten it.
     */
    @Volatile
    internal var debounceMs: Long = DEFAULT_DEBOUNCE_MS

    /**
     * Begin observing the session. Idempotent — call once from the
     * Application's onCreate (the same place FCM topic observation starts).
     */
    fun start() {
        if (started) return
        started = true
        sessionJob = scope.launch {
            sessionManager.state
                .map { it != null }
                .distinctUntilChanged()
                .collect { signedIn ->
                    if (signedIn) activate() else deactivate()
                }
        }
    }

    /** Stop everything (symmetry for tests and process teardown). */
    fun stop() {
        started = false
        deactivate()
        sessionJob?.cancel()
        sessionJob = null
    }

    /** True when the manager currently holds live table subscriptions. */
    val activeTables: Set<String>
        get() = synchronized(tableJobs) { tableJobs.keys.toSet() }

    private fun activate() {
        if (!eventSource.isConfigured) {
            Log.i(TAG, "Supabase not configured — realtime subscriptions skipped")
            return
        }
        for (table in routing.keys) {
            synchronized(tableJobs) {
                if (tableJobs.containsKey(table)) return@synchronized
                tableJobs[table] = scope.launch {
                    // The debounce window is read at chain-build time
                    // (subscription activation) — tests set [debounceMs]
                    // BEFORE the session appears.
                    eventSource.changes(table)
                        .debounce(this@RealtimeSyncManager.debounceMs)
                        .collect {
                            _tableEvents.tryEmit(it.table)
                            triggerPulls(it.table)
                        }
                }
            }
        }
        Log.i(TAG, "Realtime subscriptions active: ${routing.keys}")
    }

    private fun deactivate() {
        val jobs: List<Job>
        synchronized(tableJobs) {
            jobs = tableJobs.values.toList()
            tableJobs.clear()
        }
        jobs.forEach { it.cancel() }
        if (jobs.isNotEmpty()) Log.i(TAG, "Realtime subscriptions deactivated (${jobs.size} channels)")
    }

    /**
     * Run the routed pulls for [table]. Fail-closed on the online gate (the
     * 15-min fallback cycle converges missed windows); pull failures are
     * logged, never thrown — a failed pull must not kill the subscription
     * (the next event retries, then the periodic cycle converges).
     */
    private suspend fun triggerPulls(table: String) {
        val actions = routing[table] ?: return
        if (!onlineGate.isOnline()) {
            Log.i(TAG, "Realtime event for '$table' while offline — skipping pull (periodic sync converges)")
            return
        }
        for (action in actions) {
            runCatching { action() }
                .onFailure { e ->
                    Log.w(TAG, "Realtime-triggered pull for '$table' failed: ${e.message} (periodic sync converges)")
                }
        }
    }

    companion object {
        private const val TAG = "RealtimeSyncManager"

        /** 2 seconds: enough to collapse a waterfall replay burst, short enough to feel instant. */
        const val DEFAULT_DEBOUNCE_MS = 2_000L
    }
}
