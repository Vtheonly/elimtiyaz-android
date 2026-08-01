package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.PaymentCategory
import com.example.core.PaymentStatus
import com.example.core.Result
import com.example.domain.model.Installment
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.InstallmentRepository
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of InstallmentRepository.
 *
 * Table: `installments` (migration 0007). The DB trigger
 * `update_installment_status` auto-recomputes `status` from `amount_paid`
 * and `due_date` on every UPDATE, so the client never sets status directly.
 *
 * Mutations:
 *   - `markPaid(id)`           → RPC `mark_installment_paid(p_id)` (atomic:
 *        sets amount_paid = amount_due, paid_date = today, status = 'paid',
 *        appends ledger entry, generates receipt).
 *   - `updateDueDate(id, ...)` → UPDATE due_date + set is_custom_schedule=true
 *        + custom_schedule_note. Trigger recomputes overdue status.
 *   - `regenerateForCycle(parentId, cycle)` → RPC `regenerate_installments`
 *        deletes future unpaid installments and regenerates from pricing_config.
 *   - `findOverdue()` → SELECT WHERE status='overdue' OR
 *        (due_date < current_date AND amount_paid < amount_due).
 *
 * Audit actions:
 *   - INSTALLMENT_MARK_PAID / INSTALLMENT_RESCHEDULE /
 *     INSTALLMENT_REGENERATE / INSTALLMENT_FIND_OVERDUE
 */
@Singleton
class SupabaseInstallmentRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : InstallmentRepository {

    override fun observeByParent(parentId: String) = flow {
        emit(try {
            provider.postgrest.from("installments")
                .select {
                    filter { eq("parent_id", parentId) }
                    order("due_date", Order.ASCENDING)
                    limit(200)
                }
                .decodeList<InstallmentDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override fun observeByStudent(studentId: String) = flow {
        emit(try {
            provider.postgrest.from("installments")
                .select {
                    filter { eq("student_id", studentId) }
                    order("due_date", Order.ASCENDING)
                    limit(200)
                }
                .decodeList<InstallmentDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override fun observeById(id: String) = flow {
        emit(try {
            provider.postgrest.from("installments")
                .select { filter { eq("id", id) } }
                .decodeList<InstallmentDto>()
                .firstOrNull()
                ?.toDomain()
        } catch (e: Exception) { null })
    }

    override suspend fun markPaid(id: String, actorId: String, actorName: String): Result<Installment> = try {
        val params = buildJsonObject { put("p_id", id) }
        provider.postgrest.rpc("mark_installment_paid", params)
        val updated = fetchById(id)
            ?: return Result.Err(Errors.notFound("Installment $id not found after mark_paid"))
        auditRepository.log(AuditLogInput(
            action = AuditActions.INSTALLMENT_MARK_PAID,
            entityType = "installment",
            entityId = id,
            afterJson = """{"amount_due":${updated.amountDue},"amount_paid":${updated.amountPaid},"status":"${updated.status.code}"}""",
            note = "Installment marked paid from Android app",
        ))
        Result.Ok(updated)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun updateDueDate(id: String, dueDate: String, note: String?, actorId: String, actorName: String): Result<Installment> = try {
        require(dueDate.isNotBlank()) { "Due date is required" }
        val updates = mutableMapOf<String, String>(
            "due_date" to dueDate,
            "is_custom_schedule" to "true",
        )
        if (note != null) updates["custom_schedule_note"] = note
        provider.postgrest.from("installments").update(updates) {
            filter { eq("id", id) }
            select()
        }.decodeList<InstallmentDto>().first()
        val updated = fetchById(id)
            ?: return Result.Err(Errors.notFound("Installment $id not found after reschedule"))
        auditRepository.log(AuditLogInput(
            action = AuditActions.INSTALLMENT_RESCHEDULE,
            entityType = "installment",
            entityId = id,
            afterJson = """{"due_date":"$dueDate","custom_schedule":true,"note":"${note ?: ""}"}""",
            note = "Installment due date rescheduled from Android app",
        ))
        Result.Ok(updated)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun regenerateForCycle(parentId: String, cycle: String, actorId: String, actorName: String): Result<List<Installment>> = try {
        require(parentId.isNotBlank()) { "Parent ID is required" }
        require(cycle.isNotBlank()) { "Cycle is required" }
        val params = buildJsonObject {
            put("p_parent_id", parentId)
            put("p_cycle", cycle)
        }
        provider.postgrest.rpc("regenerate_installments", params)
        // Fetch back the regenerated installments for this parent
        val rows = provider.postgrest.from("installments")
            .select {
                filter {
                    eq("parent_id", parentId)
                    eq("academic_cycle", cycle)
                }
                order("due_date", Order.ASCENDING)
                limit(50)
            }
            .decodeList<InstallmentDto>()
            .map { it.toDomain() }
        auditRepository.log(AuditLogInput(
            action = AuditActions.INSTALLMENT_REGENERATE,
            entityType = "installment",
            entityId = parentId,
            afterJson = """{"cycle":"$cycle","generated_count":${rows.size}}""",
            note = "Installments regenerated for cycle from Android app",
        ))
        Result.Ok(rows)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun findOverdue(): Result<List<Installment>> = try {
        val rows = provider.postgrest.from("installments")
            .select {
                filter {
                    or {
                        eq("status", "overdue")
                        and {
                            lt("due_date", java.time.LocalDate.now().toString())
                            lt("amount_paid", "amount_due") // Postgrest may not support column-to-column comparison directly
                        }
                    }
                }
                order("due_date", Order.ASCENDING)
                limit(500)
            }
            .decodeList<InstallmentDto>()
            .filter { dto ->
                dto.status == "overdue" ||
                    (dto.dueDate < java.time.LocalDate.now().toString() && dto.amountPaid < dto.amountDue)
            }
            .map { it.toDomain() }
        auditRepository.log(AuditLogInput(
            action = AuditActions.INSTALLMENT_FIND_OVERDUE,
            entityType = "installment",
            entityId = "all",
            afterJson = """{"overdue_count":${rows.size}}""",
            note = "Overdue scan from Android app",
        ))
        Result.Ok(rows)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    private suspend fun fetchById(id: String): Installment? = try {
        provider.postgrest.from("installments")
            .select { filter { eq("id", id) } }
            .decodeList<InstallmentDto>()
            .firstOrNull()
            ?.toDomain()
    } catch (e: Exception) { null }

    @Serializable
    data class InstallmentDto(
        val id: String,
        val tenantId: String,
        val parentId: String,
        val studentId: String? = null,
        val serviceEnrollmentId: String? = null,
        val invoiceId: String? = null,
        val trancheNumber: Int = 1,
        val amountDue: Long = 0L,
        val amountPaid: Long = 0L,
        val dueDate: String,
        val paidDate: String? = null,
        val status: String = "unpaid",
        val academicCycle: String? = null,
        val isCustomSchedule: Boolean = false,
        val customScheduleNote: String? = null,
    ) {
        fun toDomain() = Installment(
            id = id,
            tenantId = tenantId,
            parentId = parentId,
            studentId = studentId,
            category = PaymentCategory.OTHER, // DB does not store category on installments directly
            label = "Tranche $trancheNumber",
            amountDue = amountDue,
            amountPaid = amountPaid,
            dueDate = dueDate,
            paidDate = paidDate,
            status = PaymentStatus.fromCode(status) ?: PaymentStatus.PARTIAL,
            academicCycle = academicCycle,
            customSchedule = isCustomSchedule,
            customScheduleNote = customScheduleNote,
        )
    }
}
