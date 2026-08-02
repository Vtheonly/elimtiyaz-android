package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.LedgerEngine
import com.example.core.LedgerEntry
import com.example.core.ParentLedgerSummary
import com.example.core.Result
import com.example.core.Reconcile
import com.example.core.createReversalEntry
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.LedgerRepository
import com.example.infrastructure.room.toCacheEntity
import com.example.infrastructure.room.toDomain
import com.example.infrastructure.sync.SyncSupport
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
    private val syncSupport: SyncSupport,
) : LedgerRepository {

    /**
     * Cache-then-network: emit cached entries instantly, then fetch from
     * Supabase, refresh cache, emit again. Offline → cache only.
     *
     * Ledger entries are immutable (RLS blocks UPDATE/DELETE), so the cache is
     * only ever appended-to via [upsertLedger] (REPLACE on conflict by PK).
     */
    override fun observe(): Flow<List<LedgerEntry>> = syncSupport.cacheThenNetwork(
        cacheRead = {
            syncSupport.listCachedLedger().map { it.toDomain() }
        },
        cacheWrite = { entries: List<LedgerEntry> ->
            syncSupport.upsertLedger(entries.map { it.toCacheEntity() })
        },
        fetch = { fetchAll() },
    )

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

    override suspend fun append(entry: LedgerEntry): Result<LedgerEntry> {
        val dto = LedgerEntryDto.fromDomain(entry)
        // Try direct insert; on offline, enqueue for sync (insert semantics —
        // ledger_entries is immutable server-side: RLS blocks UPDATE/DELETE).
        return syncSupport.tryThenEnqueue(
            entity = "ledger_entry",
            operation = "append",
            payload = {
                syncSupport.json().encodeToString(LedgerEntryDto.serializer(), dto)
            },
            sourceScreen = "LedgerDetail",
        ) {
            provider.postgrest.from("ledger_entries").insert(dto)
            auditRepository.log(AuditLogInput(
                action = AuditActions.LEDGER_ENTRY_APPEND,
                entityType = "ledger_entry",
                entityId = entry.id,
                afterJson = """{"type":"${entry.type.code}","amount":${entry.amount},"account_id":"${entry.accountId}"}""",
                note = "Ledger entry appended from Android app",
            ))
            // Persist to cache so the next observe() emits it instantly.
            syncSupport.upsertLedger(listOf(entry.toCacheEntity()))
            entry
        }
    }

    override suspend fun appendMany(entries: List<LedgerEntry>): Result<List<LedgerEntry>> {
        val dtos = entries.map { LedgerEntryDto.fromDomain(it) }
        // Try direct bulk insert; on offline, enqueue the batch as a single
        // sync entry (drain-side replays via direct table insert).
        return syncSupport.tryThenEnqueue(
            entity = "ledger_entry",
            operation = "append_many",
            payload = {
                syncSupport.json().encodeToString(
                    kotlinx.serialization.builtins.ListSerializer(LedgerEntryDto.serializer()),
                    dtos,
                )
            },
            sourceScreen = "LedgerDetail",
        ) {
            provider.postgrest.from("ledger_entries").insert(dtos)
            auditRepository.log(AuditLogInput(
                action = AuditActions.LEDGER_ENTRY_APPEND_MANY,
                entityType = "ledger_entry",
                entityId = "batch",
                afterJson = """{"count":${entries.size}}""",
                note = "Batch ledger entry append from Android app",
            ))
            // Persist batch to cache.
            syncSupport.upsertLedger(entries.map { it.toCacheEntity() })
            entries
        }
    }

    override suspend fun reverse(originalId: String, reason: String, actorId: String, actorName: String): Result<LedgerEntry> {
        // Try direct fetch + insert reversal; on offline, enqueue with a
        // payload that captures the originalId + reason (drain-side replay
        // is best-effort — the audit failure log surfaces drain failures).
        return syncSupport.tryThenEnqueue(
            entity = "ledger_entry",
            operation = "reverse",
            payload = {
                syncSupport.json().encodeToString(
                    LedgerReversePayload.serializer(),
                    LedgerReversePayload(originalId, reason, actorId, actorName),
                )
            },
            sourceScreen = "LedgerDetail",
        ) {
            // Fetch the original (throws if not found — caught by tryThenEnqueue
            // but NOT enqueued because it's a 404, not a network error).
            val original = provider.postgrest.from("ledger_entries")
                .select { filter { eq("id", originalId) } }
                .decodeList<LedgerEntryDto>()
                .firstOrNull()
                ?.toDomain()
                ?: error("Ledger entry $originalId not found")

            val reversal = createReversalEntry(original, reason, actorId, actorName)
            val dto = LedgerEntryDto.fromDomain(reversal)
            provider.postgrest.from("ledger_entries").insert(dto)

            auditRepository.log(AuditLogInput(
                action = AuditActions.LEDGER_ENTRY_REVERSE,
                entityType = "ledger_entry",
                entityId = reversal.id,
                afterJson = """{"reverses_id":"$originalId","amount":${reversal.amount},"reason":"$reason"}""",
                note = "Ledger entry reversed from Android app",
            ))

            // Persist reversal to cache.
            syncSupport.upsertLedger(listOf(reversal.toCacheEntity()))
            reversal
        }
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
                metadata = e.metadata.mapValues { it.value?.toString() ?: "" },
            )
        }
    }

    /**
     * Minimal payload captured when a `reverse` operation is enqueued offline.
     * The drain-side replay (SupabaseSyncDao) is best-effort: a full replay
     * would require re-fetching the original entry server-side, which is not
     * currently implemented. The audit failure log surfaces permanent drain
     * failures after [SyncService.maxAttempts] retries.
     */
    @Serializable
    data class LedgerReversePayload(
        val originalId: String,
        val reason: String,
        val actorId: String,
        val actorName: String,
    )
}
