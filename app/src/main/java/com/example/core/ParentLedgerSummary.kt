package com.example.core

/**
 * Parent-level aggregate — computed by [LedgerEngine.computeParentSummary].
 *
 * CANONICAL-FINANCIAL-LOGIC.md §4 INV-1 + INV-3 — every field is derived
 * by replaying ledger entries; balances are never stored.
 *
 * `totalUnallocatedCredit` is the parent-wide sum of `unallocatedCredit`
 * across all `parent_credit` accounts for this parent. It is a NEGATIVE
 * number (or 0) representing banked overpayment credit available to
 * auto-absorb on future charges.
 */
data class ParentLedgerSummary(
    val parentId: String, val parentName: String,
    val totalOutstanding: Long, val totalOverdue: Long,
    val totalCharged: Long, val totalPaid: Long, val totalCleared: Long,
    val totalPending: Long, val totalAdjusted: Long, val totalRefunded: Long,
    // CANONICAL-FINANCIAL-LOGIC.md §4 INV-3 — parent-wide credit rollup.
    val totalUnallocatedCredit: Long = 0L,
    val accounts: List<AccountBalance>, val entryCount: Int, val lastActivityAt: String?,
)
