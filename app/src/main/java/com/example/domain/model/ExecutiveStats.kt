package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * T-340 (61st session, 2026-09-14 — STATS-400): the executive-statistics
 * snapshot — the repository → UI contract for the Executive Command Center.
 *
 * Every value is computed by `core/ExecutiveStatistics.kt` (the ADR-002
 * verbatim mirror of the desktop's executive-statistics.ts) at the
 * REPOSITORY level — the UI layer only renders. Honest defaults: no
 * fabricated meters, no hardcoded reference numbers (§15.16). This snapshot
 * REPLACES the removed vanity fields (amountBins / collectionHeatmap /
 * revenueTrend) on the DashboardKpi contract.
 */
@Serializable
data class ExecutiveStatsSnapshot(
    val waves: List<ExecWaveItem> = emptyList(),
    val erosion: ExecErosionItem = ExecErosionItem(),
    val triage: ExecTriageSnapshot = ExecTriageSnapshot(),
    val concentration: ExecConcentrationSnapshot = ExecConcentrationSnapshot(),
    val transport: ExecTransportSnapshot = ExecTransportSnapshot(),
    val services: List<ExecServiceItem> = emptyList(),
    val dynamics: ExecDynamicsSnapshot = ExecDynamicsSnapshot(),
    val riskSummary: ExecRiskSummaryItem = ExecRiskSummaryItem(),
    val riskRadar: List<ExecRiskProfileItem> = emptyList(),
)

@Serializable
data class ExecWaveItem(
    val key: String,                // "tuition#1" …
    val category: String,
    val categoryLabel: String,      // FR label
    val wave: Int,                  // 1 | 2 | 3
    val installmentCount: Int,
    val paidCount: Int,
    val familyCount: Int,
    val debtorFamilyCount: Int,
    val dueTotal: Long,             // centimes
    val paidTotal: Long,
    val remainingTotal: Long,
    val collectedPct: Int,
    val clearedPct: Int,
    val dueDate: String? = null,
    val phase: String = "in_window",// not_due | in_window | overdue
)

@Serializable
data class ExecErosionItem(
    val remiseCount: Int = 0,
    val remiseTotal: Long = 0L,     // centimes
    val cancelCount: Int = 0,
    val cancelTotal: Long = 0L,
    val netRemiseTotal: Long = 0L,
    val grossCharges: Long = 0L,
    val stickerTotal: Long = 0L,
    val erosionPct: Int = 0,
    val averageRemise: Long = 0L,
    val maxRemise: Long = 0L,
    val minRemise: Long = 0L,
    val remiseFamilyCount: Int = 0,
)

@Serializable
data class ExecTriageBucketItem(
    val bucket: String,             // not_due | current | reminder | chronic
    val label: String,              // FR label
    val amount: Long,               // centimes
    val installmentCount: Int,
    val familyCount: Int,
    val share: Int,
)

@Serializable
data class ExecCallListEntryItem(
    val parentId: String,
    val parentName: String,
    val outstanding: Long,          // centimes
    val worstDaysOverdue: Long,
)

@Serializable
data class ExecTriageSnapshot(
    val buckets: List<ExecTriageBucketItem> = emptyList(),
    val totalOutstanding: Long = 0L,
    val callList: List<ExecCallListEntryItem> = emptyList(),
)

@Serializable
data class ExecTopFamilyItem(
    val parentId: String,
    val parentName: String,
    val outstanding: Long,          // centimes
    val childCount: Int,
    val shareOfTotalDebt: Int,
    val worstDaysOverdue: Long,
)

@Serializable
data class ExecConcentrationSnapshot(
    val totalOutstanding: Long = 0L,
    val debtorFamilyCount: Int = 0,
    val topFamilies: List<ExecTopFamilyItem> = emptyList(),
    val topTotal: Long = 0L,
    val topConcentrationPct: Int = 0,
)

@Serializable
data class ExecTransportRouteItem(
    val destination: String,        // canonical key
    val destinationLabel: String,   // FR label
    val riders: Int,
    val dueTotal: Long,             // centimes
    val paidTotal: Long,
    val remainingTotal: Long,
    val collectedPct: Int,
)

@Serializable
data class ExecTransportSnapshot(
    val riders: Int = 0,
    val nonRiders: Int = 0,
    val unresolvedRawValues: List<String> = emptyList(),
    val routes: List<ExecTransportRouteItem> = emptyList(),
    val dueTotal: Long = 0L,
    val paidTotal: Long = 0L,
    val remainingTotal: Long = 0L,
    val collectedPct: Int = 0,
)

@Serializable
data class ExecServiceItem(
    val category: String,
    val label: String,              // FR label
    val paymentCount: Int,
    val revenue: Long,              // centimes
    val studentCount: Int,
)

@Serializable
data class ExecFamilySizeItem(
    val label: String,              // "1 enfant" … "5+ enfants"
    val familyCount: Int,
    val studentCount: Int,
)

@Serializable
data class ExecSectionImbalanceItem(
    val gradeLabel: String,
    val sectionCount: Int,
    val sectionsLabel: String,      // "51 / 3" — the enrolled counts, desc
    val minEnrolled: Int,
    val maxEnrolled: Int,
    val averageEnrolled: Int,
    val spread: Int,
    val imbalanced: Boolean,
)

@Serializable
data class ExecDynamicsSnapshot(
    val totalStudents: Int = 0,
    val totalFamilies: Int = 0,
    val siblingIndex: Double? = null,
    val multiChildFamilyCount: Int = 0,
    val multiChildFamilyPct: Int = 0,
    val familySizes: List<ExecFamilySizeItem> = emptyList(),
    val imbalances: List<ExecSectionImbalanceItem> = emptyList(),
)

@Serializable
data class ExecRiskSummaryItem(
    val tripleCriticalCount: Int = 0,
    val academicAlertCount: Int = 0,
    val attendanceAlertCount: Int = 0,
    val financialTensionCount: Int = 0,
    val healthyCount: Int = 0,
    val tripleCriticalPct: Int = 0,
)

@Serializable
data class ExecRiskProfileItem(
    val studentId: String,
    val studentName: String,
    val className: String,
    val parentId: String,
    val parentName: String,
    val gpa: Double? = null,
    val attendanceRate: Double = 1.0,
    val unexcusedAbsences: Int = 0,
    val debtAmount: Long = 0L,      // centimes
    val riskCategory: String = "healthy",
)
