package com.example.infrastructure.sync

import com.example.core.Result
import com.example.core.Role
import com.example.core.Session
import com.example.domain.repository.AuthRepository
import com.example.session.SessionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * T-069 / REALTIME-104 — Android realtime subscriptions.
 *
 * The defect: the Android had ZERO Supabase realtime subscriptions — the
 * Realtime plugin was installed in SupabaseClientProvider but never used.
 * Freshness relied entirely on the 15-minute pullAll cycle (and manual
 * pull-to-refresh); a payment recorded on the desktop reached the Android
 * UI up to 15 minutes late.
 *
 * Fix under test (RealtimeSyncManager + SupabaseRealtimeEventSource):
 *  1. subscriptions activate reactively when a session appears and
 *     deactivate when it disappears (the FCM-topic pattern);
 *  2. events route to the GRANULAR pulls (payments/installments/
 *     notifications/homework) with the website's cross-invalidation
 *     semantics (an installment change also refreshes payments);
 *  3. bursts debounce to one pull pass per table;
 *  4. the online gate is fail-closed (offline events skip pulls — the
 *     periodic cycle converges);
 *  5. an unconfigured Supabase client means NO subscriptions (SEC-005
 *     posture: never touch a real host unconfigured);
 *  6. production wiring: the Application starts the manager and the
 *     SDK source subscribes with NO column filter (RLS scopes events —
 *     the REALTIME-102 lesson).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RealtimeSyncT069Test {

    // ── Fakes ────────────────────────────────────────────────────────────────

    private class FakeEventSource(override val isConfigured: Boolean = true) : RealtimeEventSource {
        val flows = mutableMapOf<String, MutableSharedFlow<TableChanged>>()
        val subscribed = mutableListOf<String>()

        override fun changes(table: String): Flow<TableChanged> =
            flows.getOrPut(table) { MutableSharedFlow(extraBufferCapacity = 256) }
                .onSubscription { subscribed.add(table) }

        suspend fun emit(table: String) {
            flows.getValue(table).emit(TableChanged(table))
        }
    }

    private class RecordingPulls : RealtimePullTarget {
        val payments = AtomicInteger()
        val installments = AtomicInteger()
        val notifications = AtomicInteger()
        val homework = AtomicInteger()

        override suspend fun pullPayments(sinceIso: String?): Result<Int> {
            payments.incrementAndGet(); return Result.Ok(1)
        }

        override suspend fun pullInstallments(): Result<Int> {
            installments.incrementAndGet(); return Result.Ok(1)
        }

        override suspend fun pullNotifications(): Result<Int> {
            notifications.incrementAndGet(); return Result.Ok(1)
        }

        override suspend fun pullHomework(): Result<Int> {
            homework.incrementAndGet(); return Result.Ok(1)
        }
    }

    private class NoopAuthRepository : AuthRepository {
        override suspend fun signIn(email: String, password: String): Result<Session> =
            Result.Err(com.example.core.AppError("test", "noop", "noop"))
        override suspend fun signOut(): Result<Unit> = Result.Ok(Unit)
        override suspend fun refreshSession(): Result<Session?> = Result.Ok(null)
        override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = Result.Ok(Unit)
        override fun observeSession(): Flow<Session?> = MutableSharedFlow()
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private lateinit var source: FakeEventSource
    private lateinit var pulls: RecordingPulls
    private lateinit var sessionManager: SessionManager
    private var online: Boolean = true
    private lateinit var manager: RealtimeSyncManager

    @Before
    fun setUp() {
        source = FakeEventSource()
        pulls = RecordingPulls()
        sessionManager = SessionManager(NoopAuthRepository())
        manager = RealtimeSyncManager(
            eventSource = source,
            sessionManager = sessionManager,
            pulls = pulls,
            onlineGate = OnlineGate { online },
        )
        // Short debounce so tests run fast; the production default is 2s.
        manager.debounceMs = 100
    }

    private fun signIn() {
        sessionManager.setSession(
            Session(
                userId = "u1", tenantId = "t1", email = "e", displayName = "d", avatarUrl = null,
                role = Role.TEACHER, permissions = emptySet(), accessToken = "jwt",
                refreshToken = null, expiresAt = Long.MAX_VALUE, locale = "fr",
            ),
        )
    }

    /** Poll until [check] holds (default 2s) so timing never flakes the suite. */
    private fun awaitUntil(timeoutMs: Long = 2000, check: () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!check() && System.currentTimeMillis() - start < timeoutMs) {
            Thread.sleep(20)
        }
    }

    private fun waitForSubscriptions() {
        awaitUntil { source.subscribed.size >= 4 }
    }

    // ── 1. Reactive lifecycle ────────────────────────────────────────────────

    @Test
    fun `sign-in activates subscriptions on the four canonical tables`() {
        manager.start()
        signIn()
        waitForSubscriptions()

        assertEquals(
            "The manager subscribes to exactly the website's table set (chat_messages lands with T-102)",
            setOf("payments", "installments", "notifications", "homework"),
            manager.activeTables,
        )
    }

    @Test
    fun `sign-out deactivates all subscriptions`() {
        manager.start()
        signIn()
        waitForSubscriptions()
        assertTrue(manager.activeTables.isNotEmpty())

        sessionManager.setSession(null)
        awaitUntil { manager.activeTables.isEmpty() }
        assertEquals(emptySet<String>(), manager.activeTables)
    }

    @Test
    fun `start is idempotent - repeated calls never duplicate channels`() {
        manager.start()
        manager.start()
        signIn()
        waitForSubscriptions()

        assertEquals(4, source.subscribed.size)
    }

    @Test
    fun `unconfigured Supabase means NO subscriptions`() {
        val unconfigured = FakeEventSource(isConfigured = false)
        val manager = RealtimeSyncManager(unconfigured, sessionManager, pulls, OnlineGate { true })
        manager.start()
        signIn()
        // Give the (correctly absent) activation a moment to prove a negative.
        Thread.sleep(200)
        assertEquals(emptySet<String>(), manager.activeTables)
        assertTrue(source.subscribed.isEmpty())
    }

    // ── 2. Event routing + debounce ──────────────────────────────────────────

    @Test
    fun `a burst of payment events debounces to ONE pull`() {
        manager.start()
        signIn()
        waitForSubscriptions()

        runBlocking {
            repeat(3) { source.emit("payments") }
        }
        awaitUntil { pulls.payments.get() >= 1 }
        Thread.sleep(300) // let any (wrong) extra debounced passes fire
        assertEquals("3 events in one burst = 1 pull", 1, pulls.payments.get())
    }

    @Test
    fun `an installment event refreshes installments AND payments (website cross-invalidation)`() {
        manager.start()
        signIn()
        waitForSubscriptions()

        runBlocking { source.emit("installments") }
        awaitUntil { pulls.installments.get() >= 1 }
        awaitUntil { pulls.payments.get() >= 1 }
        assertEquals(1, pulls.installments.get())
        assertEquals("the waterfall can move payments when installments change", 1, pulls.payments.get())
    }

    @Test
    fun `notification and homework events route to their own pulls`() {
        manager.start()
        signIn()
        waitForSubscriptions()

        runBlocking {
            source.emit("notifications")
            source.emit("homework")
        }
        awaitUntil { pulls.notifications.get() >= 1 }
        awaitUntil { pulls.homework.get() >= 1 }
        assertEquals(1, pulls.notifications.get())
        assertEquals(1, pulls.homework.get())
        assertEquals("no cross-table pull from these tables", 0, pulls.payments.get())
    }

    // ── 3. Fail-closed online gate ───────────────────────────────────────────

    @Test
    fun `offline events skip pulls - the periodic cycle converges`() {
        online = false
        manager.start()
        signIn()
        waitForSubscriptions()

        runBlocking { source.emit("payments") }
        Thread.sleep(400)
        assertEquals(0, pulls.payments.get())
    }

    // ── 4. Production wiring (source-scan pins, like T-039's style) ─────────

    @Test
    fun `the Application starts the manager`() {
        val src = File("src/main/java/com/example/ElImtiyazApplication.kt").readText()
        assertTrue(src.contains("realtimeSyncManager.start()"))
    }

    @Test
    fun `the SDK source subscribes with NO column filter - RLS scopes events`() {
        val src = File("src/main/java/com/example/infrastructure/supabase/SupabaseRealtimeEventSource.kt").readText()
        // The REALTIME-102 lesson: a narrow filter (e.g. target_user_id=eq.…)
        // silently drops role-broadcast rows. The subscription must stay
        // filter-free; RLS delivers exactly the caller-visible rows.
        assertTrue(src.contains("postgresChangeFlow<PostgresAction>"))
        assertTrue(!src.contains("filter ="))
        assertTrue(!src.contains("filter="))
        // The plugin carries the session JWT to the socket via
        // disconnectOnSessionLoss — no manual setAuth in the source.
        assertTrue(!src.contains("setAuth"))
    }

    @Test
    fun `the periodic 15-minute fallback cycle is preserved`() {
        val worker = File("src/main/java/com/example/infrastructure/sync/SyncWorker.kt").readText()
        val scheduler = File("src/main/java/com/example/infrastructure/sync/SyncScheduler.kt").readText()
        assertTrue(worker.contains("syncService.drainPending()"))
        assertTrue(scheduler.contains("15, TimeUnit.MINUTES"))
    }
}
