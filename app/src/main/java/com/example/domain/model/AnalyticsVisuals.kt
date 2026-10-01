package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * PARITY-003 (45th session, 2026-09-11) — the visual-parity contract.
 *
 * The item types the dashboard's Analytique tab + the tranche-wave /
 * demographics surfaces render. Every value is computed by
 * `core/StatisticsEngine.kt` (the ADR-002 mirror of the desktop
 * derivations) at the REPOSITORY level — the UI layer only renders.
 * Honest defaults everywhere: empty lists / nulls, NEVER fabricated
 * reference numbers (§15.16).
 */

// ============================================================================
// Method mix (desktop deriveMethodMix — the donut card)
// ============================================================================

@Serializable
data class MethodMixItem(
    val method: String,       // "cash" | "check" | "transfer" | …
    val label: String,        // "Espèces" | "Chèque" | "Virement"
    val amount: Long,         // centimes
    val count: Int,
    val percent: Int,         // Math.round share (PARITY-001 — never truncation)
)

// ============================================================================
// Weekly operating rhythm (desktop deriveWeeklyRhythm — stacked bars)
// ============================================================================

@Serializable
data class WeeklyRhythmItem(
    val day: String,          // "Dim" | "Lun" | "Mar" | "Mer" | "Jeu"
    val cash: Long,           // centimes
    val check: Long,
    val transfer: Long,
) {
    val total: Long get() = cash + check + transfer
}

// ============================================================================
// T-340 (61st session, STATS-400): the collection-heatmap and the revenue
// trend-explorer item types were REMOVED with the vanity statistics (the
// owner's kill list). The replacements live in ExecutiveStats.kt
// (ExecutiveStatsSnapshot — the wave staircase, the erosion, the triage…).
// ============================================================================

// ============================================================================
// Year-over-year comparison (desktop deriveYearOverYear — grouped bars)
// ============================================================================

@Serializable
data class YoYPointItem(
    val label: String,
    val current: Long,        // centimes
    val previous: Long,
    val deltaPercent: Int? = null, // null when previous == 0 ("n/a", never −100%)
)

@Serializable
data class YoYSnapshot(
    val points: List<YoYPointItem> = emptyList(),
    val totalCurrent: Long = 0L,
    val totalPrevious: Long = 0L,
    val deltaPercent: Int? = null,
)

// ============================================================================
// Tranche wave progress (desktop deriveTrancheWaves — T1/T2/T3 meters)
// T-454 (PARITY-007): the CONTRACT now mirrors the desktop's CURRENT strip
// view model (the T-447 adapter over the canonical pooled rows) — the
// pre-T-454 shape (label-parsed grouping + clamped pct + no dates) is
// retired from the production feed.
// ============================================================================

@Serializable
data class TrancheWaveItem(
    val index: Int,           // 1 | 2 | 3
    val label: String,        // "Tranche 1 (Septembre)" …
    val hint: String,         // due-window hint (display-only fallback)
    val due: Long,            // Σ amountDue (centimes — the POOLED all-categories basis)
    val paid: Long,           // Σ amountPaid (includes uncleared checks)
    val pending: Long,        // Σ amountPending
    /** T-454 (INV-4): the canonical remaining (Σ due − paid − pending, clamped) — the strip's "reste" line. */
    val remaining: Long = 0L,
    /** T-454 (PARITY-001): the canonical UNCLAMPED rate — over-covered waves are honest. */
    val pct: Int,             // round(paid/due×100), never min(100, …)
    /** T-432 (DATA-049): the tuition-isolated rate — the same number the Statistics wave cards show. */
    val tuitionPct: Int? = null,
    val isNextTarget: Boolean = false,
    /** T-427: the wave is overdue (an unsettled row's due date is past). */
    val isOverdue: Boolean = false,
    /** T-434/T-435: the wave's DERIVED earliest due date (ISO) — never a hardcoded hint. */
    val dueDate: String? = null,
    /** T-435: the wave's derived LATEST due date (ISO) — the range's far bound. */
    val dueDateMax: String? = null,
)

// ============================================================================
// Class demographics (desktop demographics — charts; capacity REMOVED T-340)
// ============================================================================

@Serializable
data class DemographicSliceItem(
    val label: String,
    val count: Int,
    val percent: Int,          // Math.round(count/total × 100)
)

@Serializable
data class ClassDemographicsSnapshot(
    val grade: List<DemographicSliceItem> = emptyList(),
    val gender: List<DemographicSliceItem> = emptyList(),
    val age: List<DemographicSliceItem> = emptyList(),
    // T-340 (STATS-400): the capacity fill-rate slice REMOVED — no fake
    // ceilings (the section-imbalance derivation replaced the gauges).
)
