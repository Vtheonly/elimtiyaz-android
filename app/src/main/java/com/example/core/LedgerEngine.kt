package com.example.core

import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * Safe ISO-8601 timestamp parser that supports offsets (+00:00, +01:00),
 * UTC (Z), and date-only (yyyy-MM-dd) strings without throwing.
 */
fun parseIsoInstantSafe(isoString: String?): Instant {
    if (isoString.isNullOrBlank()) return Instant.EPOCH
    return try {
        OffsetDateTime.parse(isoString).toInstant()
    } catch (_: Exception) {
        try {
            Instant.parse(isoString)
        } catch (_: Exception) {
            try {
                LocalDate.parse(isoString).atStartOfDay(ZoneOffset.UTC).toInstant()
            } catch (_: Exception) {
                Instant.EPOCH
            }
        }
    }
}

/**
 * Ledger engine — read-side computations over an immutable ledger.
 */
object LedgerEngine {

    /**
     * Compute the balance of an account by replaying its ledger entries.
     * This is the ONLY way to compute a balance. Balances are NEVER stored.
     */
    fun computeAccountBalance(entries: List<LedgerEntry>, accountId: String, now: Instant = Instant.now()): AccountBalance {
        val accountEntries = entries
            .filter { it.accountId == accountId && parseIsoInstantSafe(it.at) <= now }
            .sortedWith(compareBy({ parseIsoInstantSafe(it.at) }, { it.id }))

        if (accountEntries.isEmpty()) {
            return AccountBalance(
                accountId = accountId, parentId = "", studentId = null,
                category = PaymentCategory.OTHER, balance = 0L,
                totalCharged = 0L, totalPaid = 0L, totalAdjusted = 0L, totalRefunded = 0L,
                totalCleared = 0L, totalPending = 0L, unallocatedCredit = 0L,
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
        // CANONICAL-FINANCIAL-LOGIC.md §4 INV-3 — parent_credit rollup.
        var unallocatedCredit = 0L
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
                LedgerEntryType.ADJUSTMENT -> {
                    totalAdjusted += e.amount
                    // CANONICAL-FINANCIAL-LOGIC.md §4 INV-3 — parent_credit
                    // adjustments are tracked separately so callers can auto-
                    // absorb them on future charges.
                    if (e.category == PaymentCategory.PARENT_CREDIT) {
                        unallocatedCredit += e.amount
                    }
                }
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
            unallocatedCredit = unallocatedCredit,
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
        // CANONICAL-FINANCIAL-LOGIC.md §4 INV-3 — parent-wide rollup.
        var totalUnallocatedCredit = 0L
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
            totalUnallocatedCredit += acc.unallocatedCredit
            entryCount += acc.entryCount
            lastActivityAt = maxOf(lastActivityAt ?: "", acc.lastActivityAt ?: "")

            val dueDate = overdueCategoryDueDates[acc.accountId]
            // CANONICAL-FINANCIAL-LOGIC.md §4 INV-4 — overdue threshold is
            // 0.001 DZD (= 1 millime). Since we store centimes, use `> 0L`
            // so even a 1-centime outstanding is flagged overdue. This
            // matches the desktop's `> 0.001 DZD` threshold.
            if (dueDate != null && acc.balance > 0L && dueDate.isBefore(now)) {
                totalOverdue += acc.balance
            }
        }

        return ParentLedgerSummary(
            parentId = parentId, parentName = parentName,
            totalOutstanding = totalOutstanding, totalOverdue = totalOverdue,
            totalCharged = totalCharged, totalPaid = totalPaid, totalCleared = totalCleared,
            totalPending = totalPending, totalAdjusted = totalAdjusted, totalRefunded = totalRefunded,
            totalUnallocatedCredit = totalUnallocatedCredit,
            accounts = accounts, entryCount = entryCount, lastActivityAt = lastActivityAt,
        )
    }

    fun buildOverdueDueDateMap(entries: List<LedgerEntry>): Map<String, Instant> =
        entries.filter { it.type == LedgerEntryType.CHARGE }
            .groupBy { it.accountId }
            .mapValues { (_, e) -> e.maxOf { parseIsoInstantSafe(it.at) } }

    /**
     * T-026 (BUSINESS-007): days-overdue measured from the DUE DATE, not the
     * charge's creation date. An account is overdue under the canonical rule
     * (INV-4, identical inputs to `computeParentSummary.totalOverdue`):
     * balance > 0 AND the account's due date (latest charge `at`) is past.
     * Days overdue = floor(now - dueDate) for each OVERDUE account; the
     * result is the max across accounts (0 when nothing is overdue).
     *
     * The old implementation returned the age of the OLDEST charge entry —
     * a charge created today for next year's tuition read as "~365 days
     * overdue" even though nothing was due yet.
     */
    fun maxDaysOverdueFromLedger(entries: List<LedgerEntry>, now: Instant = Instant.now()): Long {
        val dueDateMap = buildOverdueDueDateMap(entries)
        val accountIds = entries.map { it.accountId }.distinct()
        var maxDays = 0L
        for (accId in accountIds) {
            val due = dueDateMap[accId] ?: continue
            if (!due.isBefore(now)) continue // not due yet — cannot be overdue
            val balance = computeAccountBalance(entries, accId, now).balance
            if (balance <= 0L) continue // settled account is never overdue
            val days = (now.toEpochMilli() - due.toEpochMilli()) / 86_400_000L
            if (days > maxDays) maxDays = days
        }
        return maxDays
    }
}
