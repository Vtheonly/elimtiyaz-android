package com.example.infrastructure.supabase

import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.CreateStudentInput
import com.example.domain.repository.UpdateParentInput
import com.example.domain.repository.UpdateStudentInput
import com.example.infrastructure.room.ParentEntity
import com.example.infrastructure.room.StudentEntity
import com.example.infrastructure.room.PaymentEntity
import com.example.infrastructure.room.LedgerEntryEntity
import com.example.infrastructure.room.InstallmentEntity
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Supabase write-through helpers.
 *
 * These functions take a locally-built Room entity (which the Local*Repository
 * classes already construct) and INSERT / UPDATE / DELETE the corresponding
 * row in the real Supabase `parents`, `students`, `payments`, `ledger_entries`,
 * and `installments` tables.
 *
 * Strategy:
 *   - The Local*Repository builds the entity (generating IDs, codes, timestamps).
 *   - It calls the Supabase write FIRST.
 *   - If the Supabase write succeeds, the entity is upserted into Room so the
 *     UI's Flow observables re-emit immediately.
 *   - If the Supabase write fails, the Room write is NOT performed and the
 *     caller returns `Result.Err` so the UI shows the real network error.
 *
 * The Supabase client uses ONLY the anon/publishable key (RLS-enforced). The
 * service_role key is never embedded in the APK.
 */
object SupabaseWriteThrough {

    /** Map a [ParentEntity] to the JSON payload expected by the `parents` table. */
    fun parentEntityToPayload(e: ParentEntity) = buildJsonObject {
        put("id", e.id)
        put("tenant_id", e.tenantId)
        put("parent_code", e.code)
        put("first_name", e.firstName)
        put("last_name", e.lastName)
        e.displayName?.let { put("display_name", it) }
        put("primary_phone", e.phone)
        e.whatsapp?.let { put("secondary_phone", it) }
        e.email?.let { put("email", it) }
        e.occupation?.let { put("occupation", it) }
        e.address?.let { put("address", it) }
        e.transportDestination?.let { put("transport_destination", it) }
        put("is_active", e.isActive)
        put("is_financially_restricted", e.isFinanciallyRestricted)
        put("created_at", e.createdAt)
        put("updated_at", e.updatedAt)
    }

    /** Map a [StudentEntity] to the JSON payload expected by the `students` table. */
    fun studentEntityToPayload(e: StudentEntity) = buildJsonObject {
        put("id", e.id)
        put("tenant_id", e.tenantId)
        put("student_code", e.code)
        put("parent_id", e.parentId)
        put("first_name", e.firstName)
        e.lastName.takeIf { it.isNotBlank() }?.let { put("last_name", it) }
        e.displayName?.let { put("display_name", it) }
        if (e.gender.isNotBlank() && e.gender != "unspecified") put("gender", e.gender)
        if (e.birthDate.isNotBlank()) put("date_of_birth", e.birthDate)
        if (e.enrollmentDate.isNotBlank()) put("enrollment_date", e.enrollmentDate)
        put("grade_level_code", e.gradeLevel)
        e.classId?.let { put("class_id", it) }
        e.medicalNotes?.let { put("medical_notes", it) }
        put("enrollment_status", e.status)
        put("is_active", e.status == "active")
        put("created_at", e.createdAt)
        put("updated_at", e.updatedAt)
    }

    /** Map a [PaymentEntity] to the JSON payload expected by the `payments` table. */
    fun paymentEntityToPayload(e: PaymentEntity) = buildJsonObject {
        put("id", e.id)
        put("tenant_id", e.tenantId)
        put("payment_number", e.receiptNumber)
        put("receipt_number", e.receiptNumber)
        put("parent_id", e.parentId)
        e.studentId?.let { put("student_id", it) }
        put("amount", e.amount / 100.0) // centimes → currency
        put("method", e.method)
        put("category", e.category)
        put("status", e.status)
        e.installmentId?.let { put("installment_id", it) }
        e.proofUrl?.let { put("proof_path", it) }
        e.checkNumber?.let { put("check_number", it) }
        e.checkBankName?.let { put("check_bank_name", it) }
        e.checkIssueDate?.let { put("check_issue_date", it) }
        e.checkClearanceDate?.let { put("check_clearance_date", it) }
        e.transferReference?.let { put("transfer_reference", it) }
        e.transferSourceBank?.let { put("transfer_source_bank", it) }
        e.notes?.let { put("notes", it) }
        put("collected_by", e.collectedBy)
        if (e.collectedAt.isNotBlank()) put("collected_at", e.collectedAt)
        put("created_at", e.createdAt)
        put("updated_at", e.updatedAt)
    }

    /** Map a [LedgerEntryEntity] to the JSON payload expected by the `ledger_entries` table. */
    fun ledgerEntryEntityToPayload(e: LedgerEntryEntity) = buildJsonObject {
        put("id", e.id)
        put("tenant_id", e.tenantId)
        put("account_id", e.accountId)
        put("parent_id", e.parentId)
        e.studentId?.let { put("student_id", it) }
        put("entry_type", e.type)
        put("amount", e.amount / 100.0) // centimes → currency
        put("category", e.category)
        e.description?.let { put("description", it) }
        e.sourceType?.let { put("source_type", it) }
        e.sourceId?.let { put("source_id", it) }
        e.method?.let { put("method", it) }
        e.receiptNumber?.let { put("receipt_number", it) }
        e.paymentStatus?.let { put("payment_status", it) }
        e.reversesId?.let { put("reverses_id", it) }
        put("actor_id", e.actorId)
        put("actor_name", e.actorName)
        put("at", e.at)
        put("created_at", e.at)
    }

    /** Map an [InstallmentEntity] to the JSON payload expected by the `installments` table. */
    fun installmentEntityToPayload(e: InstallmentEntity) = buildJsonObject {
        put("id", e.id)
        put("tenant_id", e.tenantId)
        put("parent_id", e.parentId)
        e.studentId?.let { put("student_id", it) }
        put("category", e.category)
        e.label.let { put("label", it) }
        put("amount_due", e.amountDue / 100.0) // centimes → currency
        put("amount_paid", e.amountPaid / 100.0)
        put("amount_pending", e.amountPending / 100.0)
        put("due_date", e.dueDate)
        e.paidDate?.let { put("paid_date", it) }
        put("status", e.status)
        e.academicCycle?.let { put("academic_cycle", it) }
        put("created_at", e.createdAt)
        put("updated_at", e.updatedAt)
    }
}
