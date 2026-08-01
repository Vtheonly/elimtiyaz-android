package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Snapshot of dashboard KPIs — sourced from the `mv_dashboard_kpis`
 * materialized view, refreshed via `refresh_kpis()` RPC.
 */
@Serializable
data class DashboardKpi(
    val totalStudents: Int,
    val totalParents: Int,
    val totalStaff: Int,
    val monthlyRevenue: Long,
    val outstandingDebt: Long,
    val pendingExpenses: Int,
    val attendanceRateToday: Double,
    val overdueAlerts: Int,
)
