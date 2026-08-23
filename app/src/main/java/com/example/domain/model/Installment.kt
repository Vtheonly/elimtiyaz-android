package com.example.domain.model

import com.example.core.PaymentCategory
import com.example.core.PaymentStatus
import kotlinx.serialization.Serializable

/**
 * Installment domain entity — a scheduled payment obligation.
 */
@Serializable
data class Installment(
    val id: String,
    val tenantId: String,
    val parentId: String,
    val studentId: String? = null,
    val category: PaymentCategory,
    val label: String,
    val amountDue: Long,
    val amountPaid: Long,
    // TIER 4 FIX (v2 audit D14 / R12) — pending-clearance bucket (uncleared
    // checks / transfers). The Room entity has carried this column since the
    // unified architecture, but toDomain() dropped it.
    val amountPending: Long = 0L,
    val dueDate: String,
    val paidDate: String? = null,
    val status: PaymentStatus,
    val academicCycle: String? = null,
    val customSchedule: Boolean = false,
    val customScheduleNote: String? = null,
) {
    val remaining: Long get() = (amountDue - amountPaid - amountPending).coerceAtLeast(0L)
}
