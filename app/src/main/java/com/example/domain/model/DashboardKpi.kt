package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Snapshot of dashboard KPIs — unified with the desktop canonical analytics model.
 *
 * PARITY-002 (44th session): every derived statistic (collection rate, aging
 * buckets, funnel, descriptive stats, bins, category mix) is computed by
 * `core/StatisticsEngine.kt` (the desktop's analytics-derivations mirror) at
 * the REPOSITORY level — the UI layer only renders. Honest defaults: no
 * fabricated "Août" best month, no 100% critical funnel, no 0.49f rate.
 */
@Serializable
data class DashboardKpi(
    val totalStudents: Int = 0,
    val totalParents: Int = 0,
    val totalStaff: Int = 0,
    val totalFamilies: Int = 0,
    val totalRevenue: Long = 0L,
    val monthlyRevenue: Long = 0L,
    val todayRevenue: Long = 0L,
    val todayPaymentsCount: Int = 0,
    // Descriptive statistics (desktop derivePaymentStats — sample σ, Math.round mean/median)
    val totalOperationsCount: Int = 0,
    val averageBasketAmount: Long = 0L,
    val medianBasketAmount: Long = 0L,
    val volatilityAmount: Long = 0L,
    val bestMonthName: String? = null,
    val bestMonthAmount: Long = 0L,
    val outstandingDebt: Long = 0L,
    val overdueDebt: Long = 0L,
    val overdueFamiliesCount: Int = 0,
    val pendingExpenses: Int = 0,
    val pendingExpensesAmount: Long = 0L,
    val attendanceRateToday: Double = 0.0,
    val todayPresentCount: Int = 0,
    val todayAbsentCount: Int = 0,
    val classesCompletedRollCall: Int = 0,
    val totalClassesCount: Int = 0,
    val pendingChecksCount: Int = 0,
    val pendingChecksAmount: Long = 0L,
    val overdueAlerts: Int = 0,
    // The annual collection rate — computed by StatisticsEngine.collectionRatePct
    // (encaissé / (encaissé + créances), Math.round, clamp 100). Rendered, never re-derived in UI.
    val collectionRatePct: Int = 0,
    // Debt aging census (StatisticsEngine.deriveDebtAging — per-installment INV-4,
    // distinct families per bucket; the desktop Supabase canonical path).
    val debtByAging: List<DebtAgingBucketItem> = emptyList(),
    // Recovery funnel (StatisticsEngine.deriveRecoveryFunnel — computed from the
    // aging census; empty list = honest "no overdue families", NEVER 100% critical).
    val recoveryFunnel: List<RecoveryFunnelStageItem> = emptyList(),
    // Amount Distribution Bins (StatisticsEngine.deriveAmountHistogram)
    val amountBins: List<AmountBinItem> = emptyList(),
    // Category Breakdown (StatisticsEngine.deriveCategoryMix — ALL canonical categories)
    val categoryBreakdown: List<CategoryRevenueItem> = emptyList(),
    // ── PARITY-003 (45th session) — the visual-parity contract ──────────────
    // Descriptive-stat completeness (desktop PaymentStats min/max — stat strip)
    val paymentStatsMin: Long = 0L,
    val paymentStatsMax: Long = 0L,
    // Payment-method mix (StatisticsEngine.deriveMethodMix — the donut card;
    // replaces the repository's former fixed-3-method parallel derivation)
    val methodMix: List<MethodMixItem> = emptyList(),
    // Weekly operating rhythm (StatisticsEngine.deriveWeeklyRhythm — the
    // counter-activity convention: only "refunded" excluded; Dim→Jeu)
    val weeklyRhythm: List<WeeklyRhythmItem> = emptyList(),
    // Collection heatmap (StatisticsEngine.deriveCollectionHeatmap — weekday × month matrix)
    val collectionHeatmap: CollectionHeatmapSnapshot = CollectionHeatmapSnapshot(),
    // Revenue trend explorer (StatisticsEngine.deriveRevenueTrend — cumulative + 3-month MA)
    val revenueTrend: List<RevenueTrendPointItem> = emptyList(),
    // Year-over-year comparison (StatisticsEngine.deriveYearOverYear — null deltas = "n/a")
    val yoy: YoYSnapshot = YoYSnapshot(),
    // Tranche wave progress (StatisticsEngine.deriveTrancheWaves — T1/T2/T3 collection health)
    val trancheWaves: List<TrancheWaveItem> = emptyList(),
    // Class demographics & capacity (StatisticsEngine.deriveDemographics)
    val demographics: ClassDemographicsSnapshot = ClassDemographicsSnapshot(),
)

@Serializable
data class DebtAgingBucketItem(
    val bucket: String,      // "0_30" | "31_60" | "61_90" | "91_180" | "180_plus"
    val label: String,       // "0–30 j" … "180+ j"
    val amount: Long,        // Σ remaining (centimes)
    val debtorCount: Int,    // distinct families in the bucket
    val sharePct: Int,       // share of total outstanding (Math.round)
)

@Serializable
data class RecoveryFunnelStageItem(
    val name: String,        // "En retard" | "≤ 60 j" | "61–90 j" | "> 90 j"
    val count: Int,
    val sharePct: Int,
)

@Serializable
data class AmountBinItem(
    val label: String,
    val count: Int,
    val amount: Long = 0L,
    val percentage: Int = 0,
)

@Serializable
data class CategoryRevenueItem(
    val category: String,
    val label: String,
    val amount: Long,
    val count: Int,
    val percentage: Int,
)

/** Summary of revenue collected by payment method (cash, check, transfer). */
@Serializable
data class PaymentMethodSummary(
    val method: String,
    val label: String,
    val count: Int,
    val totalAmount: Long,
    val percentage: Double,
)

/** Today's roll-call status for a specific academic class. */
@Serializable
data class ClassRollCallStatus(
    val classId: String,
    val className: String,
    val level: String,
    val totalStudents: Int,
    val isCompletedToday: Boolean,
    val presentCount: Int,
    val absentCount: Int,
    val lateCount: Int,
)

/** Actionable operational alert generated directly from live system data. */
@Serializable
data class DashboardOperationalAlert(
    val id: String,
    val type: String, // "overdue_debt", "pending_expense", "pending_check", "missing_roll_call", "frequent_absence"
    val title: String,
    val description: String,
    val amount: Long? = null,
    val count: Int? = null,
    val phone: String? = null,
    val severity: String = "medium", // "low", "medium", "high", "urgent"
    val entityType: String? = null,
    val entityId: String? = null,
    val actionLabel: String? = null,
)
