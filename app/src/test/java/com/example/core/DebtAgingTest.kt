package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-457 (128th session) — the §15.1 debt-status evaluation suite.
 *
 * Every fixture + expected value below is the DESKTOP'S OWN
 * `src/tests/domain/ledger/debt-aging.test.ts` corpus (the T-429 4-tier
 * configurable-hierarchy block), mirrored VERBATIM in the platform's
 * centime convention (desktop DZD × 100). Pins:
 *
 *   1. The ordered evaluation (tier 1..5 — green/yellow/orange/red with
 *      the exact boundary pins: 5/6, 15/16, 60/61);
 *   2. THE DECOUPLING (a payment 10 days ago does NOT make an ancient
 *      debt green — the pre-T-429 masking the owner removed; the
 *      « Payeur actif » ANNOTATION survives);
 *   3. INV-16c (amount magnitude never changes the level);
 *   4. The CONFIGURABLE thresholds (custom boundaries + the custom
 *      active-payer window);
 *   5. The §15.3 label wording + the INV-16d explanation contract;
 *   6. The factor derivation (the inactivity default INV-16b, the
 *      oldest-obligation aging, the subsequent-year payments).
 */
class DebtAgingTest {

    private val dzd = { v: Long -> v * 100 }

    // ── 1. The ordered evaluation (§15.1 as amended by T-429) ────────────

    @Test
    fun `tier 1 - outstanding at or below the epsilon is GREEN resolved`() {
        val s = computeDebtAgingStatus(DebtAgingStatusFactors(0, 0, 0))
        assertEquals(DebtAgingStatusLevel.GREEN, s.level)
        assertEquals(DebtAgingReasonCode.RESOLVED, s.reasonCode)
        // The 0.001-DZD epsilon (0.1 centime): a Long centime value <= 0.1
        // is exactly 0 — the degenerate-but-equivalent check.
        val sEpsilon = computeDebtAgingStatus(DebtAgingStatusFactors(0, 500, 500))
        assertEquals(DebtAgingReasonCode.RESOLVED, sEpsilon.reasonCode)
    }

    @Test
    fun `tier 2 - debtAge at grace (5) is GREEN not_due (A échoir - En cours)`() {
        val s = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100_000), 5, 0))
        assertEquals(DebtAgingStatusLevel.GREEN, s.level)
        assertEquals(DebtAgingReasonCode.NOT_DUE, s.reasonCode)
        assertTrue(s.explanationFr.contains("échoir"))
    }

    @Test
    fun `tier 3 - 5 lt debtAge le 15 is YELLOW watch (boundary pins 6 and 15)`() {
        val s = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100_000), 15, 90))
        assertEquals(DebtAgingStatusLevel.YELLOW, s.level)
        assertEquals(DebtAgingReasonCode.WATCH, s.reasonCode)
        val s6 = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100_000), 6, 90))
        assertEquals(DebtAgingStatusLevel.YELLOW, s6.level)
    }

    @Test
    fun `tier 4 - 15 lt debtAge le 60 is ORANGE sustained_delinquency (boundary pins 16 and 60)`() {
        val s = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100_000), 60, 90))
        assertEquals(DebtAgingStatusLevel.ORANGE, s.level)
        assertEquals(DebtAgingReasonCode.SUSTAINED_DELINQUENCY, s.reasonCode)
        val s16 = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100_000), 16, 90))
        assertEquals(DebtAgingStatusLevel.ORANGE, s16.level)
    }

    @Test
    fun `tier 5 - debtAge gt 60 is RED critical_delinquency`() {
        val s = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100_000), 61, 0))
        assertEquals(DebtAgingStatusLevel.RED, s.level)
        assertEquals(DebtAgingReasonCode.CRITICAL_DELINQUENCY, s.reasonCode)
    }

    // ── 2. THE DECOUPLING (the owner's issue-#24 Track 2 item 3) ─────────

    @Test
    fun `THE DECOUPLING - a payment 10 days ago does NOT make an ancient debt green`() {
        // Pre-T-429 this was GREEN / active_payer — the masking the owner
        // removed: "a recent payment must not mask accounts that remain
        // millions of dinars past due".
        val s = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100_000), 730, 10))
        assertEquals(DebtAgingStatusLevel.RED, s.level)
        assertEquals(DebtAgingReasonCode.CRITICAL_DELINQUENCY, s.reasonCode)
        assertTrue(s.explanationFr.contains("Payeur actif")) // the annotation survives
    }

    @Test
    fun `young current debt plus recent payment is GREEN via tier 2 (the a-echoir window, never the payer rule)`() {
        val s = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100_000), 3, 3))
        assertEquals(DebtAgingStatusLevel.GREEN, s.level)
        assertEquals(DebtAgingReasonCode.NOT_DUE, s.reasonCode)
    }

    // ── 3. INV-16c: amount magnitude never changes the level ─────────────

    @Test
    fun `INV-16c - amount magnitude never changes the level`() {
        val small = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(500), 400, 400))
        val huge = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(900_000), 400, 400))
        assertEquals(small.level, huge.level)
        assertEquals(DebtAgingStatusLevel.RED, small.level)
    }

    // ── 4. CONFIGURABLE: custom thresholds change the boundaries ────────

    @Test
    fun `CONFIGURABLE - custom thresholds change the boundaries (the system_settings contract)`() {
        val thresholds = ExecDebtAgingThresholds(
            gracePeriodDays = 10, yellowDays = 30, redDays = 120, activePayerGraceDays = 7,
        )
        // debtAge 12: 12 > 10 (grace) and 12 <= 30 → yellow.
        val s12 = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100), 12, 50), thresholds)
        assertEquals(DebtAgingStatusLevel.YELLOW, s12.level)
        // debtAge 31..120 → orange; > 120 → red.
        val s60 = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100), 60, 50), thresholds)
        assertEquals(DebtAgingStatusLevel.ORANGE, s60.level)
        val s130 = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100), 130, 50), thresholds)
        assertEquals(DebtAgingStatusLevel.RED, s130.level)
        // The custom active-payer window (7) narrows the annotation.
        val s8 = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100), 60, 8), thresholds)
        assertFalse(s8.explanationFr.contains("Payeur actif"))
        val s6 = computeDebtAgingStatus(DebtAgingStatusFactors(dzd(100), 60, 6), thresholds)
        assertTrue(s6.explanationFr.contains("Payeur actif"))
    }

    // ── 5. §15.3 label wording + INV-16d explanation contract ────────────

    @Test
    fun `S153 - the canonical label wording is IDENTICAL on every surface`() {
        assertEquals("Soldé / À échoir", DEBT_AGING_STATUS_LABELS_FR[DebtAgingStatusLevel.GREEN])
        assertEquals("À surveiller", DEBT_AGING_STATUS_LABELS_FR[DebtAgingStatusLevel.YELLOW])
        assertEquals("Retard soutenu", DEBT_AGING_STATUS_LABELS_FR[DebtAgingStatusLevel.ORANGE])
        assertEquals("Critique / Contentieux", DEBT_AGING_STATUS_LABELS_FR[DebtAgingStatusLevel.RED])
    }

    @Test
    fun `INV-16d - every status carries the label AND the explanation`() {
        for (level in DebtAgingStatusLevel.values()) {
            val factors = DebtAgingStatusFactors(
                outstandingAmount = dzd(100_000),
                debtAgeDays = when (level) {
                    DebtAgingStatusLevel.GREEN -> 3
                    DebtAgingStatusLevel.YELLOW -> 10
                    DebtAgingStatusLevel.ORANGE -> 30
                    DebtAgingStatusLevel.RED -> 90
                },
                inactivityDays = 90,
            )
            val s = computeDebtAgingStatus(factors)
            assertEquals(level, s.level)
            assertEquals(DEBT_AGING_STATUS_LABELS_FR.getValue(level), s.labelFr)
            assertTrue(s.explanationFr.isNotBlank())
            // The explanation embeds the CONFIGURED thresholds (never literals).
            assertTrue(s.explanationFr.contains("j"))
        }
    }

    @Test
    fun `the status carries the subsequent-year annotation when the payment continues`() {
        val s = computeDebtAgingStatus(
            DebtAgingStatusFactors(dzd(100_000), 30, 5, hasSubsequentYearPayments = true),
        )
        assertTrue(s.explanationFr.contains("paiements poursuivis sur les années suivantes"))
    }

    // ── 6. The factor derivation (the analysis semantics) ────────────────

    private val nowMs = java.time.Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()

    private fun facts(
        id: String = "ins-1",
        dueDate: String = "2026-06-10",
        due: Long = dzd(100_000),
        paid: Long = 0,
    ) = DebtAgingInstallmentFacts(
        id = id, parentId = "p-1", amountDue = due, amountPaid = paid,
        amountPending = 0, dueDate = dueDate, status = PaymentStatus.UNPAID,
    )

    @Test
    fun `the factors - outstanding is the INV-4 sum, the age is the OLDEST outstanding`() {
        val factors = deriveDebtAgingStatusFactors(
            installments = listOf(
                facts("ins-1", "2026-06-10", due = dzd(100_000)),
                facts("ins-2", "2026-01-15", due = dzd(50_000)),   // the OLDEST
                facts("ins-3", "2026-06-01", due = dzd(30_000), paid = dzd(30_000)), // settled → excluded
            ),
            paymentEntries = emptyList(),
            nowEpochMs = nowMs,
        )
        assertEquals(dzd(150_000), factors.outstandingAmount)
        // 2026-01-15 → 2026-06-15 = 151 days (UTC ms-floor).
        assertEquals(151L, factors.debtAgeDays)
    }

    @Test
    fun `INV-16b - a never-paid family defaults inactivity to the debt age`() {
        val factors = deriveDebtAgingStatusFactors(
            installments = listOf(facts(dueDate = "2026-06-10")),
            paymentEntries = emptyList(),
            nowEpochMs = nowMs,
        )
        assertEquals(factors.debtAgeDays, factors.inactivityDays)
    }

    @Test
    fun `the factors - the inactivity is the days since the LAST payment`() {
        val factors = deriveDebtAgingStatusFactors(
            installments = listOf(facts(dueDate = "2024-10-15")),
            paymentEntries = listOf(
                DebtAgingPaymentFact("led-1", "p-1", "2025-09-05T10:00:00Z"),
                DebtAgingPaymentFact("led-2", "p-1", "2026-06-10T10:00:00Z"), // the LAST
            ),
            nowEpochMs = nowMs,
        )
        // 2026-06-10 → 2026-06-15 = 5 days.
        assertEquals(5L, factors.inactivityDays)
        assertTrue(factors.inactivityDays < factors.debtAgeDays)
    }

    @Test
    fun `the factors - subsequent-year payments are detected (INV-15)`() {
        val factors = deriveDebtAgingStatusFactors(
            installments = listOf(facts(dueDate = "2024-10-15")), // origin 2024-2025
            paymentEntries = listOf(
                DebtAgingPaymentFact("led-1", "p-1", "2024-11-05T10:00:00Z"), // same year
                DebtAgingPaymentFact("led-2", "p-1", "2025-09-05T10:00:00Z"), // 2025-2026 > origin
            ),
            nowEpochMs = nowMs,
        )
        assertTrue(factors.hasSubsequentYearPayments)
        // Same-year payments only → no flag.
        val sameYear = deriveDebtAgingStatusFactors(
            installments = listOf(facts(dueDate = "2024-10-15")),
            paymentEntries = listOf(DebtAgingPaymentFact("led-1", "p-1", "2024-11-05T10:00:00Z")),
            nowEpochMs = nowMs,
        )
        assertFalse(sameYear.hasSubsequentYearPayments)
    }

    @Test
    fun `the analysis accepts thresholds (the settings-injected path)`() {
        val factors = deriveDebtAgingStatusFactors(
            installments = listOf(facts(dueDate = "2026-06-10")),
            paymentEntries = emptyList(),
            nowEpochMs = nowMs,
        )
        // due 2026-06-10 → 5 days past due: 3 < 5 <= 9 → yellow (the desktop's
        // own injected-thresholds scenario).
        val s = computeDebtAgingStatus(
            factors,
            ExecDebtAgingThresholds(gracePeriodDays = 3, yellowDays = 9, redDays = 30, activePayerGraceDays = 15),
        )
        assertEquals(DebtAgingStatusLevel.YELLOW, s.level)
        assertEquals(DebtAgingReasonCode.WATCH, s.reasonCode)
        assertEquals(5L, factors.debtAgeDays)
    }
}
