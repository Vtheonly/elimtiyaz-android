package com.example.infrastructure.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Shared DTOs for the canonical Supabase tables — mirror the schema declared
 * in migration `0027_shared_unification.sql` (the contract shared with the
 * Desktop app).
 *
 * Every field carries an explicit `@SerialName` matching the snake_case
 * PostgreSQL column name. The Kotlin serialization framework would
 * otherwise look for camelCase JSON keys and silently fall back to defaults.
 *
 * These DTOs are decoded from:
 *   - `pull_parents_for_sync` / `pull_students_for_sync` / `pull_payments_for_sync`
 *     / `pull_ledger_entries_for_sync` / `pull_device_tokens_for_sync` RPCs
 *     (used by [com.example.infrastructure.sync.RemoteSyncRepository] to
 *     pull changed rows from Supabase).
 *   - Direct `postgrest.from("parents").select()` calls in the local
 *     repositories (when the cache is cold).
 *
 * The DTOs map to the domain models via the `toDomain()` extension functions
 * declared in [SharedDtoMappers.kt].
 */

@Serializable
data class ParentDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("parent_code") val parentCode: String? = null,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    /** COMPLETE display name as imported (e.g. "BENALI Mohamed"). Migration 0027. */
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("primary_phone") val primaryPhone: String,
    @SerialName("secondary_phone") val secondaryPhone: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("occupation") val occupation: String? = null,
    @SerialName("address") val address: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class StudentDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("parent_id") val parentId: String,
    @SerialName("student_code") val studentCode: String? = null,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    /** COMPLETE display name as imported (e.g. "BENALI Sara"). Migration 0027. */
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("middle_name") val middleName: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("gender") val gender: String? = null,
    @SerialName("grade_level_id") val gradeLevelId: String? = null,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("enrollment_date") val enrollmentDate: String? = null,
    @SerialName("enrollment_status") val enrollmentStatus: String? = "active",
    @SerialName("medical_notes") val medicalNotes: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PaymentDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("payment_number") val paymentNumber: String,
    /** Alias for payment_number — kept in sync by trigger. Migration 0027. */
    @SerialName("receipt_number") val receiptNumber: String? = null,
    @SerialName("parent_id") val parentId: String,
    @SerialName("student_id") val studentId: String? = null,
    @SerialName("installment_id") val installmentId: String? = null,
    @SerialName("amount") val amount: Double,
    @SerialName("method") val method: String,
    /** Billing category. Migration 0027. */
    @SerialName("category") val category: String? = "other",
    @SerialName("status") val status: String,
    @SerialName("proof_path") val proofPath: String? = null,
    @SerialName("collected_at") val collectedAt: String? = null,
    @SerialName("collected_by") val collectedBy: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class LedgerEntryDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("entry_number") val entryNumber: String? = null,
    @SerialName("parent_id") val parentId: String,
    @SerialName("student_id") val studentId: String? = null,
    @SerialName("account_id") val accountId: String,
    @SerialName("entry_type") val entryType: String,
    @SerialName("amount") val amount: Double,
    @SerialName("category") val category: String,
    @SerialName("description") val description: String? = null,
    @SerialName("entry_date") val entryDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // ── Unified columns (migration 0027) ──
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("method") val method: String? = null,
    @SerialName("receipt_number") val receiptNumber: String? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("reverses_id") val reversesId: String? = null,
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("actor_name") val actorName: String? = null,
    @SerialName("at") val at: String? = null,
    @SerialName("metadata") val metadata: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class DeviceTokenDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("token") val token: String,
    @SerialName("platform") val platform: String,
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

// ============================================================================
// RPC payload wrappers
// ============================================================================

/** Result row from `upsert_parent_from_import` / `pull_parents_for_sync`. */
@Serializable
data class UpsertParentResult(
    @SerialName("parent_id") val parentId: String,
    @SerialName("parent_code") val parentCode: String,
    @SerialName("was_inserted") val wasInserted: Boolean,
)

@Serializable
data class UpsertStudentResult(
    @SerialName("student_id") val studentId: String,
    @SerialName("student_code") val studentCode: String,
    @SerialName("was_inserted") val wasInserted: Boolean,
)

@Serializable
data class UpsertPaymentResult(
    @SerialName("payment_id") val paymentId: String,
    @SerialName("payment_number") val paymentNumber: String,
    @SerialName("was_inserted") val wasInserted: Boolean,
)

@Serializable
data class UpsertLedgerEntryResult(
    @SerialName("entry_id") val entryId: String,
    @SerialName("was_inserted") val wasInserted: Boolean,
)
