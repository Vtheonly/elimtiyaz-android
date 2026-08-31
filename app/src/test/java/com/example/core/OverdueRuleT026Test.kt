package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

/**
 * T-026 regression suite — the canonical overdue rule (INV-4) on Android.
 *
 * WEAK-007: `computeParentSummary` requires the caller to build + pass the
 * `overdueCategoryDueDates` map (desktop debt-ops.ts:43-44 pattern); every
 * Android production call site omitted it → `totalOverdue` was PERMANENTLY 0
 * ("Créances en Retard" KPI, debt dashboard, parent profile all showed 0).
 *
 * BUSINESS-007: `maxDaysOverdueFromLedger` returned the age of the OLDEST
 * charge — a charge created today for next year's tuition read as "~365
 * days overdue" even though nothing was due. The canonical rule: an account
 * is overdue iff balance > 0 AND its due date (latest charge `at`) is past;
 * days overdue = now - dueDate for OVERDUE accounts only.
 *
 * DRIFT-006: desktop `computeParentSummary` (INV-10) is the single source
 * of truth — the threshold (`> 0L` centimes ≡ desktop `> 0.001 DZD`) and
 * the due-date semantics must match it exactly.
 */
class OverdueRuleT026Test {

    private val now: Instant = Instant.parse("2026-10-01T00:00:00Z")
    private val tenant = "t1"

    private fun charge(
        parentId: String,
        amount: Long,
        at: Instant,
        studentId: String? = null,
    ) = createChargeEntry(
        tenantId = tenant, parentId = parentId, studentId = studentId,
        category = PaymentCategory.TUITION, amount = amount,
        sourceType = LedgerSourceType.MANUAL_ENTRY, sourceId = "src-1",
        actorId = "staff-1", actorName = "Staff", description = "Tranche",
        at = at,
    )

    private fun payment(
        parentId: String,
        amount: Long,
        at: Instant,
    ) = createPaymentEntry(
        tenantId = tenant, parentId = parentId, studentId = null,
        category = PaymentCategory.TUITION, amount = amount,
        method = PaymentMethod.CASH, receiptNumber = "R-1",
        paymentStatus = PaymentStatus.PAID,
        sourceId = "src-2", actorId = "staff-1", actorName = "Staff",
        description = "Paiement", at = at,
    )

    // ── WEAK-007: the map is what makes totalOverdue non-zero ───────────

    @Test
    fun `without the due-date map totalOverdue is 0 - the original defect`() {
        val entries = listOf(charge("p1", 50_000L, now.minusSeconds(40L * 86_400)))
        // This is the OLD call shape (default empty map) — pins WHY the map
        // must be passed at every call site.
        val withoutMap = LedgerEngine.computeParentSummary(entries, "p1", "P", now = now)
        assertEquals(0L, withoutMap.totalOverdue)
    }

    @Test
    fun `with the map a past-due unpaid balance is overdue`() {
        val entries = listOf(charge("p1", 50_000L, now.minusSeconds(40L * 86_400)))
        val map = LedgerEngine.buildOverdueDueDateMap(entries)
        val summary = LedgerEngine.computeParentSummary(entries, "p1", "P", map, now)
        assertEquals(50_000L, summary.totalOverdue)
    }

    @Test
    fun `future-dated charge (next year's tuition) is NOT overdue`() {
        val entries = listOf(charge("p1", 50_000L, now.plusSeconds(300L * 86_400)))
        val map = LedgerEngine.buildOverdueDueDateMap(entries)
        val summary = LedgerEngine.computeParentSummary(entries, "p1", "P", map, now)
        assertEquals(0L, summary.totalOverdue)
    }

    @Test
    fun `a settled account is never overdue even when the due date passed`() {
        val entries = listOf(
            charge("p1", 50_000L, now.minusSeconds(40L * 86_400)),
            payment("p1", 50_000L, now.minusSeconds(10L * 86_400)),
        )
        val map = LedgerEngine.buildOverdueDueDateMap(entries)
        val summary = LedgerEngine.computeParentSummary(entries, "p1", "P", map, now)
        assertEquals(0L, summary.totalOverdue)
    }

    // ── BUSINESS-007: days-overdue from the DUE DATE ────────────────────

    @Test
    fun `days overdue counts from the due date, not the charge creation age`() {
        // Charge created 40 days ago (its due date = latest charge `at`).
        val entries = listOf(charge("p1", 50_000L, now.minusSeconds(40L * 86_400)))
        assertEquals(40L, LedgerEngine.maxDaysOverdueFromLedger(entries, now))
    }

    @Test
    fun `a charge created today for next year reads 0 days overdue (the original lie)`() {
        // Old implementation: age of oldest charge ≈ 365 days. Canonical: the
        // due date (latest charge at = today) has not passed → 0.
        val entries = listOf(charge("p1", 50_000L, now))
        assertEquals(0L, LedgerEngine.maxDaysOverdueFromLedger(entries, now))
    }

    @Test
    fun `fully paid account reads 0 days overdue`() {
        val entries = listOf(
            charge("p1", 50_000L, now.minusSeconds(40L * 86_400)),
            payment("p1", 50_000L, now.minusSeconds(5L * 86_400)),
        )
        assertEquals(0L, LedgerEngine.maxDaysOverdueFromLedger(entries, now))
    }

    @Test
    fun `max across accounts, oldest overdue account wins`() {
        val recent = charge("p1", 10_000L, now.minusSeconds(10L * 86_400))
        val old = charge("p1", 20_000L, now.minusSeconds(60L * 86_400), studentId = "s2")
        assertEquals(60L, LedgerEngine.maxDaysOverdueFromLedger(listOf(recent, old), now))
    }

    // ── DRIFT-006: consistency between the two overdue surfaces ─────────

    @Test
    fun `totalOverdue and maxDaysOverdueFromLedger agree on overdue-ness (INV-4 consistency)`() {
        val overdueEntries = listOf(charge("p1", 50_000L, now.minusSeconds(40L * 86_400)))
        val overdueMap = LedgerEngine.buildOverdueDueDateMap(overdueEntries)
        val notDueEntries = listOf(charge("p1", 50_000L, now.plusSeconds(300L * 86_400)))
        val notDueMap = LedgerEngine.buildOverdueDueDateMap(notDueEntries)

        assertTrue(
            LedgerEngine.computeParentSummary(overdueEntries, "p1", "P", overdueMap, now).totalOverdue > 0L,
        )
        assertTrue(LedgerEngine.maxDaysOverdueFromLedger(overdueEntries, now) > 0L)

        assertEquals(
            0L,
            LedgerEngine.computeParentSummary(notDueEntries, "p1", "P", notDueMap, now).totalOverdue,
        )
        assertEquals(0L, LedgerEngine.maxDaysOverdueFromLedger(notDueEntries, now))
    }

    // ── source-scan pins: every production call site passes the map ─────

    @Test
    fun `all production computeParentSummary call sites build and pass the due-date map`() {
        val src = readMainSource("infrastructure/local/LocalRepositories2.kt")
        // Every call that passes totalOverdue semantics must carry the map.
        val callsWithMap = Regex("computeParentSummary\\([^)]*dueDateMap").findAll(src).count()
        assertTrue(
            "expected the debt-dashboard/profile/KPI call sites to pass dueDateMap (found $callsWithMap)",
            callsWithMap >= 5,
        )
        // And no production call site may use the empty default anymore.
        val bareCalls = Regex("computeParentSummary\\((?:parentEntries|domainEntries),\\s*[^,]+,\\s*\"[^\"]*\"\\)").findAll(src).count()
        assertEquals("no call site may rely on the empty-map default anymore", 0, bareCalls)
    }

    private fun readMainSource(relativeUnderSrcMainJava: String): String {
        val relative = "src/main/java/com/example/$relativeUnderSrcMainJava"
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val inModule = File(cwd, relative)
        if (inModule.isFile) return inModule.readText()
        val inRepoRoot = File(cwd.parentFile ?: cwd, relative)
        if (inRepoRoot.isFile) return inRepoRoot.readText()
        error("Source file not found: $relative")
    }
}
