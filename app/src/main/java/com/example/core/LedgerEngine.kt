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
            .mapValues { (_, e) -> e.maxOf { parseIsoInstantSafe(it.at) } }

    fun maxDaysOverdueFromLedger(entries: List<LedgerEntry>, now: Instant = Instant.now()): Long {
        val pastCharges = entries.filter { it.type == LedgerEntryType.CHARGE && parseIsoInstantSafe(it.at).isBefore(now) }
        if (pastCharges.isEmpty()) return 0L
        val oldest = pastCharges.minOf { parseIsoInstantSafe(it.at) }
        return (now.toEpochMilli() - oldest.toEpochMilli()) / 86_400_000L
    }
}