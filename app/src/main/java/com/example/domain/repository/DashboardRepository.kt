package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DebtSummary
import kotlinx.coroutines.flow.Flow

/** Dashboard repository contract — KPIs + chart data. */
interface DashboardRepository {
    fun observeKpis(): Flow<DashboardKpi?>
    fun observeRevenueLast12Months(): Flow<List<RevenuePoint>>
    fun observeDebtByAging(): Flow<List<DebtSummary>>
    suspend fun refreshKpis(): Result<Unit>
}

/** Single revenue data point for the 12-month chart. */
data class RevenuePoint(val label: String, val amount: Long)
