package com.example.ui.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation routes — Navigation 2.8+ @Serializable route objects.
 *
 * Three top-level destinations:
 *   - [Splash]: shown briefly while the session is being restored
 *   - [Auth]: login + change-password
 *   - [Main]: bottom-nav host with 5 hubs
 *
 * Detail routes (student/parent/payment/expense/workflow) are pushed from
 * hub screens and carry their ID argument.
 */
object Routes {
    @Serializable object Splash : Route
    @Serializable object Login : Route
    @Serializable object ChangePassword : Route
    @Serializable object Main : Route

    // Bottom-nav hubs (children of Main)
    @Serializable object DashboardHub : Route
    @Serializable object CrmHub : Route
    @Serializable object AcademicsHub : Route
    @Serializable object FinancialsHub : Route
    @Serializable object PersonnelHub : Route

    // CRM detail routes
    @Serializable data class StudentDetail(val studentId: String) : Route
    @Serializable data class ParentDetail(val parentId: String) : Route
    @Serializable object BatchRegistration : Route

    // Financials detail routes
    @Serializable data class PaymentDetail(val paymentId: String) : Route
    @Serializable data class ExpenseDetail(val expenseId: String) : Route
    @Serializable object CounterPayment : Route
    @Serializable object ProofScanner : Route
    @Serializable object DebtDashboard : Route
    @Serializable object InstallmentSchedule : Route

    // Settings
    @Serializable object Settings : Route
    @Serializable object AuditLog : Route
}

sealed interface Route
