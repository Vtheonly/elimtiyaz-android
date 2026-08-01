package com.example.domain.model

import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import kotlinx.serialization.Serializable

/**
 * Payment domain entity.
 *
 * Amounts are Long (centimes) — NEVER Double.
 */
@Serializable
data class Payment(
    val id: String,
    val tenantId: String,
    val receiptNumber: String,
    val parentId: String,
    val studentId: String? = null,
    val amount: Long,                    // centimes
    val method: PaymentMethod,
    val status: PaymentStatus,
    val category: PaymentCategory,
    val installmentId: String? = null,
    val proofUrl: String? = null,
    val notes: String? = null,
    val collectedBy: String,
    val collectedAt: String,
    val createdAt: String,
    val updatedAt: String,
)
