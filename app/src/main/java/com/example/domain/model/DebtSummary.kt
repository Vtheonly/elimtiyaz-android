package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Per-parent debt summary row — the shared canonical derivation
 * (PARITY-002/T-284: per-parent Σ INV-4 remaining over unpaid installments,
 * dueDate-based aging — identical to the desktop's Supabase seedSummary and
 * the dashboard's top-debtors list). `bucket` is the aging bucket assignment.
 */
@Serializable
data class DebtSummary(
    val parentId: String,
    val parentName: String,
    val parentPhone: String,
    val studentCount: Int,
    val outstandingAmount: Long,
    /** Σ INV-4 remaining restricted to PAST-DUE unpaid installments (the desktop overdue portion). */
    val overdueAmount: Long = 0L,
    val daysOverdue: Long,
    val bucket: String,                  // 0_30 | 31_60 | 61_90 | 91_180 | 180_plus
)
