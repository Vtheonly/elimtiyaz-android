package com.example.infrastructure.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-021 / SYNC-106 + SYNC-107 regression suite — honest sync completion
 * semantics.
 *
 * SYNC-106: `SyncService.syncNow` was fire-and-forget — it launched the
 * drain on the service's internal scope and returned `Result.Ok(Unit)`
 * BEFORE the drain ran, so the UI ("Synchroniser" button, the post-config
 * auto-sync) reported success unconditionally. Fix: `syncNow` is a suspend
 * call that AWAITS `drainPending()` and returns an honest result; the
 * fire-and-forget scope is gone; the SettingsViewModel launches it in its
 * own viewModelScope.
 *
 * SYNC-107: `SyncWorker.doWork` wrapped the drain in runCatching and
 * returned `Result.success()` in EVERY branch — WorkManager could never
 * distinguish a clean drain from a crashed one or one with failures
 * outstanding. Fix: `DrainResult.remainingPending` (entries still pending
 * after the pass) lets the worker map:
 *   crash → retry() · permanent failures → failure() ·
 *   transient remainder → retry() · clean → success().
 *
 * The service/worker classes need an Android Context + real network for
 * end-to-end exercise, which unit tests deliberately do not fake — the
 * wiring is pinned with source-scan tests (SyncErrorSurfacingTest pattern).
 */
class SyncCompletionT021Test {

    @Test
    fun `syncNow is suspend, awaits drainPending, and returns an honest result`() {
        val src = readMainSource("infrastructure/sync/SyncService.kt")
        // suspend + block body — the T-050 no-double-pull scan keeps matching.
        val block = Regex("suspend fun syncNow\\(\\): Result<Unit> \\{[\\s\\S]*?\\n    \\}").find(src)?.value
            ?: error("suspend syncNow not found")
        assertTrue("syncNow must AWAIT drainPending", block.contains("drainPending()"))
        assertTrue("syncNow must surface the failure (Err path)", block.contains("Result.Err("))
        // No fire-and-forget launch, no internal scope.
        assertFalse("syncNow must not launch-and-forget", block.contains("scope.launch"))
        assertFalse(
            "the service's internal fire-and-forget scope must be removed",
            src.contains("CoroutineScope(SupervisorJob()"),
        )
    }

    @Test
    fun `drainPending reports remaining pending entries for the worker`() {
        val src = readMainSource("infrastructure/sync/SyncService.kt")
        assertTrue(
            "drainPending must count entries still pending after the pass (SYNC-107)",
            src.contains("listPending().size"),
        )
        val result = readMainSource("infrastructure/sync/DrainResult.kt")
        assertTrue(
            "DrainResult must carry remainingPending",
            result.contains("val remainingPending: Int = 0"),
        )
    }

    @Test
    fun `SyncWorker maps drain outcomes to retry - failure - success`() {
        val src = readMainSource("infrastructure/sync/SyncWorker.kt")
        val block = Regex("override suspend fun doWork\\(\\): Result \\{[\\s\\S]*?\\n    \\}").find(src)?.value
            ?: error("doWork not found")
        assertTrue("drain crash → retry", block.contains("outcome.isFailure -> Result.retry()"))
        assertTrue("permanent failures → failure()", block.contains("drain.failed > 0 -> Result.failure()"))
        assertTrue("transient remainder → retry()", block.contains("drain.remainingPending > 0 -> Result.retry()"))
        assertTrue("clean drain → success()", block.contains("else -> Result.success()"))
        assertFalse(
            "the old runCatching-then-success-always pattern must be gone",
            block.contains("runCatching { syncService.drainPending() }\n        return Result.success()"),
        )
    }

    @Test
    fun `SettingsViewModel launches the now-suspend syncNow in viewModelScope`() {
        val src = readMainSource("ui/features/settings/SettingsViewModel.kt")
        assertTrue(
            "SettingsViewModel.syncNow must launch the suspend call",
            src.contains("fun syncNow() = viewModelScope.launch { syncService.syncNow() }"),
        )
        assertTrue(
            "saveSupabaseConfig must launch the suspend syncNow too",
            src.contains("viewModelScope.launch { syncService.syncNow() }"),
        )
    }

    private fun readMainSource(relative: String): String =
        File("src/main/java/com/example/$relative").readText()
}
