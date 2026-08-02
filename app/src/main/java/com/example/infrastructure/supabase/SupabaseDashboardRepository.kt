package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DebtSummary
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.DashboardRepository
import com.example.domain.repository.RevenuePoint
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of DashboardRepository.
 *
 * Reads from materialized views defined in migration 0021:
 *   - `mv_dashboard_kpis`        — single-row tenant KPI snapshot
 *   - `mv_revenue_by_month`      — last 12 months of paid payments
 *   - `mv_debt_aging`            — per-parent debt bucketed by aging tier
 *
 * `refreshKpis` calls the `refresh_all_materialized_views()` RPC
 * (migration 0022) which refreshes all MVs concurrently. The mobile app
 * calls this after bulk imports or large batch operations.
 *
 * All observers catch exceptions and emit `null` / `emptyList()` so the UI
 * never crashes on a network failure.
 */
@Singleton
class SupabaseDashboardRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : DashboardRepository {

    override fun observeKpis() = flow {
        // FIX (login-blocks): hard 2.5s timeout. If Supabase is unconfigured
        // or the network is slow, emit null so the ViewModel falls back to
        // its seeded defaultKpi. NEVER block the UI.
        emit(NetworkTimeouts.guard<DashboardKpi>("dash.observeKpis", timeoutMs = 2_500L) {
            provider.postgrest.from("mv_dashboard_kpis")
                .select { limit(1) }
                .decodeList<DashboardKpiDto>()
                .firstOrNull()
                ?.toDomain()
        })
    }

    override fun observeRevenueLast12Months() = flow {
        emit(NetworkTimeouts.guard<List<RevenuePoint>>("dash.observeRevenue", timeoutMs = 2_500L) {
            provider.postgrest.from("mv_revenue_by_month")
                .select {
                    order("month", Order.ASCENDING)
                    limit(12)
                }
                .decodeList<RevenueMonthDto>()
                .map { it.toDomain() }
        } ?: emptyList())
    }

    override fun observeDebtByAging() = flow {
        emit(NetworkTimeouts.guard<List<DebtSummary>>("dash.observeDebt", timeoutMs = 2_500L) {
            provider.postgrest.from("mv_debt_aging")
                .select {
                    order("outstanding", Order.DESCENDING)
                    limit(200)
                }
                .decodeList<DebtAgingDto>()
                .map { it.toDomain() }
        } ?: emptyList())
    }

    override suspend fun refreshKpis(): Result<Unit> {
        // FIX (login-blocks): hard 3s timeout. If the RPC hangs, return Ok
        // so the dashboard continues to render with cached/demo data.
        val ok = NetworkTimeouts.guard<Unit>("dash.refreshKpis", timeoutMs = 3_000L) {
            provider.postgrest.rpc("refresh_all_materialized_views")
        }
        if (ok != null) {
            auditRepository.log(AuditLogInput(
                action = AuditActions.MATERIALIZED_VIEWS_REFRESH,
                entityType = "dashboard",
                entityId = "all",
                afterJson = """{"refreshed":["mv_dashboard_kpis","mv_debt_aging","mv_top_debtors","mv_revenue_by_month","mv_grade_summary"]}""",
                note = "Materialized views refreshed from Android app",
            ))
            return Result.Ok(Unit)
        }
        // Timeout or unconfigured — return Ok so the UI doesn't show an error.
        // The dashboard will continue to use whatever data the observe* flows
        // have already emitted (cached or demo seed).
        return Result.Ok(Unit)
    }

    @Serializable
    data class DashboardKpiDto(
        val tenantId: String,
        val tenantName: String? = null,
        val totalStudents: Int = 0,
        val totalParents: Int = 0,
        val totalClasses: Int = 0,
        val totalPersonnel: Int = 0,
        val monthlyRevenue: Double = 0.0,
        val outstandingDebt: Double = 0.0,
        val overdueCount: Int = 0,
        val collectionRatePct: Double? = null,
    ) {
        fun toDomain() = DashboardKpi(
            totalStudents = totalStudents,
            totalParents = totalParents,
            totalStaff = totalPersonnel,
            monthlyRevenue = monthlyRevenue.toLong(),
            outstandingDebt = outstandingDebt.toLong(),
            pendingExpenses = 0, // not in mv_dashboard_kpis; UI fetches separately if needed
            attendanceRateToday = 0.0, // computed elsewhere (vw_attendance_summary)
            overdueAlerts = overdueCount,
        )
    }

    @Serializable
    data class RevenueMonthDto(
        val tenantId: String,
        val month: String,
        val revenue: Double = 0.0,
        val paymentCount: Int = 0,
    ) {
        fun toDomain() = RevenuePoint(
            label = month.take(7), // YYYY-MM
            amount = revenue.toLong(),
        )
    }

    @Serializable
    data class DebtAgingDto(
        val tenantId: String,
        val parentId: String,
        val parentName: String,
        val agingBucket: String,
        val outstanding: Double = 0.0,
    ) {
        fun toDomain() = DebtSummary(
            parentId = parentId,
            parentName = parentName,
            parentPhone = "", // not in mv_debt_aging; UI fetches separately if needed
            studentCount = 0, // not in mv_debt_aging
            outstandingAmount = outstanding.toLong(),
            daysOverdue = bucketToDays(agingBucket),
            bucket = agingBucket,
        )

        private fun bucketToDays(bucket: String): Long = when (bucket) {
            "0_30" -> 15L
            "31_60" -> 45L
            "61_90" -> 75L
            "91_180" -> 135L
            "180_plus" -> 200L
            // Backward-compat: accept dash format too (older MV snapshots).
            "0-30" -> 15L
            "31-60" -> 45L
            "61-90" -> 75L
            "91-180" -> 135L
            "180+" -> 200L
            else -> 0L
        }
    }
}
