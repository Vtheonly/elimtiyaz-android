package com.example.core

data class ParentLedgerSummary(
    val parentId: String, val parentName: String,
    val totalOutstanding: Long, val totalOverdue: Long,
    val totalCharged: Long, val totalPaid: Long, val totalCleared: Long,
    val totalPending: Long, val totalAdjusted: Long, val totalRefunded: Long,
    val accounts: List<AccountBalance>, val entryCount: Int, val lastActivityAt: String?,
)
