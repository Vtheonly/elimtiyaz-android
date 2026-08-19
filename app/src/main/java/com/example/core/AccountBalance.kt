package com.example.core

/**
 * Per-account balance snapshot — computed by [LedgerEngine.computeAccountBalance].
 *
 * CANONICAL-FINANCIAL-LOGIC.md §4 INV-1 — balance is computed by replaying
 * ledger entries, NEVER stored as a column.
 *
 * CANONICAL-FINANCIAL-LOGIC.md §4 INV-3 — `unallocatedCredit` is reported
 * as a NEGATIVE number (or zero) when the account is a `parent_credit`
 * account; for other accounts it is always 0. Auto-absorb logic for future
 * charges scans accounts with `unallocatedCredit < 0`.
 */
data class AccountBalance(
    val accountId: String, val parentId: String, val studentId: String?,
    val category: PaymentCategory,
    val balance: Long, val totalCharged: Long, val totalPaid: Long,
    val totalAdjusted: Long, val totalRefunded: Long, val totalCleared: Long,
    val totalPending: Long,
    // CANONICAL-FINANCIAL-LOGIC.md §4 INV-3 — parent_credit rollup.
    // Negative number (or 0) representing banked overpayment credit.
    val unallocatedCredit: Long = 0L,
    val entryCount: Int, val lastActivityAt: String?,
)
