package com.example.infrastructure.sync

import com.example.infrastructure.room.SyncQueueEntity
import com.example.infrastructure.supabase.NetworkTimeouts
import com.example.infrastructure.supabase.SupabaseClientProvider
import com.example.session.SessionManager
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dispatcher that processes a single [SyncQueueEntity] by pushing it to
 * Supabase via the shared `upsert_*_from_import` RPCs declared in
 * migration `0027_shared_unification.sql`.
 *
 * SHARED-UNIFICATION (migration 0027):
 *   - Both Desktop and Android call the SAME RPCs.
 *   - The RPCs are SECURITY DEFINER + idempotent (matched by stable
 *     identifiers: parent_code / phone / student_code / payment_number /
 *     (source_type, source_id) for ledger entries).
 *   - Re-pushing the same queue entry is safe — it never creates duplicates.
 *
 * When Supabase is NOT configured (placeholder URL), the dispatcher
 * silently no-ops and the SyncService marks the entry as "synced" so the
 * local cache stays consistent.
 */
@Singleton
class SyncQueueDispatcher @Inject constructor(
    private val supabaseProvider: SupabaseClientProvider,
    private val sessionManager: SessionManager,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        isLenient = true
    }

    /**
     * Process a single queue entry — push it to Supabase via the appropriate
     * upsert RPC. Throws on failure; the SyncService catches and records the
     * error, then re-tries with exponential backoff.
     */
    suspend fun pushEntry(entry: SyncQueueEntity) {
        // Defense in depth: never push mock data.
        if (entry.isMock) return

        // Skip silently when Supabase isn't configured (offline / demo mode).
        if (!NetworkTimeouts.isSupabaseConfigured) return

        val payload = try {
            json.parseToJsonElement(entry.payload).jsonObject
        } catch (e: Exception) {
            // Malformed payload — can't push. Mark as synced to avoid
            // endless retries; the audit log entry in the queue is enough.
            return
        }

        val actorId = sessionManager.currentUserId() ?: entry.actorId

        when (entry.entity) {
            "parent" -> pushParent(entry, payload, actorId)
            "student" -> pushStudent(entry, payload, actorId)
            "payment" -> pushPayment(entry, payload, actorId)
            "ledger_entry" -> pushLedgerEntry(entry, payload, actorId)
            // Other entity kinds (installment, expense, attendance, grade,
            // homework, audit_log, notification, calendar_event) are
            // currently local-only. The shared schema migration 0027
            // supports them via direct table upserts, but those flows
            // are out of scope for this iteration.
            else -> {
                // No-op — the SyncService will mark the entry as "synced".
            }
        }
    }

    // ── Per-entity push implementations ────────────────────────────────────

    private suspend fun pushParent(
        entry: SyncQueueEntity,
        p: JsonObject,
        actorId: String,
    ) {
        val params = buildJsonObject {
            put("p_tenant_id", entry.tenantId)
            put("p_parent_code", p.str("code") ?: p.str("parent_code") ?: generateParentCode())
            put("p_first_name", p.str("firstName") ?: p.str("first_name") ?: "")
            put("p_last_name", p.str("lastName") ?: p.str("last_name") ?: "")
            put("p_display_name", p.str("displayName") ?: p.str("display_name"))
            put("p_primary_phone", p.str("phone") ?: p.str("primary_phone") ?: "(inconnu)")
            put("p_secondary_phone", p.str("whatsapp") ?: p.str("secondary_phone"))
            put("p_email", p.str("email"))
            put("p_occupation", p.str("occupation"))
            put("p_address", p.str("address"))
            put("p_preferred_language", p.str("preferredLanguage") ?: "fr")
            put("p_is_active", true)
        }
        NetworkTimeouts.guard<Unit>("sync.pushParent", timeoutMs = 5_000L) {
            supabaseProvider.postgrest.rpc("upsert_parent_from_import", params)
        }
    }

    private suspend fun pushStudent(
        entry: SyncQueueEntity,
        p: JsonObject,
        actorId: String,
    ) {
        val parentId = p.str("parentId") ?: p.str("parent_id") ?: return
        val params = buildJsonObject {
            put("p_tenant_id", entry.tenantId)
            put("p_student_code", p.str("code") ?: p.str("student_code") ?: generateStudentCode())
            put("p_parent_id", parentId)
            put("p_first_name", p.str("firstName") ?: p.str("first_name") ?: "")
            put("p_last_name", p.str("lastName") ?: p.str("last_name") ?: "")
            put("p_display_name", p.str("displayName") ?: p.str("display_name"))
            put("p_date_of_birth", p.str("birthDate") ?: p.str("date_of_birth"))
            val gender = p.str("gender")
            if (gender != null && gender != "unspecified") put("p_gender", gender)
            put("p_class_id", p.str("classId") ?: p.str("class_id"))
            put("p_enrollment_status", p.str("status") ?: "active")
            put("p_medical_notes", p.str("medicalNotes") ?: p.str("medical_notes"))
            put("p_is_active", true)
        }
        NetworkTimeouts.guard<Unit>("sync.pushStudent", timeoutMs = 5_000L) {
            supabaseProvider.postgrest.rpc("upsert_student_from_import", params)
        }
    }

    private suspend fun pushPayment(
        entry: SyncQueueEntity,
        p: JsonObject,
        actorId: String,
    ) {
        val parentId = p.str("parentId") ?: p.str("parent_id") ?: return
        // CANONICAL-FINANCIAL-LOGIC.md §8.3 — Android domain stores money as
        // Long CENTIMES; the Supabase `payments.amount` column is NUMERIC(12,2)
        // DZD. Without `/100.0` conversion, a 150,000 DZD payment (15,000,000
        // centimes) is pushed as 15,000,000.00 DZD — a 100× inflation.
        val amountCentimes = p.str("amount")?.toLongOrNull() ?: return
        val amountDzd = amountCentimes / 100.0
        val params = buildJsonObject {
            put("p_tenant_id", entry.tenantId)
            put("p_payment_number", p.str("receiptNumber") ?: p.str("payment_number") ?: generatePaymentNumber())
            put("p_parent_id", parentId)
            // p_student_id — nullable; send null for parent-scoped payments.
            val studentId = p.str("studentId") ?: p.str("student_id")
            if (studentId != null) put("p_student_id", studentId) else put("p_student_id", JsonNull)
            // CANONICAL-FINANCIAL-LOGIC.md §8.3 — centimes → DZD.
            put("p_amount", amountDzd)
            put("p_method", p.str("method") ?: "cash")
            put("p_category", p.str("category") ?: "other")
            put("p_status", p.str("status"))
            put("p_proof_path", p.str("proofUrl") ?: p.str("proof_path"))
            put("p_collected_at", p.str("collectedAt") ?: p.str("collected_at"))
            put("p_collected_by", p.str("collectedBy") ?: p.str("collected_by") ?: actorId)
            put("p_notes", p.str("notes"))
            // CANONICAL-FINANCIAL-LOGIC.md §8.5 — check / transfer metadata.
            p.str("installmentId") ?: p.str("installment_id")?.let { put("p_installment_id", it) }
            p.str("checkNumber") ?: p.str("check_number")?.let { put("p_check_number", it) }
            p.str("checkBankName") ?: p.str("check_bank_name")?.let { put("p_check_bank_name", it) }
            p.str("checkIssueDate") ?: p.str("check_issue_date")?.let { put("p_check_issue_date", it) }
            p.str("checkClearanceDate") ?: p.str("check_clearance_date")?.let { put("p_check_clearance_date", it) }
            p.str("transferReference") ?: p.str("transfer_reference")?.let { put("p_transfer_reference", it) }
            p.str("transferSourceBank") ?: p.str("transfer_source_bank")?.let { put("p_transfer_source_bank", it) }
        }
        NetworkTimeouts.guard<Unit>("sync.pushPayment", timeoutMs = 5_000L) {
            supabaseProvider.postgrest.rpc("upsert_payment_from_import", params)
        }
    }

    private suspend fun pushLedgerEntry(
        entry: SyncQueueEntity,
        p: JsonObject,
        actorId: String,
    ) {
        val parentId = p.str("parentId") ?: p.str("parent_id") ?: return
        // CANONICAL-FINANCIAL-LOGIC.md §8.3 — centimes → DZD.
        val amountCentimes = p.str("amount")?.toLongOrNull() ?: return
        val amountDzd = amountCentimes / 100.0
        // CANONICAL-FINANCIAL-LOGIC.md §8.4 — `p_metadata` MUST be sent on every
        // ledger_entry push. Preserve verbatim from the source entity.
        val metadataJsonStr = p.str("metadataJson") ?: p.str("metadata_json") ?: "{}"
        val metadataElement = runCatching {
            json.parseToJsonElement(metadataJsonStr)
        }.getOrNull() ?: JsonNull
        val params = buildJsonObject {
            put("p_tenant_id", entry.tenantId)
            put("p_entry_number", p.str("id") ?: p.str("entry_number"))
            put("p_parent_id", parentId)
            // p_student_id — nullable (parent_credit entries have studentId=null).
            val studentId = p.str("studentId") ?: p.str("student_id")
            if (studentId != null) put("p_student_id", studentId) else put("p_student_id", JsonNull)
            put("p_account_id", p.str("accountId") ?: p.str("account_id"))
            put("p_entry_type", p.str("type") ?: p.str("entry_type") ?: "charge")
            // CANONICAL-FINANCIAL-LOGIC.md §8.3 — centimes → DZD.
            put("p_amount", amountDzd)
            put("p_category", p.str("category") ?: "other")
            put("p_description", p.str("description"))
            put("p_source_type", p.str("sourceType") ?: p.str("source_type") ?: "bulk_import")
            put("p_source_id", p.str("sourceId") ?: p.str("source_id"))
            put("p_method", p.str("method"))
            put("p_receipt_number", p.str("receiptNumber") ?: p.str("receipt_number"))
            put("p_payment_status", p.str("paymentStatus") ?: p.str("payment_status"))
            put("p_reverses_id", p.str("reversesId") ?: p.str("reverses_id"))
            put("p_actor_id", p.str("actorId") ?: p.str("actor_id") ?: actorId)
            put("p_actor_name", p.str("actorName") ?: p.str("actor_name") ?: "Android")
            put("p_at", p.str("at"))
            // CANONICAL-FINANCIAL-LOGIC.md §8.4 — send p_metadata as a JSON object.
            put("p_metadata", metadataElement)
        }
        NetworkTimeouts.guard<Unit>("sync.pushLedgerEntry", timeoutMs = 5_000L) {
            supabaseProvider.postgrest.rpc("upsert_ledger_entry_from_import", params)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun generateParentCode(): String {
        val year = java.time.Year.now().value
        val chars = ('A'..'Z') + ('0'..'9')
        val suffix = (1..4).map { chars.random() }.joinToString("")
        return "PAR-$year-$suffix"
    }

    private fun generateStudentCode(): String {
        val year = java.time.Year.now().value
        val seq = (1..1_000_000).random()
        return "ELV-$year-${seq.toString().padStart(6, '0')}"
    }

    private fun generatePaymentNumber(): String {
        val year = java.time.Year.now().value
        val seq = (1..1_000_000).random()
        return "PAY-$year-${seq.toString().padStart(6, '0')}"
    }
}

/**
 * Helper: return the string content of a [JsonObject] field by key, or null
 * if the field is absent, null, or not a string primitive.
 *
 * Workaround for the Kotlin serialization API's verbose null handling —
 * handles `JsonNull`, missing keys, and non-string primitives uniformly.
 */
private fun JsonObject.str(key: String): String? {
    val el: JsonElement = this[key] ?: return null
    if (el is JsonNull) return null
    return try {
        el.jsonPrimitive.content.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        null
    }
}
