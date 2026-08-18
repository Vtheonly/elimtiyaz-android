package com.example.core

import java.time.Instant

/**
 * Waterfall Allocation Engine — mirrors the desktop's
 * `src/domain/calc/payment/installments.ts` (`allocatePaymentToInstallments`)
 * and the SQL `allocate_payment_waterfall` RPC exactly.
 */

data class InstallmentAllocation(
    val installmentId: String,
    val allocatedAmount: Long,
    val newAmountPaid: Long,
    val newAmountPending: Long,
    val newStatus: String,
    val fullySatisfied: Boolean,
    val cleared: Boolean,
)

data class AllocationResult(
    val allocations: List<InstallmentAllocation>,
    val unallocatedAmount: Long,
    val totalAllocated: Long,
    val paymentAmount: Long,
)

data class WaterfallInstallment(
    val id: String,
    val category: PaymentCategory,
    val amountDue: Long,
    val amountPaid: Long,
    val amountPending: Long,
    val dueDate: String,
    val status: String,
)

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
        .sortedWith(compareBy({ parseIsoInstantSafe(it.dueDate) }, { it.id }))

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

data class RevertAllocation(
    val installmentId: String,
    val revertedAmount: Long,
    val newAmountPaid: Long,
    val newAmountPending: Long,
    val newStatus: String,
    val reopened: Boolean,
)

data class RevertAllocationResult(
    val reverts: List<RevertAllocation>,
    val totalReverted: Long,
    val unrevertedAmount: Long,
    val reversalAmount: Long,
)

fun reevaluateInstallmentStatus(
    amountPaid: Long,
    amountDue: Long,
    dueDate: String,
    nowEpochMs: Long = System.currentTimeMillis(),
): String {
    if (amountPaid >= amountDue && amountDue > 0L) return "paid"
    if (amountPaid > 0L) return "partial"
    val dueMs = parseIsoInstantSafe(dueDate).toEpochMilli()
    return if (dueMs in 1 until nowEpochMs) "overdue" else "pending"
}

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
        .sortedWith(compareByDescending<WaterfallInstallment> { parseIsoInstantSafe(it.dueDate) }.thenByDescending { it.id })

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

fun agingBucketFromDays(daysOverdue: Long): String = when {
    daysOverdue <= 30 -> "0_30"
    daysOverdue <= 60 -> "31_60"
    daysOverdue <= 90 -> "61_90"
    daysOverdue <= 180 -> "91_180"
    else -> "180_plus"
}

fun daysBetweenFloor(dueDateIso: String, nowEpochMs: Long = System.currentTimeMillis()): Long {
    val dueMs = parseIsoInstantSafe(dueDateIso).toEpochMilli()
    if (dueMs == 0L || dueMs == Instant.EPOCH.toEpochMilli()) return 0L
    return ((nowEpochMs - dueMs) / 86_400_000L).coerceAtLeast(0L)
}

fun splitNetTuitionByOfficialSchedule(netAnnual: Long): Triple<Long, Long, Long> {
    val t1 = Math.round(netAnnual * 0.40)
    val t2 = Math.round(netAnnual * 0.30)
    val t3 = netAnnual - t1 - t2
    return Triple(t1, t2, t3)
}

fun officialTuitionDueDates(startYear: Int): Triple<String, String, String> {
    val t1 = java.time.OffsetDateTime.of(startYear, 9, 15, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toString()
    val t2 = java.time.OffsetDateTime.of(startYear, 12, 15, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toString()
    val t3 = java.time.OffsetDateTime.of(startYear + 1, 3, 15, 0, 0, 0, 0, java.time.ZoneOffset.UTC).toString()
    return Triple(t1, t2, t3)
}