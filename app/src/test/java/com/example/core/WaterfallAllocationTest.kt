package com.example.core

import org.junit.Test
import org.junit.Assert.*
import org.junit.Before

/**
 * Unit tests for the Waterfall Allocation Engine — mirrors the desktop's
 * `tests/domain/payment/waterfall-allocation.test.ts` to guarantee both
 * platforms produce identical numbers.
 */
class WaterfallAllocationTest {

    private val installments = listOf(
        WaterfallInstallment("ins-t1", PaymentCategory.TUITION, 96_000L, 0L, 0L, "2026-09-15T00:00:00Z", "pending"),
        WaterfallInstallment("ins-t2", PaymentCategory.TUITION, 72_000L, 0L, 0L, "2026-12-15T00:00:00Z", "pending"),
        WaterfallInstallment("ins-t3", PaymentCategory.TUITION, 72_000L, 0L, 0L, "2027-03-15T00:00:00Z", "pending"),
    )

    @Test fun `zero payment produces empty allocation`() {
        val result = allocatePaymentToInstallments(installments, 0L)
        assertTrue(result.allocations.isEmpty())
        assertEquals(0L, result.unallocatedAmount)
        assertEquals(0L, result.totalAllocated)
    }

    @Test fun `payment fully satisfies tranche 1`() {
        val result = allocatePaymentToInstallments(installments, 96_000L)
        assertEquals(1, result.allocations.size)
        assertEquals("ins-t1", result.allocations[0].installmentId)
        assertEquals(96_000L, result.allocations[0].allocatedAmount)
        assertEquals(96_000L, result.allocations[0].newAmountPaid)
        assertTrue(result.allocations[0].fullySatisfied)
        assertEquals("paid", result.allocations[0].newStatus)
        assertEquals(0L, result.unallocatedAmount)
    }

    @Test fun `payment spills across two tranches (waterfall)`() {
        val result = allocatePaymentToInstallments(installments, 100_000L)
        assertEquals(2, result.allocations.size)
        // First tranche fully satisfied
        assertEquals("ins-t1", result.allocations[0].installmentId)
        assertEquals(96_000L, result.allocations[0].allocatedAmount)
        assertTrue(result.allocations[0].fullySatisfied)
        // Second tranche partially satisfied (4,000 of 72,000)
        assertEquals("ins-t2", result.allocations[1].installmentId)
        assertEquals(4_000L, result.allocations[1].allocatedAmount)
        assertFalse(result.allocations[1].fullySatisfied)
        assertEquals("partial", result.allocations[1].newStatus)
        assertEquals(0L, result.unallocatedAmount)
    }

    @Test fun `overpayment returns unallocated amount`() {
        val result = allocatePaymentToInstallments(installments, 300_000L)
        // Total due = 96k + 72k + 72k = 240k; payment = 300k → 60k overpayment
        assertEquals(240_000L, result.totalAllocated)
        assertEquals(60_000L, result.unallocatedAmount)
        assertTrue(result.allocations.all { it.fullySatisfied })
    }

    @Test fun `pending payment does not satisfy tranches`() {
        val result = allocatePaymentToInstallments(
            installments, 96_000L,
            paymentStatus = PaymentStatus.PENDING,
        )
        assertEquals(1, result.allocations.size)
        assertEquals(0L, result.allocations[0].newAmountPaid) // amountPaid unchanged
        assertEquals(96_000L, result.allocations[0].newAmountPending) // funds in pending
        assertFalse(result.allocations[0].fullySatisfied)
        assertEquals("pending_clearance", result.allocations[0].newStatus)
    }

    @Test fun `category filter restricts eligible installments`() {
        val mixed = installments + listOf(
            WaterfallInstallment("ins-tr1", PaymentCategory.TRANSPORT, 20_000L, 0L, 0L, "2026-09-15T00:00:00Z", "pending"),
        )
        val result = allocatePaymentToInstallments(mixed, 50_000L, categoryFilter = PaymentCategory.TUITION)
        assertTrue(result.allocations.all { it.installmentId.startsWith("ins-t") })
    }

    @Test fun `paid installments are skipped`() {
        val withPaid = installments.mapIndexed { i, ins ->
            if (i == 0) ins.copy(amountPaid = ins.amountDue, status = "paid") else ins
        }
        val result = allocatePaymentToInstallments(withPaid, 96_000L)
        // T1 is paid → skipped; T2 gets the full 72k; T3 gets 24k
        assertEquals(2, result.allocations.size)
        assertEquals("ins-t2", result.allocations[0].installmentId)
        assertEquals("ins-t3", result.allocations[1].installmentId)
    }

    @Test fun `LIFO reversal un-allocates newest tranche first`() {
        // Simulate a state where T1 and T2 are fully paid
        val paid = listOf(
            WaterfallInstallment("ins-t1", PaymentCategory.TUITION, 96_000L, 96_000L, 0L, "2026-09-15T00:00:00Z", "paid"),
            WaterfallInstallment("ins-t2", PaymentCategory.TUITION, 72_000L, 72_000L, 0L, "2026-12-15T00:00:00Z", "paid"),
            WaterfallInstallment("ins-t3", PaymentCategory.TUITION, 72_000L, 72_000L, 0L, "2027-03-15T00:00:00Z", "paid"),
        )
        // Reverse 72,000 — should un-allocate T3 (newest) first
        val result = revertPaymentAllocation(paid, 72_000L)
        assertEquals(1, result.reverts.size)
        assertEquals("ins-t3", result.reverts[0].installmentId)
        assertEquals(0L, result.reverts[0].newAmountPaid)
        assertEquals("pending", result.reverts[0].newStatus) // or "overdue" depending on dueDate
        assertTrue(result.reverts[0].reopened)
    }

    @Test fun `aging bucket boundaries match desktop`() {
        assertEquals("0_30", agingBucketFromDays(0))
        assertEquals("0_30", agingBucketFromDays(30))
        assertEquals("31_60", agingBucketFromDays(31))
        assertEquals("31_60", agingBucketFromDays(60))
        assertEquals("61_90", agingBucketFromDays(61))
        assertEquals("91_180", agingBucketFromDays(91))
        assertEquals("91_180", agingBucketFromDays(180))
        assertEquals("180_plus", agingBucketFromDays(181))
    }

    @Test fun `official tranche split conserves total exactly`() {
        val netAnnual = 245_000L // 1AP tuition
        val (t1, t2, t3) = splitNetTuitionByOfficialSchedule(netAnnual)
        assertEquals("Tranches must sum to net annual (no dinar lost)", netAnnual, t1 + t2 + t3)
        // 40% / 30% / 30% (with remainder in T3)
        assertEquals(98_000L, t1) // round(245000 * 0.40)
        assertEquals(73_500L, t2) // round(245000 * 0.30)
        assertEquals(73_500L, t3) // 245000 - 98000 - 73500
    }

    @Test fun `official due dates are Sept 15, Dec 15, Mar 15`() {
        val (t1, t2, t3) = officialTuitionDueDates(2026)
        assertTrue(t1.startsWith("2026-09-15"))
        assertTrue(t2.startsWith("2026-12-15"))
        assertTrue(t3.startsWith("2027-03-15"))
    }
}
