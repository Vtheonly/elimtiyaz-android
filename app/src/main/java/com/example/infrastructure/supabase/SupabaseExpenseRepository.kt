package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.Expense
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.SubmitExpenseInput
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of ExpenseRepository.
 *
 * State machine (enforced server-side by `enforce_expense_workflow_rules` trigger):
 *   draft → submitted → {approved | rejected} → disbursed → settled
 *
 * No-self-approval is enforced server-side: approve() and reject() reject
 * if submittedBy === approver. The trigger raises an exception.
 */
@Singleton
class SupabaseExpenseRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : ExpenseRepository {

    override fun observe() = flow { emit(fetchAll()) }

    override fun observeByStatus(status: String) = flow {
        emit(try {
            provider.postgrest.from("expense_tickets")
                .select {
                    filter { eq("status", status) }
                    order("submitted_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<ExpenseDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override fun observeById(id: String) = flow {
        emit(try {
            provider.postgrest.from("expense_tickets")
                .select { filter { eq("id", id) } }
                .decodeList<ExpenseDto>()
                .firstOrNull()
                ?.toDomain()
        } catch (e: Exception) { null })
    }

    override suspend fun submit(input: SubmitExpenseInput, actorId: String, actorName: String): Result<Expense> {
        return try {
            val dto = ExpenseInsertDto(
                title = input.title, description = input.description,
                amount = input.amount, category = input.category, payee = input.payee,
                urgency = input.urgency, submittedBy = actorId,
            )
            val inserted = provider.postgrest.from("expense_tickets").insert(dto) { select() }.decodeList<ExpenseDto>().first()
            val expense = inserted.toDomain()
            auditRepository.log(AuditLogInput(
                action = AuditActions.EXPENSE_SUBMIT,
                entityType = "expense_ticket",
                entityId = expense.id,
                afterJson = """{"code":"${expense.requestCode}","amount":${expense.amount},"title":"${expense.title}"}""",
                note = "Expense submitted from Android app",
            ))
            Result.Ok(expense)
        } catch (e: Exception) {
            Result.Err(Errors.fromException(e))
        }
    }

    override suspend fun approve(id: String, note: String, actorId: String, actorName: String): Result<Expense> {
        return try {
            if (note.isBlank()) return Result.Err(Errors.validation("Approval note is required"))
            val params = buildJsonObject {
                put("p_ticket_id", id)
                put("p_approver_profile_id", actorId)
                put("p_note", note)
            }
            provider.postgrest.rpc("approve_expense", params)
            val updated = fetchById(id) ?: return Result.Err(Errors.notFound("Expense $id not found after approve"))
            auditRepository.log(AuditLogInput(
                action = AuditActions.EXPENSE_APPROVE,
                entityType = "expense_ticket",
                entityId = id,
                afterJson = """{"status":"approved","note":"$note"}""",
                note = "Expense approved from Android app",
            ))
            Result.Ok(updated)
        } catch (e: Exception) {
            Result.Err(Errors.fromException(e))
        }
    }

    override suspend fun reject(id: String, reason: String, actorId: String, actorName: String): Result<Expense> {
        return try {
            if (reason.isBlank()) return Result.Err(Errors.validation("Rejection reason is required"))
            val params = buildJsonObject {
                put("p_ticket_id", id)
                put("p_approver_profile_id", actorId)
                put("p_reason", reason)
            }
            provider.postgrest.rpc("reject_expense", params)
            val updated = fetchById(id) ?: return Result.Err(Errors.notFound("Expense $id not found after reject"))
            auditRepository.log(AuditLogInput(
                action = AuditActions.EXPENSE_REJECT,
                entityType = "expense_ticket",
                entityId = id,
                afterJson = """{"status":"rejected","reason":"$reason"}""",
                note = "Expense rejected from Android app",
            ))
            Result.Ok(updated)
        } catch (e: Exception) {
            Result.Err(Errors.fromException(e))
        }
    }

    override suspend fun disburse(id: String, actorId: String, actorName: String): Result<Expense> {
        return try {
            val params = buildJsonObject {
                put("p_ticket_id", id)
                put("p_disburser_profile_id", actorId)
            }
            provider.postgrest.rpc("disburse_expense", params)
            val updated = fetchById(id) ?: return Result.Err(Errors.notFound("Expense $id not found after disburse"))
            auditRepository.log(AuditLogInput(
                action = AuditActions.EXPENSE_DISBURSE,
                entityType = "expense_ticket",
                entityId = id,
                afterJson = """{"status":"disbursed"}""",
                note = "Expense disbursed from Android app",
            ))
            Result.Ok(updated)
        } catch (e: Exception) {
            Result.Err(Errors.fromException(e))
        }
    }

    override suspend fun settleProof(id: String, proofPath: String, finalAmount: Long, actorId: String, actorName: String): Result<Expense> {
        return try {
            val params = buildJsonObject {
                put("p_ticket_id", id)
                put("p_proof_path", proofPath)
                put("p_final_amount", finalAmount)
                put("p_settler_profile_id", actorId)
            }
            provider.postgrest.rpc("settle_expense", params)
            val updated = fetchById(id) ?: return Result.Err(Errors.notFound("Expense $id not found after settle"))
            auditRepository.log(AuditLogInput(
                action = AuditActions.EXPENSE_SETTLE,
                entityType = "expense_ticket",
                entityId = id,
                afterJson = """{"status":"settled","final_amount":$finalAmount,"proof":"$proofPath"}""",
                note = "Expense settled with proof from Android app",
            ))
            Result.Ok(updated)
        } catch (e: Exception) {
            Result.Err(Errors.fromException(e))
        }
    }

    private suspend fun fetchAll(): List<Expense> = try {
        provider.postgrest.from("expense_tickets")
            .select { order("submitted_at", Order.DESCENDING); limit(100) }
            .decodeList<ExpenseDto>()
            .map { it.toDomain() }
    } catch (e: Exception) { emptyList() }

    private suspend fun fetchById(id: String): Expense? = try {
        provider.postgrest.from("expense_tickets")
            .select { filter { eq("id", id) } }
            .decodeList<ExpenseDto>()
            .firstOrNull()
            ?.toDomain()
    } catch (e: Exception) { null }

    @Serializable
    data class ExpenseDto(
        val id: String, val tenantId: String, val requestCode: String,
        val title: String, val description: String, val amount: Long,
        val category: String, val payee: String, val status: String,
        val submittedBy: String, val submittedAt: String,
        val approvedBy: String? = null, val approvedAt: String? = null,
        val approvalNote: String? = null,
        val disbursedBy: String? = null, val disbursedAt: String? = null,
        val proofUrl: String? = null, val proofUploadedBy: String? = null, val proofUploadedAt: String? = null,
        val anomalyScore: Double? = null, val anomalyNote: String? = null,
    ) {
        fun toDomain() = Expense(
            id = id, tenantId = tenantId, requestCode = requestCode,
            title = title, description = description, amount = amount,
            category = category, payee = payee, status = status,
            submittedBy = submittedBy, submittedAt = submittedAt,
            approvedBy = approvedBy, approvedAt = approvedAt, approvalNote = approvalNote,
            disbursedBy = disbursedBy, disbursedAt = disbursedAt,
            proofUrl = proofUrl, proofUploadedBy = proofUploadedBy, proofUploadedAt = proofUploadedAt,
            anomalyScore = anomalyScore, anomalyNote = anomalyNote,
        )
    }

    @Serializable
    data class ExpenseInsertDto(
        val title: String, val description: String, val amount: Long,
        val category: String, val payee: String, val urgency: String = "normal",
        val submittedBy: String,
    )
}
