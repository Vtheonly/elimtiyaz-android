package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Expense domain entity — staff-submitted expense request with multi-state
 * approval workflow (draft → submitted → approved/rejected → disbursed → settled).
 */
@Serializable
data class Expense(
    val id: String,
    val tenantId: String,
    val requestCode: String,             // EXP-{year}-{3-digit}
    val title: String,
    val description: String,
    val amount: Long,
    val category: String,                // utilities | supplies | maintenance | transport | event | salary | tax | rent | other
    val payee: String,
    val status: String,                  // draft | submitted | approved | rejected | disbursed | settled
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
    // TIER 3 R18 FIX: when an expense is settled, the actual spent amount
    // may differ from the requested `amount` (e.g. receipt total was higher
    // or lower than the request). This field captures the final amount
    // confirmed by the proof scan, matching the desktop's behavior.
    val finalSpentAmount: Long? = null,
)
