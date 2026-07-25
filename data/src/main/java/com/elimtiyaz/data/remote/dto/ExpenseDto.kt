package com.elimtiyaz.data.remote.dto

import com.elimtiyaz.domain.model.Expense
import com.elimtiyaz.domain.model.ExpenseCategory
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTO for the `expenses` table — captures the two-tier approval workflow
 * (submit → approve → disburse → settle-proof).
 */
@Serializable
data class ExpenseDto(
    val id: String,
    @SerialName("tenant_id") val tenantId: String,
    @SerialName("request_code") val requestCode: String,
    val title: String,
    val description: String,
    val amount: Double,
    val category: ExpenseCategory,
    val payee: String,
    val status: String,
    @SerialName("submitted_by") val submittedBy: String,
    @SerialName("submitted_at") val submittedAt: String,
    @SerialName("approved_by") val approvedBy: String? = null,
    @SerialName("approved_at") val approvedAt: String? = null,
    @SerialName("approval_note") val approvalNote: String? = null,
    @SerialName("disbursed_by") val disbursedBy: String? = null,
    @SerialName("disbursed_at") val disbursedAt: String? = null,
    @SerialName("proof_url") val proofUrl: String? = null,
    @SerialName("proof_uploaded_by") val proofUploadedBy: String? = null,
    @SerialName("proof_uploaded_at") val proofUploadedAt: String? = null,
    @SerialName("anomaly_score") val anomalyScore: Double? = null,
    @SerialName("anomaly_note") val anomalyNote: String? = null,
) {
    /** Convert to a domain [Expense]. */
    fun toDomain(): Expense = Expense(
        id = id, tenantId = tenantId, requestCode = requestCode, title = title, description = description,
        amount = amount, category = category, payee = payee, status = status, submittedBy = submittedBy,
        submittedAt = submittedAt, approvedBy = approvedBy, approvedAt = approvedAt, approvalNote = approvalNote,
        disbursedBy = disbursedBy, disbursedAt = disbursedAt, proofUrl = proofUrl, proofUploadedBy = proofUploadedBy,
        proofUploadedAt = proofUploadedAt, anomalyScore = anomalyScore, anomalyNote = anomalyNote,
    )

    companion object {
        /** Build a DTO from a domain [Expense]. */
        fun fromDomain(e: Expense): ExpenseDto = ExpenseDto(
            id = e.id, tenantId = e.tenantId, requestCode = e.requestCode, title = e.title,
            description = e.description, amount = e.amount, category = e.category, payee = e.payee,
            status = e.status, submittedBy = e.submittedBy, submittedAt = e.submittedAt, approvedBy = e.approvedBy,
            approvedAt = e.approvedAt, approvalNote = e.approvalNote, disbursedBy = e.disbursedBy,
            disbursedAt = e.disbursedAt, proofUrl = e.proofUrl, proofUploadedBy = e.proofUploadedBy,
            proofUploadedAt = e.proofUploadedAt, anomalyScore = e.anomalyScore, anomalyNote = e.anomalyNote,
        )
    }
}
