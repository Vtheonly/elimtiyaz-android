package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.LedgerEngine
import com.example.core.Result
import com.example.domain.model.DebtSummary
import com.example.domain.model.Installment
import com.example.domain.model.Payment
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.DebtRepository
import com.example.domain.repository.ParentFinancialProfile
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.request.setBody
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of DebtRepository.
 *
 * - `observeSummary()` reads the `mv_debt_aging` materialized view
 *   (migration 0021) and maps each row to a [DebtSummary] domain object.
 * - `observeParentProfile(parentId)` combines four queries (parent info,
 *   all installments, last 20 payments, all ledger entries) and uses
 *   [LedgerEngine.computeParentSummary] to compute totals deterministically.
 * - `sendReminder(parentId)` invokes the `send-debt-reminder` Edge Function
 *   which dispatches SMS + FCM + email to the parent.
 *
 * Audit action: `AuditActions.DEBT_REMINDER_SENT` (`debt.reminder_sent`).
 */
@Singleton
class SupabaseDebtRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : DebtRepository {

    override fun observeSummary() = flow {
        emit(try {
            provider.postgrest.from("mv_debt_aging")
                .select {
                    order("outstanding", Order.DESCENDING)
                    limit(200)
                }
                .decodeList<DebtAgingRowDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override fun observeParentProfile(parentId: String) = flow {
        emit(try {
            fetchParentProfile(parentId)
        } catch (e: Exception) { null })
    }

    override suspend fun sendReminder(parentId: String, actorId: String, actorName: String): Result<Unit> = try {
        require(parentId.isNotBlank()) { "Parent ID is required" }
        val params = buildJsonObject {
            put("parent_id", parentId)
        }
        provider.functions.invoke("send-debt-reminder") {
            setBody(params)
        }
        auditRepository.log(AuditLogInput(
            action = AuditActions.DEBT_REMINDER_SENT,
            entityType = "parent",
            entityId = parentId,
            afterJson = """{"channel":"sms+push+email"}""",
            note = "Debt reminder sent from Android app by $actorName",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    private suspend fun fetchParentProfile(parentId: String): ParentFinancialProfile? {
        // 1) Fetch parent name + phone
        val parentInfo = fetchParentInfo(parentId) ?: return null

        // 2) Fetch all installments for parent
        val installments = fetchInstallments(parentId)

        // 3) Fetch recent payments (last 20)
        val recentPayments = fetchRecentPayments(parentId)

        // 4) Fetch all ledger entries for parent (for deterministic totals)
        val ledgerEntries = provider.postgrest.from("ledger_entries")
            .select {
                filter { eq("parent_id", parentId) }
                order("entry_date", Order.ASCENDING)
                limit(500)
            }
            .decodeList<SupabaseLedgerRepository.LedgerEntryDto>()
            .map { it.toDomain() }

        // 5) Compute totals via LedgerEngine (deterministic replay)
        val summary = LedgerEngine.computeParentSummary(ledgerEntries, parentId, parentInfo.name)

        return ParentFinancialProfile(
            parentId = parentId,
            parentName = parentInfo.name,
            totalDue = summary.totalCharged,
            totalPaid = summary.totalPaid,
            totalOutstanding = summary.totalOutstanding,
            overdueAmount = summary.totalOverdue,
            installments = installments,
            recentPayments = recentPayments,
        )
    }

    private suspend fun fetchParentInfo(parentId: String): ParentInfoDto? = try {
        provider.postgrest.from("parents")
            .select { filter { eq("id", parentId) } }
            .decodeList<ParentInfoDto>()
            .firstOrNull()
    } catch (e: Exception) { null }

    private suspend fun fetchInstallments(parentId: String): List<Installment> = try {
        provider.postgrest.from("installments")
            .select {
                filter { eq("parent_id", parentId) }
                order("due_date", Order.ASCENDING)
                limit(100)
            }
            .decodeList<SupabaseInstallmentRepository.InstallmentDto>()
            .map { it.toDomain() }
    } catch (e: Exception) { emptyList() }

    private suspend fun fetchRecentPayments(parentId: String): List<Payment> = try {
        provider.postgrest.from("payments")
            .select {
                filter { eq("parent_id", parentId) }
                order("collected_at", Order.DESCENDING)
                limit(20)
            }
            .decodeList<SupabasePaymentRepository.PaymentDto>()
            .map { it.toDomain() }
    } catch (e: Exception) { emptyList() }

    @Serializable
    data class DebtAgingRowDto(
        val tenantId: String,
        val parentId: String,
        val parentName: String,
        val agingBucket: String,
        val outstanding: Double = 0.0,
    ) {
        fun toDomain() = DebtSummary(
            parentId = parentId,
            parentName = parentName,
            parentPhone = "", // not in mv_debt_aging view
            studentCount = 0, // not in mv_debt_aging view
            outstandingAmount = outstanding.toLong(),
            daysOverdue = bucketToDays(agingBucket),
            bucket = agingBucket,
        )

        private fun bucketToDays(bucket: String): Long = when (bucket) {
            "0_30" -> 15L
            "31_60" -> 45L
            "61_90" -> 75L
            "91_180" -> 135L
            "180_plus" -> 200L
            // Backward-compat: accept dash format too (older MV snapshots).
            "0-30" -> 15L
            "31-60" -> 45L
            "61-90" -> 75L
            "91-180" -> 135L
            "180+" -> 200L
            else -> 0L
        }
    }

    @Serializable
    data class ParentInfoDto(
        val id: String,
        val firstName: String,
        val lastName: String,
        val primaryPhone: String? = null,
    ) {
        val name: String get() = "$firstName $lastName"
    }
}
