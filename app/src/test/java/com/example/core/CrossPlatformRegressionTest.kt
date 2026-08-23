package com.example.core

import com.example.domain.model.Assessment
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * REGRESSION TESTS — every discrepancy fixed during the TIER 4
 * cross-platform unification pass, locked in as permanent Android unit tests.
 *
 * Each test names the discrepancy it guards against, so a future regression
 * fails with an explanation instead of a silent semantic drift.
 */
class CrossPlatformRegressionTest {

    // ── GPA contamination by extracurricular modules ──────────────────────

    @Test fun `REGRESSION gpa excludes extracurricular modules`() {
        // Desktop academic.ts + SQL fn_calculate_student_term_gpa exclude
        // isExtracurricular subjects. Android previously had NO flag on
        // Assessment and contaminated the official GPA with chess / therapy.
        val assessments = listOf(
            Assessment("a1", "t", "s1", "sub1", "c1", "T1", "2026",
                subjectAverage = 15.0, coefficient = 4.0, isExtracurricular = false,
                enteredBy = "u", enteredAt = "now"),
            Assessment("a2", "t", "s1", "sub2", "c1", "T1", "2026",
                subjectAverage = 20.0, coefficient = 2.0, isExtracurricular = true,
                enteredBy = "u", enteredAt = "now"),
        )
        assertEquals(15.0, computeOverallGpa(assessments)!!, 1e-9)
    }

    // ── Coefficient Int truncation ────────────────────────────────────────

    @Test fun `REGRESSION gpa supports fractional coefficients`() {
        // SQL is NUMERIC(4,2); desktop is `number`. Android's previous Int
        // column truncated 1.5 → 1 corrupting the weighted average.
        val assessments = listOf(
            Assessment("a1", "t", "s1", "sub1", "c1", "T1", "2026",
                subjectAverage = 15.0, coefficient = 2.5,
                enteredBy = "u", enteredAt = "now"),
            Assessment("a2", "t", "s1", "sub2", "c1", "T1", "2026",
                subjectAverage = 12.0, coefficient = 1.5,
                enteredBy = "u", enteredAt = "now"),
        )
        // (15×2.5 + 12×1.5) / 4 = 55.5/4 = 13.875 → 13.88
        assertEquals(13.88, computeOverallGpa(assessments)!!, 1e-9)
    }

    // ── GPA boundary rounding (binary toFixed vs decimal half-up) ─────────

    @Test fun `REGRESSION gpa rounds xx5 boundaries with decimal half-up`() {
        // (16.5×3 + 12.0×1)/4 = 61.5/4 = 15.375 → 15.38 (decimal half-up,
        // bit-identical to PostgreSQL ROUND(numeric,2); the old binary-float
        // path produced 15.37).
        val assessments = listOf(
            Assessment("a1", "t", "s1", "sub1", "c1", "T1", "2026",
                subjectAverage = 16.5, coefficient = 3.0,
                enteredBy = "u", enteredAt = "now"),
            Assessment("a2", "t", "s1", "sub2", "c1", "T1", "2026",
                subjectAverage = 12.0, coefficient = 1.0,
                enteredBy = "u", enteredAt = "now"),
        )
        assertEquals(15.38, computeOverallGpa(assessments)!!, 1e-9)
    }

    // ── Null-grade coercion ───────────────────────────────────────────────

    @Test fun `REGRESSION subject average requires all three marks`() {
        // The SQL trigger leaves subject_average NULL while any mark is
        // missing; the old Android rule coerced nulls to 0 and deflated
        // partial assessments.
        assertNull(computeSubjectAverage(12.0, null, 15.0))
        assertNull(computeSubjectAverage(null, null, null))
        assertEquals(16.5, computeSubjectAverage(14.0, 16.0, 18.0)!!, 1e-9)
    }

    @Test fun `REGRESSION subject average rounds xx5 with decimal half-up`() {
        // (12 + 13 + 2*14.75)/4 = 54.5/4 = 13.625 → 13.63
        assertEquals(13.63, computeSubjectAverage(12.0, 13.0, 14.75)!!, 1e-9)
        // (11 + 13 + 2*14.775)/4 = 53.55/4 = 13.3875 → 13.39
        assertEquals(13.39, computeSubjectAverage(11.0, 13.0, 14.775)!!, 1e-9)
    }

    // ── deterministicParentCode hash-input alignment ──────────────────────

    @Test fun `REGRESSION parent code filters null and empty identity fields identically`() {
        // Desktop previously joined empty strings while Android skipped
        // nulls — the same parent hashed differently per platform, breaking
        // the idempotent (tenant_id, parent_code) upsert match.
        val withNulls = ParentCodeInput(
            phone = "0550123456", displayName = "Famille ZIREG",
            firstName = null, lastName = null,
        )
        val withEmpties = ParentCodeInput(
            phone = "0550123456", displayName = "Famille ZIREG",
            firstName = "", lastName = "",
        )
        val withWhitespace = ParentCodeInput(
            phone = " 0550123456 ", displayName = " Famille ZIREG ",
            firstName = "  ", lastName = null,
        )
        val c1 = deterministicParentCode(2026, withNulls)
        val c2 = deterministicParentCode(2026, withEmpties)
        val c3 = deterministicParentCode(2026, withWhitespace)
        assertEquals(c1, c2, "null and empty identity fields must hash identically")
        assertEquals(c1, c3, "whitespace-only fields must be trimmed away")
        assertTrue(c1.startsWith("PAR-2026-"), "code format: $c1")
    }

    @Test fun `REGRESSION stable hash matches canonical FNV-1a vectors`() {
        assertEquals("811C9D", stableHash(""))
        assertEquals("E40C29", stableHash("a"))
    }

    // ── Promotion stub ────────────────────────────────────────────────────

    @Test fun `REGRESSION grade progression follows the Algerian ladder`() {
        assertEquals("1am", getNextGradeProgression("5ap").nextGradeCode)
        assertEquals("cem", getNextGradeProgression("5ap").nextCycle)
        assertEquals("1ere_annee", getNextGradeProgression("4am").nextGradeCode)
        assertEquals("1ap", getNextGradeProgression("prescolaire_2").nextGradeCode)
        assertTrue(getNextGradeProgression("3eme_annee").isGraduation)
        assertNull(getNextGradeProgression("3eme_annee").nextGradeCode)
        assertNull(getNextGradeProgression("unknown_grade").nextGradeCode)
    }

    // ── Overpayment credit account (INV-7) ────────────────────────────────

    @Test fun `REGRESSION overpayment credit derives the parent_scoped parent_credit account`() {
        val accountId = deriveAccountId("par-77", PaymentCategory.PARENT_CREDIT, null)
        assertEquals("parent:par-77:category:parent_credit", accountId)
    }

    // ── Waterfall status transitions ──────────────────────────────────────

    @Test fun `REGRESSION pending payment sets pending_clearance and never paid`() {
        val installments = listOf(
            WaterfallInstallment("ins-1", PaymentCategory.TUITION, 1_000_000L, 0L, 0L, "2026-09-15", "unpaid"),
        )
        val result = allocatePaymentToInstallments(
            installments, 400_000L, PaymentCategory.TUITION, PaymentStatus.PENDING,
        )
        assertEquals(400_000L, result.totalAllocated)
        assertEquals("pending_clearance", result.allocations[0].newStatus)
        assertEquals(400_000L, result.allocations[0].newAmountPending)
        assertEquals(0L, result.allocations[0].newAmountPaid)
    }

    @Test fun `REGRESSION waterfall ordering is chronological then id`() {
        val installments = listOf(
            WaterfallInstallment("ins-b", PaymentCategory.TUITION, 1_000_000L, 0L, 0L, "2026-12-15", "unpaid"),
            WaterfallInstallment("ins-a", PaymentCategory.TUITION, 1_000_000L, 0L, 0L, "2026-09-15", "unpaid"),
        )
        val result = allocatePaymentToInstallments(installments, 1_500_000L, PaymentCategory.TUITION, PaymentStatus.PAID)
        assertEquals("ins-a", result.allocations[0].installmentId)
        assertEquals(1_000_000L, result.allocations[0].allocatedAmount)
        assertEquals("ins-b", result.allocations[1].installmentId)
        assertEquals(500_000L, result.allocations[1].allocatedAmount)
        assertEquals("paid", result.allocations[0].newStatus)
        assertEquals("partial", result.allocations[1].newStatus)
    }

    // ── Net-tuition split invariant ───────────────────────────────────────

    @Test fun `REGRESSION net tuition split sums exactly and uses 40-30-30`() {
        val net = 9_850_000L
        val (t1, t2, t3) = splitNetTuitionByOfficialSchedule(net)
        assertEquals(net, t1 + t2 + t3, "tranches must sum to the net exactly")
        assertEquals(3_940_000L, t1) // round(net × 0.40)
        assertEquals(2_955_000L, t2) // round(net × 0.30)
        assertEquals(2_955_000L, t3) // remainder absorbed
    }

    // ── Reconciler violation shapes ───────────────────────────────────────

    @Test fun `REGRESSION orphan reversal violation matches desktop wording and details`() {
        val entries = listOf(
            LedgerEntry(
                id = "led-1", tenantId = "t1",
                accountId = "parent:p1:category:tuition",
                parentId = "p1", studentId = null,
                category = PaymentCategory.TUITION, amount = -1_000_000L,
                type = LedgerEntryType.REVERSAL, sourceType = LedgerSourceType.PAYMENT,
                sourceId = "pay-1", method = null, receiptNumber = null,
                paymentStatus = null, reversesId = "led-999-does-not-exist",
                description = "reversal", actorId = "u1", actorName = "A", at = "2026-01-01T00:00:00Z",
                metadata = emptyMap(),
            ),
        )
        val report = Reconcile.reconcileLedger(entries, Reconcile.CrossCheckInputs())
        val orphan = report.violations.first { it.code == "ORPHAN_REVERSAL" }
        assertEquals(
            "Reversal entry led-1 references non-existent original led-999-does-not-exist.",
            orphan.message,
        )
        assertEquals("led-999-does-not-exist", orphan.details["reversesId"])
    }
}
