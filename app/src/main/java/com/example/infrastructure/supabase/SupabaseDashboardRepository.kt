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
        emit(try {
            provider.postgrest.from("mv_dashboard_kpis")
                .select { limit(1) }
                .decodeList<DashboardKpiDto>()
                .firstOrNull()
                ?.toDomain()
        } catch (e: Exception) { null })
    }

    override fun observeRevenueLast12Months() = flow {
        emit(try {
            provider.postgrest.from("mv_revenue_by_month")
                .select {
                    order("month", Order.ASCENDING)
                    limit(12)
                }
                .decodeList<RevenueMonthDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override fun observeDebtByAging() = flow {
        emit(try {
            provider.postgrest.from("mv_debt_aging")
                .select {
                    order("outstanding", Order.DESCENDING)
                    limit(200)
                }
                .decodeList<DebtAgingDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override suspend fun refreshKpis(): Result<Unit> = try {
        provider.postgrest.rpc("refresh_all_materialized_views")
        auditRepository.log(AuditLogInput(
            action = AuditActions.MATERIALIZED_VIEWS_REFRESH,
            entityType = "dashboard",
            entityId = "all",
            afterJson = """{"refreshed":["mv_dashboard_kpis","mv_debt_aging","mv_top_debtors","mv_revenue_by_month","mv_grade_summary"]}""",
            note = "Materialized views refreshed from Android app",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
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
