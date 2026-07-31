package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.LedgerEngine
import com.example.core.LedgerEntry
import com.example.core.ParentLedgerSummary
import com.example.core.Result
import com.example.core.Reconcile
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.LedgerRepository
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of LedgerRepository.
 *
 * The `ledger_entries` table is IMMUTABLE — RLS blocks UPDATE and DELETE.
 * The client can only SELECT (filtered by tenant via RLS) and INSERT
 * (via direct insert for simple cases, or via RPCs like `collect_payment`
 * for atomic operations that also touch installments + receipts).
 *
 * Balance computation happens CLIENT-SIDE by replaying fetched entries
 * via [LedgerEngine.computeAccountBalance] / [LedgerEngine.computeParentSummary].
 * This matches the desktop pattern and guarantees the determinism invariant:
 * the same ledger, replayed twice, always yields the same balances.
 */
@Singleton
class SupabaseLedgerRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : LedgerRepository {

    override fun observe(): Flow<List<LedgerEntry>> = flow {
        val rows = fetchAll()
        emit(rows)
    }

    override fun observeByParent(parentId: String) = flow {
        val rows = try {
            provider.postgrest.from("ledger_entries")
                .select {
                    filter { eq("parent_id", parentId) }
                    order("entry_date", Order.ASCENDING)
                    limit(500)
                }
                .decodeList<LedgerEntryDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() }
        emit(rows)
    }

    override fun observeByAccount(accountId: String) = flow {
        val rows = try {
            provider.postgrest.from("ledger_entries")
                .select {
                    filter { eq("account_id", accountId) }
                    order("entry_date", Order.ASCENDING)
                }
                .decodeList<LedgerEntryDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() }
        emit(rows)
    }

    override suspend fun append(entry: LedgerEntry): Result<LedgerEntry> = try {
        val dto = LedgerEntryDto.fromDomain(entry)
        provider.postgrest.from("ledger_entries").insert(dto)
        auditRepository.log(AuditLogInput(
            action = AuditActions.LEDGER_ENTRY_APPEND,
            entityType = "ledger_entry",
            entityId = entry.id,
            afterJson = """{"type":"${entry.type.code}","amount":${entry.amount},"account_id":"${entry.accountId}"}""",
            note = "Ledger entry appended from Android app",
        ))
        Result.Ok(entry)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun appendMany(entries: List<LedgerEntry>): Result<List<LedgerEntry>> = try {
        val dtos = entries.map { LedgerEntryDto.fromDomain(it) }
        provider.postgrest.from("ledger_entries").insert(dtos)
        auditRepository.log(AuditLogInput(
            action = AuditActions.LEDGER_ENTRY_APPEND_MANY,
            entityType = "ledger_entry",
            entityId = "batch",
            afterJson = """{"count":${entries.size}}""",
            note = "Batch ledger entry append from Android app",
        ))
        Result.Ok(entries)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun reverse(originalId: String, reason: String, actorId: String, actorName: String): Result<LedgerEntry> = try {
        // Fetch the original
        val original = provider.postgrest.from("ledger_entries")
            .select { filter { eq("id", originalId) } }
            .decodeList<LedgerEntryDto>()
            .firstOrNull()
            ?.toDomain()
            ?: return Result.Err(Errors.notFound("Ledger entry $originalId not found"))

        val reversal = LedgerEngine.createReversalEntry(original, reason, actorId, actorName)
        val dto = LedgerEntryDto.fromDomain(reversal)
        provider.postgrest.from("ledger_entries").insert(dto)

        auditRepository.log(AuditLogInput(
            action = AuditActions.LEDGER_ENTRY_REVERSE,
            entityType = "ledger_entry",
            entityId = reversal.id,
            afterJson = """{"reverses_id":"$originalId","amount":${reversal.amount},"reason":"$reason"}""",
            note = "Ledger entry reversed from Android app",
        ))

        Result.Ok(reversal)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun summary(parentId: String): Result<ParentLedgerSummary> = try {
        val entries = provider.postgrest.from("ledger_entries")
            .select { filter { eq("parent_id", parentId) } }
            .decodeList<LedgerEntryDto>()
            .map { it.toDomain() }
        // Fetch parent name (best-effort)
        val parentName = try {
            provider.postgrest.from("parents")
                .select { filter { eq("id", parentId) } }
                .decodeList<Map<String, String?>>()
                .firstOrNull()
                ?.let { "${it["first_name"] ?: ""} ${it["last_name"] ?: ""}".trim() }
                ?: parentId
        } catch (e: Exception) { parentId }

        val summary = LedgerEngine.computeParentSummary(entries, parentId, parentName)
        Result.Ok(summary)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun reconcile(): Result<Reconcile.Report> = try {
        val entries = fetchAll()
        val report = Reconcile.reconcileLedger(entries)
        auditRepository.log(AuditLogInput(
            action = AuditActions.LEDGER_RECONCILE,
            entityType = "ledger",
            entityId = "all",
            afterJson = """{"passed":${report.passed},"errors":${report.errorCount},"warnings":${report.warningCount}}""",
            note = "Reconciliation run from Android app",
        ))
        Result.Ok(report)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    private suspend fun fetchAll(): List<LedgerEntry> = try {
        provider.postgrest.from("ledger_entries")
            .select {
                order("entry_date", Order.ASCENDING)
                limit(1000)
            }
            .decodeList<LedgerEntryDto>()
            .map { it.toDomain() }
    } catch (e: Exception) { emptyList() }

    @Serializable
    data class LedgerEntryDto(
        val id: String,
        val tenantId: String,
        val accountId: String,
        val parentId: String,
        val studentId: String? = null,
        val category: String,
        val amount: Long,
        val type: String,
        val sourceType: String,
        val sourceId: String,
        val method: String? = null,
        val receiptNumber: String? = null,
        val paymentStatus: String? = null,
        val reversesId: String? = null,
        val description: String,
        val actorId: String,
        val actorName: String,
        val entryDate: String,
        val metadata: Map<String, String>? = null,
    ) {
        fun toDomain() = LedgerEntry(
            id = id, tenantId = tenantId, accountId = accountId,
            parentId = parentId, studentId = studentId,
            category = com.example.core.PaymentCategory.fromCode(category) ?: com.example.core.PaymentCategory.OTHER,
            amount = amount,
            type = com.example.core.LedgerEntryType.fromCode(type),
            sourceType = com.example.core.LedgerSourceType.fromCode(sourceType),
            sourceId = sourceId,
            method = method?.let { com.example.core.PaymentMethod.fromCode(it) },
            receiptNumber = receiptNumber,
            paymentStatus = paymentStatus?.let { com.example.core.PaymentStatus.fromCode(it) },
            reversesId = reversesId,
            description = description,
            actorId = actorId, actorName = actorName,
            at = entryDate,
            metadata = metadata ?: emptyMap(),
        )

        companion object {
            fun fromDomain(e: LedgerEntry) = LedgerEntryDto(
                id = e.id, tenantId = e.tenantId, accountId = e.accountId,
                parentId = e.parentId, studentId = e.studentId,
                category = e.category.code, amount = e.amount,
                type = e.type.code, sourceType = e.sourceType.code, sourceId = e.sourceId,
                method = e.method?.code, receiptNumber = e.receiptNumber,
                paymentStatus = e.paymentStatus?.code, reversesId = e.reversesId,
                description = e.description, actorId = e.actorId, actorName = e.actorName,
                entryDate = e.at,
                metadata = e.metadata.mapValues { it.value?.toString() },
            )
        }
    }
}
