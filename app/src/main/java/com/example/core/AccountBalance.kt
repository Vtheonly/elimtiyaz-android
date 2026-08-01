package com.example.core

data class AccountBalance(
    val accountId: String, val parentId: String, val studentId: String?,
    val category: PaymentCategory,
    val balance: Long, val totalCharged: Long, val totalPaid: Long,
    val totalAdjusted: Long, val totalRefunded: Long, val totalCleared: Long,
    val totalPending: Long, val entryCount: Int, val lastActivityAt: String?,
)
