package com.example.domain.model

import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import kotlinx.serialization.Serializable

/**
 * Domain models — mirror desktop `src/domain/model/`. All immutable.
 * Amounts are Long (centimes) — NEVER Double.
 */

@Serializable
data class Parent(
    val id: String,
    val tenantId: String,
    val code: String,                    // PAR-{year}-{4-char}
    val firstName: String,
    val lastName: String,
    val phone: String,
    val whatsapp: String? = null,
    val email: String? = null,
    val occupation: String? = null,
    val address: String? = null,
    val transportDestination: String? = null,
    val preferredLanguage: String = "fr",
    val avatarUrl: String? = null,
    val createdAt: String,
    val updatedAt: String,
) {
    val fullName: String get() = "$firstName $lastName"
}

@Serializable
data class Student(
    val id: String,
    val tenantId: String,
    val code: String,                    // ELV-{year}-{6-digit}
    val parentId: String,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val birthDate: String,
    val enrollmentDate: String,
    val level: String,                   // primaire | cem | lycee
    val gradeLevel: String,              // 14 codes: prescolaire_1 ... 3eme_annee
    val classId: String? = null,
    val photoUrl: String? = null,
    val medicalNotes: String? = null,
    val status: String = "active",       // active | graduated | transferred | suspended | withdrawn
    val createdAt: String,
    val updatedAt: String,
) {
    val fullName: String get() = "$firstName $lastName"
}

@Serializable
data class AcademicClass(
    val id: String,
    val tenantId: String,
    val name: String,
    val level: String,
    val gradeYear: Int,
    val homeroomTeacherId: String? = null,
    val homeroomTeacherName: String? = null,
    val room: String? = null,
    val capacity: Int,
    val enrolledCount: Int,
    val academicYear: String,
)

@Serializable
data class Subject(
    val id: String,
    val tenantId: String,
    val name: String,
    val nameAr: String? = null,
    val code: String,
    val level: String,
    val coefficient: Int,
    val isExtracurricular: Boolean,
    val passingGrade: Double = 10.0,
)

@Serializable
data class Payment(
    val id: String,
    val tenantId: String,
    val receiptNumber: String,
    val parentId: String,
    val studentId: String? = null,
    val amount: Long,                    // centimes
    val method: PaymentMethod,
    val status: PaymentStatus,
    val category: PaymentCategory,
    val installmentId: String? = null,
    val proofUrl: String? = null,
    val notes: String? = null,
    val collectedBy: String,
    val collectedAt: String,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class Installment(
    val id: String,
    val tenantId: String,
    val parentId: String,
    val studentId: String? = null,
    val category: PaymentCategory,
    val label: String,
    val amountDue: Long,
    val amountPaid: Long,
    val dueDate: String,
    val paidDate: String? = null,
    val status: PaymentStatus,
    val academicCycle: String? = null,
    val customSchedule: Boolean = false,
    val customScheduleNote: String? = null,
) {
    val remaining: Long get() = (amountDue - amountPaid).coerceAtLeast(0L)
}

@Serializable
data class Expense(
    val id: String,
    val tenantId: String,
    val requestCode: String,             // EXP-{year}-{3-digit}
    val title: String,
    val description: String,
    val amount: Long,
    val category: String,                // utilities | supplies | maintenance | transport | event | salary | tax | rent | other
    val payee: String,
    val status: String,                  // draft | submitted | approved | rejected | disbursed | settled
    val submittedBy: String,
    val submittedAt: String,
    val approvedBy: String? = null,
    val approvedAt: String? = null,
    val approvalNote: String? = null,
    val disbursedBy: String? = null,
    val disbursedAt: String? = null,
    val proofUrl: String? = null,
    val proofUploadedBy: String? = null,
    val proofUploadedAt: String? = null,
    val anomalyScore: Double? = null,
    val anomalyNote: String? = null,
)

@Serializable
data class Personnel(
    val id: String,
    val tenantId: String,
    val userId: String? = null,
    val firstName: String,
    val lastName: String,
    val staffCategory: String,           // teacher | administration | support | maintenance | driver | buyer | warehouse | worker
    val roleId: String,                  // Role.code
    val departmentId: String? = null,
    val position: String,
    val phone: String,
    val email: String? = null,
    val hireDate: String,
    val terminationDate: String? = null,
    val salary: Long? = null,
    val status: String = "active",
    val avatarUrl: String? = null,
    val weeklyHoursTarget: Int = 0,
    val weeklyHoursLogged: Int = 0,
) {
    val fullName: String get() = "$firstName $lastName"
}

@Serializable
data class Department(
    val id: String,
    val tenantId: String,
    val name: String,
    val description: String? = null,
    val headPersonnelId: String? = null,
    val parentDepartmentId: String? = null,
    val colorHex: String? = null,
    val archivedAt: String? = null,
)

@Serializable
data class AuditLog(
    val id: String,
    val tenantId: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val actorId: String,
    val actorName: String,
    val actorRole: String? = null,
    val beforeJson: String? = null,
    val afterJson: String? = null,
    val note: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val occurredAt: String,
)

@Serializable
data class Assessment(
    val id: String,
    val tenantId: String,
    val studentId: String,
    val subjectId: String,
    val classId: String,
    val term: String,                    // T1 | T2 | T3
    val academicYear: String,
    val devoir1: Double? = null,
    val devoir2: Double? = null,
    val examen: Double? = null,
    val subjectAverage: Double? = null,
    val coefficient: Int,
    val enteredBy: String,
    val enteredAt: String,
)

@Serializable
data class AttendanceRecord(
    val id: String,
    val tenantId: String,
    val studentId: String,
    val classId: String,
    val date: String,
    val session: String,                 // morning | afternoon | both
    val status: String,                  // present | absent_excused | absent_unexcused | late
    val note: String? = null,
    val recordedBy: String,
    val recordedAt: String,
    val syncedAt: String? = null,
)

@Serializable
data class Homework(
    val id: String,
    val tenantId: String,
    val classId: String,
    val subjectId: String,
    val subjectName: String,
    val teacherId: String,
    val teacherName: String,
    val title: String,
    val description: String,
    val dueDate: String,
    val attachments: List<String> = emptyList(),
    val academicYear: String,
    val createdAt: String,
    val pushedAt: String? = null,
    val acknowledgedCount: Int = 0,
)

@Serializable
data class AppNotification(
    val id: String,
    val tenantId: String,
    val title: String,
    val body: String,
    val type: String,                    // payment_overdue | expense_pending | attendance_alert | homework | audit | system | message | custom
    val priority: String,                // low | medium | high | urgent
    val source: String,                  // system | manual | workflow | schedule | audit
    val sourceLabel: String,
    val entityType: String? = null,
    val entityId: String? = null,
    val targetUserId: String? = null,
    val targetRole: String? = null,
    val triggeredAt: String? = null,
    val readAt: String? = null,
    val createdAt: String,
    val createdBy: String,
)

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

@Serializable
data class DebtSummary(
    val parentId: String,
    val parentName: String,
    val parentPhone: String,
    val studentCount: Int,
    val outstandingAmount: Long,
    val daysOverdue: Long,
    val bucket: String,                  // 0_30 | 31_60 | 61_90 | 91_180 | 180_plus
)

@Serializable
data class PricingConfig(
    val id: String,
    val tenantId: String,
    val isActive: Boolean,
    val registrationFee: Long,
    val latePenaltyPerDay: Long,
    val secondApronFee: Long,
    val updatedAt: String,
)

@Serializable
data class GradeLevelTuition(
    val id: String,
    val pricingConfigId: String,
    val gradeLevel: String,
    val annualAmount: Long,
    val tranche1: Long,
    val tranche2: Long,
    val tranche3: Long,
)
