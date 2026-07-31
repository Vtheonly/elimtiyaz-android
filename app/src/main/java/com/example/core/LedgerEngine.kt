package com.example.core

import java.time.Instant
import java.util.UUID

/**
 * Ledger engine — pure functions, no I/O. Mirrors desktop `ledger.ts`.
 * Deterministic: same inputs always produce same outputs.
 *
 * The 5 determinism invariants:
 *   1. Complete audit trail — every DZD has a traceable origin.
 *   2. Determinism — replaying the ledger always yields the same balance.
 *   3. No ambiguity — exactly one way to compute any balance.
 *   4. Reversibility — corrections are new entries with reversesId.
 *   5. Reconcilability — sum of all entries equals sum of all balances.
 */
object LedgerEngine {

    /** Derive an account ID. Pure, deterministic. No `accounts` table exists. */
    fun deriveAccountId(parentId: String, category: PaymentCategory, studentId: String? = null): String {
        val parts = mutableListOf("parent", parentId, "category", category.code)
        if (studentId != null) { parts.add("student"); parts.add(studentId) }
        return parts.joinToString(":")
    }

    fun generateEntryId(at: Instant = Instant.now()): String {
        val datePart = at.toString().substring(0, 10).replace("-", "")
        val randomPart = UUID.randomUUID().toString().replace("-", "").take(8)
        return "led-$datePart-$randomPart"
    }

    fun createChargeEntry(
        tenantId: String, parentId: String, studentId: String?,
        category: PaymentCategory, amount: Long,
        sourceType: LedgerSourceType, sourceId: String,
        actorId: String, actorName: String, description: String,
        receiptNumber: String? = null, paymentStatus: PaymentStatus? = null,
        at: Instant = Instant.now(), metadata: Map<String, Any?> = emptyMap(),
    ): LedgerEntry {
        require(amount > 0) { "Charge amount must be > 0 (got $amount)" }
        require(description.isNotBlank()) { "Charge description must be non-blank" }
        require(actorId.isNotBlank()) { "Charge actorId must be non-blank" }
        require(actorName.isNotBlank()) { "Charge actorName must be non-blank" }
        return LedgerEntry(
            id = generateEntryId(at), tenantId = tenantId,
            accountId = deriveAccountId(parentId, category, studentId),
            parentId = parentId, studentId = studentId, category = category,
            amount = amount, type = LedgerEntryType.CHARGE,
            sourceType = sourceType, sourceId = sourceId,
            method = null, receiptNumber = receiptNumber, paymentStatus = paymentStatus,
            reversesId = null, description = description,
            actorId = actorId, actorName = actorName, at = at.toString(),
            metadata = metadata.toMap(),
        )
    }

    fun createPaymentEntry(
        tenantId: String, parentId: String, studentId: String?,
        category: PaymentCategory, amount: Long,
        method: PaymentMethod, receiptNumber: String, paymentStatus: PaymentStatus,
        sourceId: String, actorId: String, actorName: String, description: String,
        at: Instant = Instant.now(), metadata: Map<String, Any?> = emptyMap(),
    ): LedgerEntry {
        require(amount > 0) { "Payment amount must be > 0 (got $amount)" }
        require(description.isNotBlank()) { "Payment description must be non-blank" }
        return LedgerEntry(
            id = generateEntryId(at), tenantId = tenantId,
            accountId = deriveAccountId(parentId, category, studentId),
            parentId = parentId, studentId = studentId, category = category,
            amount = -amount, type = LedgerEntryType.PAYMENT,
            sourceType = LedgerSourceType.PAYMENT, sourceId = sourceId,
            method = method, receiptNumber = receiptNumber, paymentStatus = paymentStatus,
            reversesId = null, description = description,
            actorId = actorId, actorName = actorName, at = at.toString(),
            metadata = metadata.toMap(),
        )
    }

    fun createAdjustmentEntry(
        tenantId: String, parentId: String, studentId: String?,
        category: PaymentCategory, amount: Long,
        sourceId: String, actorId: String, actorName: String, reason: String,
        receiptRef: String? = null,
        at: Instant = Instant.now(), metadata: Map<String, Any?> = emptyMap(),
    ): LedgerEntry {
        require(amount != 0L) { "Adjustment amount must be != 0 (got $amount)" }
        require(reason.isNotBlank()) { "Adjustment reason must be non-blank" }
        return LedgerEntry(
            id = generateEntryId(at), tenantId = tenantId,
            accountId = deriveAccountId(parentId, category, studentId),
            parentId = parentId, studentId = studentId, category = category,
            amount = amount, type = LedgerEntryType.ADJUSTMENT,
            sourceType = LedgerSourceType.ADJUSTMENT, sourceId = sourceId,
            method = null, receiptNumber = receiptRef, paymentStatus = null,
            reversesId = null, description = reason,
            actorId = actorId, actorName = actorName, at = at.toString(),
            metadata = metadata.toMap(),
        )
    }

    fun createRefundEntry(
        tenantId: String, parentId: String, studentId: String?,
        category: PaymentCategory, amount: Long,
        sourceId: String, actorId: String, actorName: String, reason: String,
        method: PaymentMethod, receiptNumber: String?,
        at: Instant = Instant.now(), metadata: Map<String, Any?> = emptyMap(),
    ): LedgerEntry {
        require(amount > 0) { "Refund amount must be > 0 (got $amount)" }
        require(reason.isNotBlank()) { "Refund reason must be non-blank" }
        return LedgerEntry(
            id = generateEntryId(at), tenantId = tenantId,
            accountId = deriveAccountId(parentId, category, studentId),
            parentId = parentId, studentId = studentId, category = category,
            amount = -amount, type = LedgerEntryType.REFUND,
            sourceType = LedgerSourceType.REFUND, sourceId = sourceId,
            method = method, receiptNumber = receiptNumber, paymentStatus = PaymentStatus.REFUNDED,
            reversesId = null, description = reason,
            actorId = actorId, actorName = actorName, at = at.toString(),
            metadata = metadata.toMap(),
        )
    }

    /** Create a reversal entry that negates a prior entry. */
    fun createReversalEntry(
        original: LedgerEntry, reason: String,
        actorId: String, actorName: String,
        at: Instant = Instant.now(),
    ): LedgerEntry {
        require(reason.isNotBlank()) { "Reversal reason must be non-blank" }
        require(actorId.isNotBlank()) { "Reversal actorId must be non-blank" }
        return LedgerEntry(
            id = generateEntryId(at), tenantId = original.tenantId,
            accountId = original.accountId, parentId = original.parentId,
            studentId = original.studentId, category = original.category,
            amount = -original.amount, type = LedgerEntryType.REVERSAL,
            sourceType = original.sourceType, sourceId = original.sourceId,
            method = original.method, receiptNumber = original.receiptNumber,
            paymentStatus = original.paymentStatus,
            reversesId = original.id,
            description = "REVERSAL of ${original.id}: $reason",
            actorId = actorId, actorName = actorName, at = at.toString(),
            metadata = mapOf("reversedEntryId" to original.id, "reason" to reason),
        )
    }

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

data class AccountBalance(
    val accountId: String, val parentId: String, val studentId: String?,
    val category: PaymentCategory,
    val balance: Long, val totalCharged: Long, val totalPaid: Long,
    val totalAdjusted: Long, val totalRefunded: Long, val totalCleared: Long,
    val totalPending: Long, val entryCount: Int, val lastActivityAt: String?,
)

data class ParentLedgerSummary(
    val parentId: String, val parentName: String,
    val totalOutstanding: Long, val totalOverdue: Long,
    val totalCharged: Long, val totalPaid: Long, val totalCleared: Long,
    val totalPending: Long, val totalAdjusted: Long, val totalRefunded: Long,
    val accounts: List<AccountBalance>, val entryCount: Int, val lastActivityAt: String?,
)
