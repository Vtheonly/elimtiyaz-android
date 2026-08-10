package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Parent (guardian) domain entity — mirrors desktop `src/domain/model/parent.ts`.
 *
 * SHARED-UNIFICATION (migration 0027):
 *   - `displayName` is the COMPLETE name as imported from Excel (e.g.
 *     "BENALI Mohamed"). When non-null, the UI MUST show it verbatim
 *     instead of `firstName + " " + lastName`.
 *   - This fixes the "Tuteur BENALI" prefix bug — the desktop importer
 *     previously used "Tuteur" as a placeholder for `firstName` when
 *     TUTEUR was empty. The complete name is now preserved in `displayName`
 *     end-to-end (Excel → Desktop → Supabase → Android → UI).
 *
 * Amounts are never stored on Parent; financial state lives on
 * [com.example.domain.model.Installment] / [com.example.domain.model.Payment]
 * and is aggregated via the ledger.
 */
@Serializable
data class Parent(
    val id: String,
    val tenantId: String,
    val code: String,                    // PAR-{year}-{4-char}
    val firstName: String,
    val lastName: String,
    /**
     * COMPLETE display name as imported. When non-null, UI shows this verbatim.
     * Migration 0027.
     */
    val displayName: String? = null,
    val phone: String,
    val whatsapp: String? = null,
    val email: String? = null,
    val occupation: String? = null,
    val address: String? = null,
    val transportDestination: String? = null,
    val preferredLanguage: String = "fr",
    val avatarUrl: String? = null,
    val createdAt: String,
    val updatedAt: String,
) {
    /**
     * The COMPLETE name for display.
     *
     * Prefers `displayName` (the full imported name) and falls back to
     * `firstName + " " + lastName` only when `displayName` is null/empty.
     *
     * UI code MUST use this property — never read `firstName`/`lastName`
     * directly for display.
     */
    val fullName: String
        get() {
            val dn = displayName?.trim().orEmpty()
            return if (dn.isNotEmpty()) dn
            else listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifEmpty { "—" }
        }
}
