package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Snapshot of dashboard KPIs — computed from real local Room database tables
 * and Supabase materialized views.
 */
@Serializable
data class DashboardKpi(
    val totalStudents: Int = 0,
    val totalParents: Int = 0,
    val totalStaff: Int = 0,
    val monthlyRevenue: Long = 0L,
    val todayRevenue: Long = 0L,
    val todayPaymentsCount: Int = 0,
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