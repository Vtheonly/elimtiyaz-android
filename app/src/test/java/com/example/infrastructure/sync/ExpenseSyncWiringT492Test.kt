package com.example.infrastructure.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-492 (SYNC-302) — the expense pipeline's WIRING pins (the source-scan
 * convention: TenantStampingT051Test / the T-020 RestException wiring pin).
 *
 * The pipeline is three seams that only compose when each side is wired:
 *  1. the WRITE seam — LocalExpenseRepository must enqueue an "expense"
 *     queue entry after EVERY successful Room write (submit/approve/reject/
 *     disburse/settleProof — previously the writes were local-only and the
 *     queue never saw them);
 *  2. the DISPATCH seam — SyncQueueDispatcher must route "expense" to
 *     pushExpense (previously a documented no-op else-branch);
 *  3. the READ seam — PullSyncRepository must pull expense_tickets in
 *     doPullAll, paginated, with the category embed.
 *
 * Plus the §15.86 guard: the v19→v20 MigrationTestHelper suite MUST appear
 * in the testReleaseUnitTest exclusion list (the TEST-504 lesson — the 5th
 * ARCH-012 recurrence class this file's own commit must not repeat).
 */
class ExpenseSyncWiringT492Test {

    private fun repoFile(relative: String): File {
        val f = File("src/main/java/com/example/$relative")
        assertTrue("missing source file: $f", f.exists())
        return f
    }

    // ─── 1. the write seam ───────────────────────────────────────────────

    @Test
    fun `the expense repository enqueues the sync push after every write`() {
        val src = repoFile("infrastructure/local/LocalExpenseRepository.kt").readText()
        // The seam is injected.
        assertTrue(src.contains("syncSupport: com.example.infrastructure.sync.SyncSupport? = null"))
        // Every write method enqueues (the helper carries the entity kind).
        assertTrue(src.contains("enqueueExpense(entity, \"create\""))
        assertEquals(
            "approve/reject/disburse/settleProof must each enqueue",
            4,
            Regex("enqueueExpense\\(updated, \"update\"").findAll(src).count(),
        )
        // The ticket number is the DESKTOP convention — never the count+1
        // sequence (the §5 identity rule; the old code's countPending()+1).
        assertTrue(src.contains("generateTicketNumber()"))
        assertTrue(!src.contains("countPending() + 1"))
    }

    @Test
    fun `the enqueue payload carries the full row the push needs`() {
        val src = repoFile("infrastructure/local/LocalExpenseRepository.kt").readText()
        for (field in listOf(
            "\"id\"", "\"requestCode\"", "\"title\"", "\"description\"", "\"amount\"",
            "\"category\"", "\"payee\"", "\"status\"", "\"submittedBy\"", "\"approvedBy\"",
            "\"disbursedAt\"", "\"settledAt\"", "\"proofUrl\"", "\"urgency\"", "\"notes\"",
            "\"finalSpentAmount\"", "\"actorId\"",
        )) {
            assertTrue("the payload must carry $field", src.contains("put($field,"))
        }
    }

    // ─── 2. the dispatch seam ────────────────────────────────────────────

    @Test
    fun `the dispatcher routes expense to pushExpense with the desktop translation layer`() {
        val src = repoFile("infrastructure/sync/SyncQueueDispatcher.kt").readText()
        assertTrue(src.contains("\"expense\" -> pushExpense(entry, payload)"))
        // The translation layer is the SHARED mapper (never re-implemented).
        assertTrue(src.contains("expenseStatusToDb"))
        assertTrue(src.contains("expenseCategoryToDb"))
        // The idempotent upsert on the canonical table + the id-prefix strip
        // (the homework convention).
        assertTrue(src.contains("from(\"expense_tickets\").upsert(row)"))
        assertTrue(src.contains("removePrefix(\"exp-\")"))
        // The NOT NULL justification (the desktop's header note 4).
        assertTrue(src.contains("put(\"justification\""))
        // The transition push writes ONLY the workflow columns (the desktop's
        // transition() shape — an Android approval must never rewrite the
        // originator's title/category/amount).
        val transitionBlock = src.substringAfter("The transition-only UPDATE").substringBefore("// ── Per-entity")
        assertTrue(transitionBlock.contains("set(\"status\""))
        assertTrue(!transitionBlock.contains("set(\"title\""))
        assertTrue(!transitionBlock.contains("set(\"category_id\""))
        assertTrue(!transitionBlock.contains("set(\"requested_amount\""))
    }

    // ─── 3. the read seam ────────────────────────────────────────────────

    @Test
    fun `the pull layer drains expense_tickets with the category embed into the pull cycle`() {
        val src = repoFile("infrastructure/sync/PullSyncRepository.kt").readText()
        // The pull exists, is paginated (the T-493 drain), and embeds the
        // category code (the table stores the category UUID).
        assertTrue(src.contains("suspend fun pullExpenses()"))
        assertTrue(src.contains("Columns.raw(\"*, expense_categories(code)\")"))
        // It is wired into the pull cycle.
        assertTrue(src.contains("pullExpenses() as? Result.Ok"))
    }

    // ─── 4. the §15.86 guard (the TEST-504 lesson) ───────────────────────

    @Test
    fun `the v20 migration suite appears in the release exclusion list`() {
        val gradle = File("build.gradle.kts")
        assertTrue("missing app/build.gradle.kts", gradle.exists())
        val src = gradle.readText()
        assertTrue(
            "RoomSchemaUpgradeT492Test MUST appear in the testReleaseUnitTest exclusion list " +
                "(the §15.86 same-commit rule — the TEST-504 5th-recurrence class)",
            src.contains("excludeTestsMatching(\"com.example.infrastructure.room.RoomSchemaUpgradeT492Test\")"),
        )
    }
}
