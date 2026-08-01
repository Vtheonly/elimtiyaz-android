package com.example.core

import java.time.Instant

/**
 * Ledger engine — read-side computations over an immutable ledger.
 *
 * Entry construction (charge / payment / adjustment / refund / reversal) and
 * ID derivation have been extracted to top-level functions in
 * [LedgerEntryFactory.kt]; this object now holds only the replay-based
 * balance + summary + overdue computations that need to be addressed as
 * `LedgerEngine.computeXxx(...)` (they're referenced from cross-cutting
 * reconciliation, repository, and UI layers).
 *
 * Invariants preserved verbatim from the original implementation:
 *   - Balances are NEVER stored — always computed by replaying entries.
 *   - Reversed entries contribute to the running balance but NOT to typed
 *     totals (totalCharged / totalPaid / etc.).
 *   - The same ledger replayed twice yields identical results.
 */
object LedgerEngine {

    /**
     * Compute the balance of an account by replaying its ledger entries.
     * This is the ONLY way to compute a balance. Balances are NEVER stored.
     */
    fun computeAccountBalance(entries: List<LedgerEntry>, accountId: String, now: Instant = Instant.now()): AccountBalance {
        val nowIso = now.toString()
        val accountEntries = entries
            .filter { it.accountId == accountId && it.at <= nowIso }
            .sortedWith(compareBy({ it.at }, { it.id }))

        if (accountEntries.isEmpty()) {
            return AccountBalance(
                accountId = accountId, parentId = "", studentId = null,
                category = PaymentCategory.OTHER, balance = 0L,
                totalCharged = 0L, totalPaid = 0L, totalAdjusted = 0L, totalRefunded = 0L,
                totalCleared = 0L, totalPending = 0L,
                entryCount = 0, lastActivityAt = null,
            )
        }

        val reversedIds: Set<String> = accountEntries.mapNotNull { it.reversesId }.toSet()

        var balance = 0L
        var totalCharged = 0L
        var totalPaid = 0L
        var totalAdjusted = 0L
        var totalRefunded = 0L
        var totalCleared = 0L
        var totalPending = 0L
        var lastActivityAt: String? = null

        for (e in accountEntries) {
            balance += e.amount
            if (e.id in reversedIds) {
                lastActivityAt = maxOf(lastActivityAt ?: "", e.at)
                continue
            }
            when (e.type) {
                LedgerEntryType.CHARGE -> totalCharged += e.amount
                LedgerEntryType.PAYMENT -> {
                    totalPaid += kotlin.math.abs(e.amount)
                    when (e.paymentStatus) {
                        PaymentStatus.PAID -> totalCleared += kotlin.math.abs(e.amount)
                        PaymentStatus.PENDING -> totalPending += kotlin.math.abs(e.amount)
                        else -> {}
                    }
                }
                LedgerEntryType.ADJUSTMENT -> totalAdjusted += e.amount
                LedgerEntryType.REFUND -> totalRefunded += kotlin.math.abs(e.amount)
                LedgerEntryType.REVERSAL, LedgerEntryType.TRANSFER -> {}
            }
            lastActivityAt = maxOf(lastActivityAt ?: "", e.at)
        }

        val first = accountEntries.first()
        return AccountBalance(
            accountId = accountId, parentId = first.parentId, studentId = first.studentId,
            category = first.category, balance = balance,
            totalCharged = totalCharged, totalPaid = totalPaid, totalAdjusted = totalAdjusted,
            totalRefunded = totalRefunded, totalCleared = totalCleared, totalPending = totalPending,
            entryCount = accountEntries.size, lastActivityAt = lastActivityAt,
        )
    }

    /** Compute a parent's consolidated summary across all their accounts. */
    fun computeParentSummary(
        entries: List<LedgerEntry>, parentId: String, parentName: String,
        overdueCategoryDueDates: Map<String, Instant> = emptyMap(),
        now: Instant = Instant.now(),
    ): ParentLedgerSummary {
        val parentEntries = entries.filter { it.parentId == parentId }
        val accountIds = parentEntries.map { it.accountId }.distinct()
        val accounts = accountIds.map { computeAccountBalance(parentEntries, it, now) }

        var totalOutstanding = 0L
        var totalOverdue = 0L
        var totalCharged = 0L
        var totalPaid = 0L
        var totalCleared = 0L
        var totalPending = 0L
        var totalAdjusted = 0L
        var totalRefunded = 0L
        var entryCount = 0
        var lastActivityAt: String? = null

        for (acc in accounts) {
            totalOutstanding += acc.balance
            totalCharged += acc.totalCharged
            totalPaid += acc.totalPaid
            totalCleared += acc.totalCleared
            totalPending += acc.totalPending
            totalAdjusted += acc.totalAdjusted
            totalRefunded += acc.totalRefunded
            entryCount += acc.entryCount
            lastActivityAt = maxOf(lastActivityAt ?: "", acc.lastActivityAt ?: "")

            val dueDate = overdueCategoryDueDates[acc.accountId]
            if (dueDate != null && acc.balance > 100L && dueDate.isBefore(now)) {
                totalOverdue += acc.balance
            }
        }

        return ParentLedgerSummary(
            parentId = parentId, parentName = parentName,
            totalOutstanding = totalOutstanding, totalOverdue = totalOverdue,
            totalCharged = totalCharged, totalPaid = totalPaid, totalCleared = totalCleared,
            totalPending = totalPending, totalAdjusted = totalAdjusted, totalRefunded = totalRefunded,
            accounts = accounts, entryCount = entryCount, lastActivityAt = lastActivityAt,
        )
    }

    fun buildOverdueDueDateMap(entries: List<LedgerEntry>): Map<String, Instant> =
        entries.filter { it.type == LedgerEntryType.CHARGE }
            .groupBy { it.accountId }
            .mapValues { (_, e) -> e.maxOf { Instant.parse(it.at) } }

    fun maxDaysOverdueFromLedger(entries: List<LedgerEntry>, now: Instant = Instant.now()): Long {
        val pastCharges = entries.filter { it.type == LedgerEntryType.CHARGE && Instant.parse(it.at).isBefore(now) }
        if (pastCharges.isEmpty()) return 0L
        val oldest = pastCharges.minOf { Instant.parse(it.at) }
        return (now.toEpochMilli() - oldest.toEpochMilli()) / 86_400_000L
    }
}
