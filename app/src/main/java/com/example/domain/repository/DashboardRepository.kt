package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.ClassRollCallStatus
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DashboardOperationalAlert
import com.example.domain.model.DebtSummary
import com.example.domain.model.PaymentMethodSummary
import kotlinx.coroutines.flow.Flow

/** Dashboard repository contract — real-time KPIs, trends, and operational summaries. */
interface DashboardRepository {
    fun observeKpis(): Flow<DashboardKpi?>
    fun observeRevenueLast12Months(): Flow<List<RevenuePoint>>
    fun observeDebtByAging(): Flow<List<DebtSummary>>
    fun observePaymentMethodsSummary(): Flow<List<PaymentMethodSummary>>
    fun observeClassRollCallStatus(): Flow<List<ClassRollCallStatus>>
    fun observeOperationalAlerts(): Flow<List<DashboardOperationalAlert>>

    /**
     * Real daily attendance rates over the last 7 days, computed from the
     * `attendance` table. Days with no roll-call records are omitted (no
     * fabricated values) — the UI decides how to display gaps.
     */
    fun observeAttendanceTrend(): Flow<List<AttendanceTrendPoint>>
    suspend fun refreshKpis(): Result<Unit>
}

/** Single revenue data point for the 12-month chart. */
data class RevenuePoint(val label: String, val amount: Long)

/** Daily attendance rate data point for the 7-day trend chart. */
data class AttendanceTrendPoint(val label: String, val rate: Double, val records: Int)