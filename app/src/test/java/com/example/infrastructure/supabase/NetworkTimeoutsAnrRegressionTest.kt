package com.example.infrastructure.supabase

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * ANR regression suite — login-blocks fix, iter 4.
 *
 * Defect: `NetworkTimeouts.guard` ran its block on the CALLER's dispatcher.
 * Callers routinely launch from `viewModelScope` (Dispatchers.Main.immediate),
 * so the first guarded Supabase call of a process executed
 * `SupabaseClientProvider.build()` — Android Keystore `MasterKey` mint +
 * `EncryptedSharedPreferences` creation + the initial synchronous
 * SharedPreferences disk load — ON THE MAIN THREAD. None of that work has a
 * suspension point, so `withTimeout` could never interrupt it; a slow or
 * contended Keystore froze the UI at the exact moment the user submitted
 * login credentials, and the system ANR dialog followed ("the app blocks
 * when you enter a credential, then stops working").
 *
 * Fix under test (two layers):
 *  1. guard()/guardSyncPush() relocate the block to Dispatchers.IO;
 *  2. ElImtiyazApplication pre-warms the client on its IO scope at startup
 *     (verified by wiring, not unit-testable here — see AGENTS.md session log).
 */
@RunWith(RobolectricTestRunner::class)
class NetworkTimeoutsAnrRegressionTest {

    @Test
    fun `guard relocates block execution off the caller thread`(): Unit = runBlocking {
        val callerThreadName = Thread.currentThread().name

        // onlyIfConfigured = false → the block runs regardless of the
        // build-time Supabase configuration (unit tests see the .env values).
        val blockThreadName: String? = NetworkTimeouts.guard<String>(
            tag = "anr.threadProbe",
            onlyIfConfigured = false,
        ) {
            Thread.currentThread().name
        }

        // The block MUST NOT run on the caller's thread — the caller is the
        // runBlocking test thread standing in for the MAIN dispatcher.
        assertNotNull(blockThreadName)
        assertEquals(false, blockThreadName == callerThreadName)
    }

    @Test
    fun `guard still applies the hard timeout to hanging blocks`(): Unit = runBlocking {
        // A block that suspends forever must resolve to null within the
        // timeout, NOT hang the caller (the original spinner-forever symptom
        // when the backend was unreachable).
        val result: String? = NetworkTimeouts.guard<String>(
            tag = "anr.hangProbe",
            timeoutMs = 250L,
            onlyIfConfigured = false,
        ) {
            delay(10_000L) // would block for 10s if the timeout were dead
            "never-returned"
        }
        assertNull(result)
    }

    @Test
    fun `guard propagates a successful block result unchanged`(): Unit = runBlocking {
        val result: String? = NetworkTimeouts.guard<String>(
            tag = "anr.okProbe",
            onlyIfConfigured = false,
        ) {
            "ok"
        }
        assertEquals("ok", result)
    }
}
