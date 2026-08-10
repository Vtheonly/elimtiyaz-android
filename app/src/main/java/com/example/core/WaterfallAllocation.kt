package com.example.core

/**
 * Waterfall Allocation Engine — mirrors the desktop's
 * `src/domain/calc/payment/installments.ts` (`allocatePaymentToInstallments`)
 * and the SQL `allocate_payment_waterfall` RPC exactly.
 *
 * Distributes a single payment amount sequentially across unpaid /
 * partially-paid installments in chronological order (oldest due date first).
 * Guarantees that the Ledger and the Installment table stay mathematically
 * in sync:
 *
 *   sum(allocatedAmount) + unallocatedAmount === paymentAmount
 *
 * Any excess (overpayment) is returned as `unallocatedAmount` so the caller
 * can store it as parent credit.
 *
 * Branches on `paymentStatus`:
 *   - "paid"     → increments `amountPaid`; may transition status to
 *                  "paid" / "partial".
 *   - "pending"  → increments `amountPending` ONLY; status transitions to
 *                  "pending_clearance"; the tranche is NOT considered
 *                  satisfied until the underlying payment clears the bank.
 *
 * Invariant 4 (Cleared Funds Only): a tranche can only transition to "paid"
 * when cleared funds (cash or cleared check) cover its full amountDue.
 * Uncleared funds sit in `amountPending` and never reduce the debt.
 */

/** A single allocation result — how much of the payment was applied to one installment. */
data class InstallmentAllocation(
    val installmentId: String,
    val allocatedAmount: Long,
    val newAmountPaid: Long,
    val newAmountPending: Long,
    val newStatus: String, // paid | partial | overdue | pending | pending_clearance
    val fullySatisfied: Boolean,
    val cleared: Boolean,
)

/** Result of the waterfall allocation. */
data class AllocationResult(
    val allocations: List<InstallmentAllocation>,
    val unallocatedAmount: Long,
    val totalAllocated: Long,
    val paymentAmount: Long,
)

/**
 * Input installment for the waterfall — a slim projection of the full
 * [com.example.domain.model.Installment] to keep this function pure
 * (no Room/DAO dependencies).
 */
data class WaterfallInstallment(
    val id: String,
    val category: PaymentCategory,
    val amountDue: Long,
    val amountPaid: Long,
    val amountPending: Long,
    val dueDate: String,    // ISO
    val status: String,     // paid | partial | overdue | pending | pending_clearance
)

/**
 * Pure function: allocate a payment across unpaid installments in
 * chronological order (oldest due date first), like a waterfall.
 *
 * This function is PURE — it does not mutate the input installments.
 * Callers (repository layer) must persist the resulting new states.
 *
 * @param installments    All installments for the parent (will be filtered/sorted internally).
 * @param paymentAmount   The total amount being paid. Must be >= 0.
 * @param categoryFilter  Optional — if provided, only installments matching this category are eligible.
 * @param paymentStatus   Clearing status of the underlying payment. "paid" (cash) or "pending" (uncleared check/transfer).
 */
fun allocatePaymentToInstallments(
    installments: List<WaterfallInstallment>,
    paymentAmount: Long,
    categoryFilter: PaymentCategory? = null,
    paymentStatus: PaymentStatus = PaymentStatus.PAID,
): AllocationResult {
    if (paymentAmount <= 0L) {
        return AllocationResult(
            allocations = emptyList(),
            unallocatedAmount = 0L,
            totalAllocated = 0L,
            paymentAmount = paymentAmount,
        )
    }

    val eligible = installments
        .filter { it.status != "paid" }
        .filter { categoryFilter == null || it.category == categoryFilter }
        .sortedWith(compareBy({ it.dueDate }, { it.id }))

    val allocations = mutableListOf<InstallmentAllocation>()
    var remaining = paymentAmount
    val cleared = paymentStatus == PaymentStatus.PAID

    for (ins in eligible) {
        if (remaining <= 0L) break
        val insRemaining = (ins.amountDue - ins.amountPaid).coerceAtLeast(0L)
        if (insRemaining <= 0L) continue
        val allocate = minOf(remaining, insRemaining)
        var newAmountPaid = ins.amountPaid
        var newAmountPending = ins.amountPending
        val newStatus: String
        val fullySatisfied: Boolean

        if (cleared) {
            newAmountPaid = ins.amountPaid + allocate
            fullySatisfied = newAmountPaid >= ins.amountDue
            newStatus = when {
                fullySatisfied -> "paid"
                newAmountPaid > 0L -> "partial"
                ins.status == "overdue" -> "overdue"
                else -> "pending"
            }
        } else {
            // Pending: funds land in amountPending; status becomes "pending_clearance".
            newAmountPending = ins.amountPending + allocate
            fullySatisfied = false
            newStatus = "pending_clearance"
        }

        allocations.add(
            InstallmentAllocation(
                installmentId = ins.id,
                allocatedAmount = allocate,
                newAmountPaid = newAmountPaid,
                newAmountPending = newAmountPending,
                newStatus = newStatus,
                fullySatisfied = fullySatisfied,
                cleared = cleared,
            )
        )
        remaining -= allocate
    }

    val totalAllocated = paymentAmount - remaining
    return AllocationResult(
        allocations = allocations,
        unallocatedAmount = remaining.coerceAtLeast(0L),
        totalAllocated = totalAllocated,
        paymentAmount = paymentAmount,
    )
}

/* ============================================================ */
/*  Reverse-Waterfall (LIFO) — Refunds / Cancellations          */
/* ============================================================ */

/** A single un-allocation result. */
data class RevertAllocation(
    val installmentId: String,
    val revertedAmount: Long,
    val newAmountPaid: Long,
    val newAmountPending: Long,
    val newStatus: String, // paid | partial | overdue | pending
    val reopened: Boolean,
)

/** Result of the reverse-waterfall un-allocation. */
data class RevertAllocationResult(
    val reverts: List<RevertAllocation>,
    val totalReverted: Long,
    val unrevertedAmount: Long,
    val reversalAmount: Long,
)

/**
 * Re-evaluate an installment's status after a reversal.
 *
 *   - amountPaid >= amountDue && amountDue > 0  → "paid"
 *   - amountPaid > 0                            → "partial"
 *   - amountPaid == 0 & dueDate < now           → "overdue"
 *   - amountPaid == 0 & dueDate >= now          → "pending"
 */
fun reevaluateInstallmentStatus(
    amountPaid: Long,
    amountDue: Long,
    dueDate: String,
    nowEpochMs: Long = System.currentTimeMillis(),
): String {
    if (amountPaid >= amountDue && amountDue > 0L) return "paid"
    if (amountPaid > 0L) return "partial"
    val dueMs = try { java.time.Instant.parse(dueDate).toEpochMilli() } catch (_: Exception) { Long.MAX_VALUE }
    return if (dueMs < nowEpochMs) "overdue" else "pending"
}

/**
 * Pure function: reverse a prior waterfall allocation in LIFO order
 * (newest satisfied tranche un-allocated first).
 */
fun revertPaymentAllocation(
    installments: List<WaterfallInstallment>,
    reversalAmount: Long,
    categoryFilter: PaymentCategory? = null,
    originalWasPending: Boolean = false,
    nowEpochMs: Long = System.currentTimeMillis(),
): RevertAllocationResult {
    if (reversalAmount <= 0L) {
        return RevertAllocationResult(
            reverts = emptyList(),
            totalReverted = 0L,
            unrevertedAmount = 0L,
            reversalAmount = reversalAmount,
        )
    }

    val eligible = installments
        .filter { if (originalWasPending) it.amountPending > 0L else it.amountPaid > 0L }
        .filter { categoryFilter == null || it.category == categoryFilter }
        .sortedWith(compareByDescending<WaterfallInstallment> { it.dueDate }.thenByDescending { it.id })

    val reverts = mutableListOf<RevertAllocation>()
    var remaining = reversalAmount

    for (ins in eligible) {
        if (remaining <= 0L) break
        val bucket = if (originalWasPending) ins.amountPending else ins.amountPaid
        if (bucket <= 0L) continue
        val revert = minOf(remaining, bucket)
        val newAmountPaid = if (originalWasPending) ins.amountPaid else (ins.amountPaid - revert).coerceAtLeast(0L)
        val newAmountPending = if (originalWasPending) (ins.amountPending - revert).coerceAtLeast(0L) else ins.amountPending
        val newStatus = reevaluateInstallmentStatus(newAmountPaid, ins.amountDue, ins.dueDate, nowEpochMs)
        val reopened = ins.status == "paid" && newStatus != "paid"
        reverts.add(
            RevertAllocation(
                installmentId = ins.id,
                revertedAmount = revert,
                newAmountPaid = newAmountPaid,
                newAmountPending = newAmountPending,
                newStatus = newStatus,
                reopened = reopened,
            )
        )
        remaining -= revert
    }

    val totalReverted = reversalAmount - remaining
    return RevertAllocationResult(
        reverts = reverts,
        totalReverted = totalReverted,
        unrevertedAmount = remaining.coerceAtLeast(0L),
        reversalAmount = reversalAmount,
    )
}

/* ============================================================ */
/*  Aging + Overdue helpers (mirror desktop installments.ts)    */
/* ============================================================ */

/**
 * Map a days-overdue number to an aging bucket.
 *   0–30 → "0_30", 31–60 → "31_60", 61–90 → "61_90",
 *   91–180 → "91_180", 181+ → "180_plus".
 */
fun agingBucketFromDays(daysOverdue: Long): String = when {
    daysOverdue <= 30 -> "0_30"
    daysOverdue <= 60 -> "31_60"
    daysOverdue <= 90 -> "61_90"
    daysOverdue <= 180 -> "91_180"
    else -> "180_plus"
}

/**
 * Days between two ISO timestamps, floored (matches desktop
 * `Math.floor((now - dueDate) / 86_400_000)`).
 */
fun daysBetweenFloor(dueDateIso: String, nowEpochMs: Long = System.currentTimeMillis()): Long {
    val dueMs = try { java.time.Instant.parse(dueDateIso).toEpochMilli() } catch (_: Exception) { return 0L }
    return ((nowEpochMs - dueMs) / 86_400_000L).coerceAtLeast(0L)
}

/**
 * Official tuition tranche split per Prices.md — 40% / 30% / 30%.
 * The remainder is absorbed into tranche 3 to guarantee exact conservation:
 *   T1 = round(net × 0.40)
 *   T2 = round(net × 0.30)
 *   T3 = net − T1 − T2
 * Invariant: T1 + T2 + T3 === net (no dinar is lost or invented).
 */
fun splitNetTuitionByOfficialSchedule(netAnnual: Long): Triple<Long, Long, Long> {
    val t1 = Math.round(netAnnual * 0.40)
    val t2 = Math.round(netAnnual * 0.30)
    val t3 = netAnnual - t1 - t2
    return Triple(t1, t2, t3)
}

/**
 * Official tuition tranche due dates per Prices.md:
 *   Tranche 1: September 15 of startYear (at registration)
 *   Tranche 2: December 15 of startYear (window: Dec 1 – 15)
 *   Tranche 3: March 15 of startYear + 1 (window: Mar 1 – 15)
 */
fun officialTuitionDueDates(startYear: Int): Triple<String, String, String> {
    val t1 = java.time.OffsetDateTime.of(startYear, 9, 15, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toString()
    val t2 = java.time.OffsetDateTime.of(startYear, 12, 15, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toString()
    val t3 = java.time.OffsetDateTime.of(startYear + 1, 3, 15, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toString()
    return Triple(t1, t2, t3)
}
