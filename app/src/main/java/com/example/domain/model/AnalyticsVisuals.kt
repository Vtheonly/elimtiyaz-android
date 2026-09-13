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
// ============================================================================

@Serializable
data class TrancheWaveItem(
    val index: Int,           // 1 | 2 | 3
    val label: String,        // "Tranche 1 (Septembre)" …
    val hint: String,         // due-window hint (display-only)
    val due: Long,            // Σ amountDue (centimes)
    val paid: Long,           // Σ amountPaid (includes uncleared checks)
    val pending: Long,        // Σ amountPending
    val pct: Int,             // min(100, Math.round(paid/due×100))
    val isNextTarget: Boolean = false,
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
