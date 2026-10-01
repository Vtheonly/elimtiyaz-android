package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Per-parent debt summary row — the shared canonical derivation
 * (PARITY-002/T-284: per-parent Σ INV-4 remaining over unpaid installments,
 * dueDate-based aging — identical to the desktop's Supabase seedSummary and
 * the dashboard's top-debtors list). `bucket` is the aging bucket assignment.
 *
 * T-457 (§15.1): the row additionally carries the canonical 4-TIER
 * DEBT-STATUS (financial-rules §15.1/§15.3 — the configurable aging
 * hierarchy over the INV-4 remaining): the level, the §15.3 label
 * (identical wording on every surface), and the INV-16d explanation —
 * a UI can never show a bare color.
 */
@Serializable
data class DebtSummary(
    val parentId: String,
    val parentName: String,
    val parentPhone: String,
    val studentCount: Int,
    val outstandingAmount: Long,
    /** Σ INV-4 remaining restricted to PAST-DUE unpaid installments (the desktop overdue portion). */
    val overdueAmount: Long = 0L,
    val daysOverdue: Long,
    val bucket: String,                  // 0_30 | 31_60 | 61_90 | 91_180 | 180_plus
    /** The §15.1 status level: green | yellow | orange | red. */
    val statusLevel: String = "green",
    /** The §15.3 canonical FR label (Soldé / À échoir · À surveiller · Retard soutenu · Critique / Contentieux). */
    val statusLabel: String = "Soldé / À échoir",
    /** The INV-16d derived explanation (the status's reason, the factors, the payeur-actif annotation). */
    val statusExplanation: String = "",
)
