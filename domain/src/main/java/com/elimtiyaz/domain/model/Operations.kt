package com.elimtiyaz.domain.model

import kotlinx.serialization.Serializable

/** Personnel directory — §09.04. */
@Serializable
data class Personnel(
    val id: String,
    val tenantId: String,
    val firstName: String,
    val lastName: String,
    val staffCategory: StaffCategory,
    val phone: String,
    val email: String? = null,
    val hireDate: String,
    val salary: Double? = null,
    val weeklyHoursTarget: Int = 0,
    val weeklyHoursLogged: Int = 0,
    val avatarUrl: String? = null,
    val status: PersonnelStatus = PersonnelStatus.Active,
)

@Serializable
enum class StaffCategory(val displayFr: String, val displayAr: String) {
    Teacher("Enseignant", "معلم"),
    Administration("Administration", "إدارة"),
    Support("Soutien", "دعم"),
    Maintenance("Maintenance", "صيانة"),
    Driver("Chauffeur", "سائق");
}

@Serializable
enum class PersonnelStatus { Active, OnLeave, Suspended, Terminated }

/** Teacher activity ledger — "Relevé" in French. §09.05. */
@Serializable
data class ReleveEntry(
    val id: String,
    val personnelId: String,
    val personnelName: String,
    val date: String,
    val hoursIn: Double,
    val hoursOut: Double? = null,
    val activity: String,
    val classId: String? = null,
    val subjectId: String? = null,
    val recordedAt: String,
)

/** Audit log entry — contextual schema per §12.02. */
@Serializable
data class AuditEntry(
    val id: String,
    val tenantId: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val actorId: String,
    val actorName: String,
    val diff: String? = null,
    val note: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val at: String,
)

/** Dashboard KPI — §15. */
@Serializable
data class DashboardKpi(
    val totalStudents: Int,
    val totalParents: Int,
    val totalStaff: Int,
    val monthlyRevenue: Double,
    val outstandingDebt: Double,
    val pendingExpenses: Int,
    val attendanceRateToday: Double,
    val overdueAlerts: Int,
)

@Serializable
data class RevenuePoint(
    val label: String,                 // e.g. "Sep", "T1 Tranche"
    val amount: Double,
)

@Serializable
data class DebtByAgingBucket(
    val bucket: String,
    val amount: Double,
    val debtorCount: Int,
)

@Serializable
data class DemographicSlice(
    val label: String,
    val count: Int,
    val percent: Double,
)

/** Notifications — local in-app center plus FCM push. */
@Serializable
data class AppNotification(
    val id: String,
    val title: String,
    val body: String,
    val type: NotificationType,
    val entityType: String? = null,
    val entityId: String? = null,
    val readAt: String? = null,
    val createdAt: String,
)

@Serializable
enum class NotificationType { PaymentOverdue, ExpensePending, AttendanceAlert, Homework, Audit, System, Message }
