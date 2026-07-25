package com.elimtiyaz.domain.model

import kotlinx.serialization.Serializable

/**
 * Expense workflow — master plan §08.
 * Two-tier approval lifecycle: submit → approve/reject → disburse → settle (proof).
 */

@Serializable
data class Expense(
    val id: String,
    val tenantId: String,
    val requestCode: String,
    val title: String,
    val description: String,
    val amount: Double,
    val category: ExpenseCategory,
    val payee: String,
    val status: String,
    val submittedBy: String,
    val submittedAt: String,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val approvalNote: String? = null,
    val disbursedBy: String? = null,
    val disbursedAt: String? = null,
    val proofUrl: String? = null,
    val proofUploadedBy: String? = null,
    val proofUploadedAt: String? = null,
    val anomalyScore: Double? = null,
    val anomalyNote: String? = null,
)

@Serializable
enum class ExpenseCategory { Utilities, Supplies, Maintenance, Transport, Event, Salary, Tax, Rent, Other }

@Serializable
data class CreateExpenseInput(
    val title: String,
    val description: String,
    val amount: Double,
    val category: ExpenseCategory,
    val payee: String,
)
