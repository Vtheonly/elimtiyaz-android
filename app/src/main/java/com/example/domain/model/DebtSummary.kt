package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Per-parent debt summary row — sourced from the `mv_debt_summary`
 * materialized view. `bucket` is the aging bucket assignment.
 */
@Serializable
data class DebtSummary(
    val parentId: String,
    val parentName: String,
    val parentPhone: String,
    val studentCount: Int,
    val outstandingAmount: Long,
    val daysOverdue: Long,
    val bucket: String,                  // 0_30 | 31_60 | 61_90 | 91_180 | 180_plus
)
