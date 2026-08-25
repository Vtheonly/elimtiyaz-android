package com.example.infrastructure.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local source-of-truth entities — Room is the PRIMARY store for this build.
 *
 * The mobile app is designed to work offline-first. These entities persist
 * real business data (parents, students, classes, payments, installments,
 * ledger entries, attendance, grades, audit logs, etc.) so that every UI
 * screen is backed by real records and real calculations — not hardcoded
 * dummy numbers.
 *
 * Mirrors the desktop's Supabase schema (`supabase/migrations/`) field-by-field
 * so the business logic, calculations, and financial mathematics produce
 * identical numbers on both platforms.
 */

// ─── CRM ─────────────────────────────────────────────────────────────────────

@Entity(tableName = "parents", indices = [Index("code", unique = true), Index("phone")])
data class ParentEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val code: String,
    val firstName: String,
    val lastName: String,
    /** COMPLETE display name as imported (migration 0027). UI shows this verbatim when non-null. */
    val displayName: String? = null,
    val phone: String,
    val whatsapp: String?,
    val email: String?,
    val occupation: String?,
    val address: String?,
    val transportDestination: String?,
    val preferredLanguage: String,
    val avatarUrl: String?,
    val isActive: Boolean,
    // TIER 4 FIX — cityTier persisted (0028 schema parity).
    val cityTier: String? = null,
    val isFinanciallyRestricted: Boolean,
    val activationCode: String?,
    val createdAt: String,
    val updatedAt: String,
) {
    /**
     * The complete display name — mirrors the `Parent.fullName` helper on the
     * domain model. UI code MUST use this extension instead of
     * `firstName + " " + lastName` because the latter produces a blank
     * `" "` string for parents imported with only `displayName` set
     * (migration 0027 — the importer stores the full NOM column as
     * `displayName` when TUTEUR is empty).
     *
     * Behavior:
     *   - If `displayName` is non-blank → return it verbatim.
     *   - Otherwise → return `firstName + " " + lastName` (filtered for blanks).
     *   - If both are blank → return "—" so the UI never renders an empty name.
     */
    val fullName: String
        get() {
            val dn = displayName?.trim().orEmpty()
            if (dn.isNotEmpty()) return dn
            return listOf(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifEmpty { "—" }
        }
}

@Entity(tableName = "students", indices = [Index("code", unique = true), Index("parentId"), Index("classId"), Index("gradeLevel")])
data class StudentEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val code: String,
    val parentId: String,
    val firstName: String,
    val lastName: String,
    /** COMPLETE display name as imported (migration 0027). UI shows this verbatim when non-null. */
    val displayName: String? = null,
    val gender: String,
    val birthDate: String,
    val enrollmentDate: String,
    val level: String,
    val gradeLevel: String,
    val classId: String?,
    val photoUrl: String?,
    val medicalNotes: String?,
    val status: String,
    /**
     * TIER 2 R12 — billing plan. Mirrors `StudentEntity.payment_plan`
     * in the Supabase schema (migration 0028) and the desktop's
     * `Student.paymentPlan` field. Default `"tranches"` matches the
     * desktop's default for students imported without an explicit plan.
     *
     * The 10% early-annual discount (CANONICAL-FINANCIAL-LOGIC.md §5
     * rule 3) cannot be evaluated without this field — students on
     * the `full_annual` plan who pay before June 30 qualify.
     */
    val paymentPlan: String = "tranches",
    val createdAt: String,
    val updatedAt: String,
) {
    /** Mirrors [ParentEntity.fullName] — see that property for the rationale. */
    val fullName: String
        get() {
            val dn = displayName?.trim().orEmpty()
            if (dn.isNotEmpty()) return dn
            return listOf(firstName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifEmpty { "—" }
        }
}

// ─── Academic ────────────────────────────────────────────────────────────────

@Entity(tableName = "classes", indices = [Index("code", unique = true), Index("gradeLevel")])
data class AcademicClassEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val code: String,
    val name: String,
    val level: String,
    val gradeYear: Int,
    val gradeLevel: String,
    val section: String?,
    val room: String?,
    // TIER 4 FIX — nullable: desktop models capacity as `number | null`
    // (null = unlimited enrollment). The non-null Int column made a
    // null-capacity class fail deserialization.
    val capacity: Int?,
    val homeroomTeacherId: String?,
    val homeroomTeacherName: String?,
    val academicYear: String,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "subjects", indices = [Index("code")])
data class SubjectEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val code: String,
    val name: String,
    val category: String,
    // TIER 4 FIX — Double (REAL): SQL is NUMERIC(4,2), desktop is `number`.
    // The previous Int column silently truncated decimal coefficients.
    val coefficient: Double,
    val weeklyHours: Double,
    val isExtracurricular: Boolean,
    val isActive: Boolean,
    // FIX (broken level filter): level was hardcoded to "all" in the mapper,
    // so the directory's level chips always showed empty lists.
    val level: String = "all",
    val passingGrade: Double = 10.0,
)

@Entity(tableName = "attendance", indices = [Index("studentId"), Index("classId"), Index("date")])
data class AttendanceEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val studentId: String,
    val classId: String,
    val date: String,
    val session: String,
    val status: String,
    val arrivalTime: String?,
    val note: String?,
    val recordedBy: String,
    val recordedBy_name: String,
    val recordedAt: String,
)

@Entity(tableName = "assessments", indices = [Index("studentId"), Index("subjectId"), Index("classId"), Index("term")])
data class AssessmentEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val studentId: String,
    val subjectId: String,
    val classId: String,
    val term: String,
    val academicYear: String,
    val devoir1: Double?,
    val devoir2: Double?,
    val examen: Double?,
    // TIER 4 FIX — Double coefficient (NUMERIC(4,2) parity) + isExtracurricular
    // so computeOverallGpa can apply the canonical exclusion rule.
    val coefficient: Double,
    val isExtracurricular: Boolean = false,
    val subjectAverage: Double?,
    val enteredBy: String,
    val enteredAt: String,
)

@Entity(tableName = "homework", indices = [Index("classId"), Index("subjectId"), Index("dueDate")])
data class HomeworkEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val classId: String,
    val subjectId: String,
    val subjectName: String,
    val teacherId: String,
    val teacherName: String,
    val title: String,
    val description: String,
    val dueDate: String,
    val attachmentsJson: String,
    val createdAt: String,
)

// ─── Finance ─────────────────────────────────────────────────────────────────

@Entity(tableName = "payments", indices = [Index("receiptNumber", unique = true), Index("parentId"), Index("studentId"), Index("status")])
data class PaymentEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val receiptNumber: String,
    val parentId: String,
    val studentId: String?,
    val amount: Long,
    val method: String,
    val status: String,
    val category: String,
    val installmentId: String?,
    val proofUrl: String?,
    val checkNumber: String?,
    val checkBankName: String?,
    val checkIssueDate: String?,
    val checkClearanceDate: String?,
    val transferReference: String?,
    val transferSourceBank: String?,
    val notes: String?,
    // TIER 4 FIX (v2 audit D13 / R13) — expected-vs-excess tracking for
    // partial / overpayments. Amounts are centimes (Long), matching `amount`.
    val expectedAmount: Long? = null,
    val excessAmount: Long? = null,
    val excessRemark: String? = null,
    val collectedBy: String,
    val collectedBy_name: String,
    val collectedAt: String,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "installments", indices = [Index("parentId"), Index("studentId"), Index("status"), Index("dueDate")])
data class InstallmentEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val parentId: String,
    val studentId: String?,
    val category: String,
    val label: String,
    val amountDue: Long,
    val amountPaid: Long,
    val amountPending: Long,
    val dueDate: String,
    val paidDate: String?,
    val status: String,
    val academicCycle: String?,
    val customSchedule: Boolean,
    val customScheduleNote: String?,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "ledger_entries", indices = [Index("accountId"), Index("parentId"), Index("studentId"), Index("type"), Index("at")])
data class LedgerEntryEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val accountId: String,
    val parentId: String,
    val studentId: String?,
    val category: String,
    val amount: Long,
    val type: String,
    val sourceType: String,
    val sourceId: String,
    val method: String?,
    val receiptNumber: String?,
    val paymentStatus: String?,
    val reversesId: String?,
    val description: String,
    val actorId: String,
    val actorName: String,
    val at: String,
    // CANONICAL-FINANCIAL-LOGIC.md §7.5 + §8.4 — metadata MUST be preserved
    // through the full sync cycle. Stored as TEXT (JSON-serialized);
    // mappers parse it back to a Map<String, Any?> on domain conversion.
    val metadataJson: String = "{}",
)

@Entity(tableName = "expenses", indices = [Index("requestCode", unique = true), Index("status"), Index("submittedBy")])
data class ExpenseEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val requestCode: String,
    val title: String,
    val description: String,
    val amount: Long,
    val category: String,
    val payee: String,
    val status: String,
    val submittedBy: String,
    val submittedByName: String,
    val submittedAt: String,
    val approvedBy: String?,
    val approvedAt: String?,
    val disbursedAt: String?,
    val settledAt: String?,
    val proofUrl: String?,
    val urgency: String,
    val anomalyScore: Double,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
    // TIER 3 R18 FIX: previously `settleProof()` accepted a `finalAmount`
    // parameter but silently dropped it — the column didn't exist on the
    // entity. The local Room schema now matches the Supabase schema
    // (which has had `final_spent_amount` since migration 0028).
    val finalSpentAmount: Long? = null,
)

// ─── Personnel ───────────────────────────────────────────────────────────────

@Entity(tableName = "personnel", indices = [Index("code")])
data class PersonnelEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val code: String,
    val firstName: String,
    val lastName: String,
    val role: String,
    val departmentId: String?,
    val departmentName: String?,
    val phone: String?,
    val email: String?,
    val status: String,
    val hireDate: String?,
    val weeklyHoursTarget: Int,
    val createdAt: String,
    val updatedAt: String,
)

@Entity(tableName = "departments", indices = [Index("name")])
data class DepartmentEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val name: String,
    val description: String?,
    val headPersonnelId: String?,
    val parentDepartmentId: String?,
    val colorHex: String?,
    val archivedAt: String?,
)

// ─── Pricing ─────────────────────────────────────────────────────────────────

@Entity(tableName = "pricing_config")
data class PricingConfigEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val isActive: Boolean,
    val registrationFee: Long,
    val latePenaltyPerDay: Long,
    val secondApronFee: Long,
    val canteenTermFee: Long,
    val uniformFee: Long,
    val booksFee: Long,
    val updatedAt: String,
)

@Entity(tableName = "pricing_discounts", indices = [Index("code")])
data class PricingDiscountEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val code: String,
    val label: String,
    val amount: Long,
    val discountType: String,
    val isActive: Boolean,
)

@Entity(tableName = "grade_level_tuition", indices = [Index("gradeLevel")])
data class GradeLevelTuitionEntity(
    @PrimaryKey val id: String,
    val pricingConfigId: String,
    val gradeLevel: String,
    val annualAmount: Long,
    val tranche1: Long,
    val tranche2: Long,
    val tranche3: Long,
)

@Entity(tableName = "transport_pricing", indices = [Index("destination")])
data class TransportPricingEntity(
    @PrimaryKey val id: String,
    val pricingConfigId: String,
    val destination: String,
    val annualAmount: Long,
    val tranche1: Long,
    val tranche2: Long,
    val tranche3: Long,
)

// ─── Notifications & Audit ───────────────────────────────────────────────────

@Entity(tableName = "notifications", indices = [Index("targetUserId"), Index("priority"), Index("createdAt")])
data class NotificationEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val title: String,
    val body: String,
    val type: String,
    val priority: String,
    val source: String,
    val sourceLabel: String,
    val entityType: String?,
    val entityId: String?,
    val targetUserId: String?,
    val isRead: Boolean,
    val createdAt: String,
)

@Entity(tableName = "audit_logs", indices = [Index("entityType"), Index("entityId"), Index("actorId"), Index("createdAt")])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val actorId: String,
    val actorName: String,
    val actorRole: String?,
    val beforeJson: String?,
    val afterJson: String?,
    val note: String?,
    val createdAt: String,
)

// ─── Routing (optional, for driver dashboard) ────────────────────────────────

@Entity(tableName = "trip_logs", indices = [Index("driverId"), Index("date")])
data class TripLogEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val driverId: String,
    val driverName: String,
    /** The vehicle this trip was driven with ("" for legacy rows). */
    val vehicleId: String = "",
    val date: String,
    val startTime: String?,
    val endTime: String?,
    /** Number of stops PLANNED when the trip started. */
    val stopCount: Int,
    /** Number of stops actually COMPLETED when the trip ended. */
    val stopsCompleted: Int = 0,
    val studentIdsJson: String,
    val distanceKm: Double?,
    val status: String,
    val notes: String?,
    val createdAt: String,
)

/**
 * A transport vehicle used for student pickup/drop-off rounds.
 * Backs the driver-mode routing hub — real Room rows (not a stub list).
 */
@Entity(tableName = "vehicles", indices = [Index("plate", unique = true)])
data class VehicleEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val plate: String,
    val driverId: String?,
    val driverName: String?,
    val capacity: Int,
    val hasWheelchairAccess: Boolean = false,
    val isActive: Boolean = true,
    val createdAt: String,
)

/**
 * A single pickup/drop-off stop on a routing plan.
 * `shift` wire codes: morning | afternoon | both (mirrors [com.example.domain.model.RoutingShift]).
 */
@Entity(tableName = "routing_stops", indices = [Index("studentId"), Index("shift")])
data class RoutingStopEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val studentId: String,
    val studentName: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val shift: String,
    val orderInRoute: Int = 0,
    val estimatedMinutesFromPrevious: Double = 0.0,
    val isActive: Boolean = true,
    val createdAt: String,
)

/**
 * Per-class subject assignment (subject + optional teacher + weekly hours +
 * coefficient). Backs `SubjectRepository.assignSubjectToClass` — previously a
 * silent no-op that returned success without persisting anything.
 */
@Entity(tableName = "class_subjects", indices = [Index("classId"), Index("subjectId")])
data class ClassSubjectEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val classId: String,
    val subjectId: String,
    val teacherId: String?,
    val weeklyHours: Int,
    val coefficient: Double,
    val createdAt: String,
)

@Entity(tableName = "releve_entries", indices = [Index("personnelId"), Index("date")])
data class ReleveEntryEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val personnelId: String,
    val personnelName: String,
    val date: String,
    val activityType: String,
    val description: String,
    val durationMinutes: Int,
    val recordedBy: String,
    val recordedAt: String,
)

@Entity(tableName = "workflow_runs", indices = [Index("status"), Index("startedAt")])
data class WorkflowRunEntity(
    @PrimaryKey val id: String,
    val tenantId: String,
    val workflowId: String,
    val workflowName: String,
    val status: String,
    val startedBy: String,
    val startedAt: String,
    val finishedAt: String?,
    val resultJson: String?,
    val errorMessage: String?,
)
