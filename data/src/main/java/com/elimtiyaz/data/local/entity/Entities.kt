package com.elimtiyaz.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity mirror of the `parents` Supabase table. Persisted locally so
 * the CRM roster remains available offline and refreshes via the sync worker.
 */
@Entity(tableName = "parents")
data class ParentEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val code: String,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val phone: String,
    val whatsapp: String?,
    val email: String?,
    val occupation: String?,
    val address: String?,
    val cityTier: String?,
    val preferredLanguage: String,
    val avatarUrl: String?,
    val createdAt: String,
    val updatedAt: String,
)

/** Room entity for the `students` table. */
@Entity(tableName = "students")
data class StudentEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val code: String,
    val parentId: String,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val birthDate: String,
    val enrollmentDate: String,
    val level: String,
    val gradeYear: Int,
    val classId: String?,
    val photoUrl: String?,
    val medicalNotes: String?,
    val transportTier: String?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
)

/** Room entity for the `academic_classes` table. */
@Entity(tableName = "academic_classes")
data class AcademicClassEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val name: String,
    val level: String,
    val gradeYear: Int,
    val homeroomTeacherId: String?,
    val homeroomTeacherName: String?,
    val room: String?,
    val capacity: Int,
    val enrolledCount: Int,
    val academicYear: String,
)

/** Room entity for the `subjects` table. */
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val name: String,
    val nameAr: String?,
    val code: String,
    val level: String,
    val coefficient: Double,
    val isExtracurricular: Boolean,
    val passingGrade: Double,
)

/** Room entity for the `class_subjects` join table. */
@Entity(tableName = "class_subjects")
data class ClassSubjectEntity(
    @PrimaryKey val id: String,
    val classId: String,
    val subjectId: String,
    val teacherId: String?,
    val teacherName: String?,
    val weeklyHours: Int,
    val coefficient: Double,
)

/** Room entity for the `assessments` table — captures grades. */
@Entity(tableName = "assessments")
data class AssessmentEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val subjectId: String,
    val classId: String,
    val term: String,
    val academicYear: String,
    val devoir1: Double?,
    val devoir2: Double?,
    val examen: Double?,
    val subjectAverage: Double?,
    val coefficient: Double,
    val enteredBy: String,
    val enteredAt: String,
)

/** Room entity for the `attendance_records` table. */
@Entity(tableName = "attendance_records")
data class AttendanceRecordEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val classId: String,
    val date: String,
    val session: String,
    val status: String,
    val note: String?,
    val recordedBy: String,
    val recordedAt: String,
    val syncedAt: String?,
)

/** Room entity for the `homework` table. */
@Entity(tableName = "homework")
data class HomeworkEntity(
    @PrimaryKey val id: String,
    val classId: String,
    val subjectId: String,
    val subjectName: String,
    val teacherId: String,
    val teacherName: String,
    val title: String,
    val description: String,
    val dueDate: String,
    val attachments: List<String>,
    val academicYear: String,
    val createdAt: String,
    val pushedAt: String?,
    val acknowledgedCount: Int,
)

/** Room entity for the `payments` table. */
@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val receiptNumber: String,
    val parentId: String,
    val studentId: String?,
    val amount: Double,
    val method: String,
    val status: String,
    val category: String,
    val installmentId: String?,
    val proofUrl: String?,
    val notes: String?,
    val collectedBy: String,
    val collectedAt: String,
    val createdAt: String,
    val updatedAt: String,
)

/** Room entity for the `installments` table. */
@Entity(tableName = "installments")
data class InstallmentEntity(
    @PrimaryKey val id: String,
    val parentId: String,
    val studentId: String,
    val category: String,
    val label: String,
    val amountDue: Double,
    val amountPaid: Double,
    val dueDate: String,
    val paidDate: String?,
    val status: String,
)

/** Room entity for the `expenses` table. */
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val requestCode: String,
    val title: String,
    val description: String,
    val amount: Double,
    val category: String,
    val payee: String,
    val status: String,
    val submittedBy: String,
    val submittedAt: String,
    val approvedBy: String?,
    val approvedAt: String?,
    val approvalNote: String?,
    val disbursedBy: String?,
    val disbursedAt: String?,
    val proofUrl: String?,
    val proofUploadedBy: String?,
    val proofUploadedAt: String?,
    val anomalyScore: Double?,
    val anomalyNote: String?,
)

/** Room entity for the `personnel` table. */
@Entity(tableName = "personnel")
data class PersonnelEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val firstName: String,
    val lastName: String,
    val staffCategory: String,
    val phone: String,
    val email: String?,
    val hireDate: String,
    val salary: Double?,
    val weeklyHoursTarget: Int,
    val weeklyHoursLogged: Int,
    val avatarUrl: String?,
    val status: String,
)

/** Room entity for the `releve_entries` table. */
@Entity(tableName = "releve_entries")
data class ReleveEntryEntity(
    @PrimaryKey val id: String,
    val personnelId: String,
    val personnelName: String,
    val date: String,
    val hoursIn: Double,
    val hoursOut: Double?,
    val activity: String,
    val classId: String?,
    val subjectId: String?,
    val recordedAt: String,
)

/** Room entity for the `audit_log` table. */
@Entity(tableName = "audit_log")
data class AuditEntryEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val actorId: String,
    val actorName: String,
    val diff: String?,
    val note: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val at: String,
)

/** Room entity for the `notifications` table. */
@Entity(tableName = "notifications")
data class AppNotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val type: String,
    val entityType: String?,
    val entityId: String?,
    val readAt: String?,
    val createdAt: String,
)

/** Room entity for the `routing_stops` table. */
@Entity(tableName = "routing_stops")
data class RoutingStopEntity(
    @PrimaryKey val id: String,
    val studentId: String,
    val studentName: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val shift: String,
    val orderInRoute: Int,
    val estimatedMinutesFromPrevious: Double,
)

/** Room entity for the `vehicles` table. */
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey val id: String,
    val plate: String,
    val driverId: String,
    val driverName: String,
    val capacity: Int,
    val hasWheelchairLift: Boolean,
)

/** Room entity for the `trip_logs` table. */
@Entity(tableName = "trip_logs")
data class TripLogEntity(
    @PrimaryKey val id: String,
    val vehicleId: String,
    val driverId: String,
    val startedAt: String,
    val endedAt: String?,
    val stopsPlanned: Int,
    val stopsCompleted: Int,
    val totalDistanceKm: Double,
    val notes: String?,
)

/**
 * Offline write queue — every failed Supabase write is persisted here and
 * replayed by [com.elimtiyaz.data.sync.SyncQueueWorker] when connectivity
 * returns. Per master plan §09 the sync queue is mobile-side only and never
 * exported.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val id: String,
    val tableName: String,
    val operation: String,          // insert / update / delete
    val payloadJson: String,        // serialised DTO
    val createdAt: String,
    val attempts: Int = 0,
    val lastError: String? = null,
)
