package com.example.infrastructure.supabase

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Shared DTOs for the canonical Supabase PostgreSQL tables.
 * Perfectly mirrors the schema definitions provided by the user.
 */

@Serializable
data class TenantDto(
    @SerialName("id") val id: String,
    @SerialName("slug") val slug: String,
    @SerialName("name") val name: String,
    @SerialName("legal_name") val legalName: String? = null,
    @SerialName("tax_id") val taxId: String? = null,
    @SerialName("address") val address: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    @SerialName("country") val country: String = "DZ",
    @SerialName("phone") val phone: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("website") val website: String? = null,
    @SerialName("logo_path") val logoPath: String? = null,
    @SerialName("default_locale") val defaultLocale: String = "fr",
    @SerialName("default_currency") val defaultCurrency: String = "DZD",
    @SerialName("timezone") val timezone: String = "Africa/Algiers",
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("settings") val settings: JsonElement? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class ParentDto(
    @SerialName("id") val id: String = "",
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("parent_code") val parentCode: String? = null,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("last_name") val lastName: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("primary_phone") val primaryPhone: String = "",
    @SerialName("secondary_phone") val secondaryPhone: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("national_id") val nationalId: String? = null,
    @SerialName("occupation") val occupation: String? = null,
    @SerialName("address") val address: String? = null,
    @SerialName("city") val city: String? = null,
    @SerialName("postal_code") val postalCode: String? = null,
    @SerialName("relationship") val relationship: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("is_financially_restricted") val isFinanciallyRestricted: Boolean = false,
    @SerialName("auth_user_id") val authUserId: String? = null,
    @SerialName("transport_destination") val transportDestination: String? = null,
    @SerialName("city_tier") val cityTier: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class StudentDto(
    @SerialName("id") val id: String = "",
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("student_code") val studentCode: String? = null,
    @SerialName("first_name") val firstName: String = "",
    @SerialName("middle_name") val middleName: String? = null,
    @SerialName("last_name") val lastName: String = "",
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("date_of_birth") val dateOfBirth: String? = null,
    @SerialName("gender") val gender: String? = null,
    @SerialName("grade_level_id") val gradeLevelId: String? = null,
    @SerialName("grade_level_code") val gradeLevelCode: String? = null,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("enrollment_date") val enrollmentDate: String? = null,
    @SerialName("enrollment_status") val enrollmentStatus: String? = "active",
    @SerialName("medical_notes") val medicalNotes: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("auth_user_id") val authUserId: String? = null,
    @SerialName("transport_tier") val transportTier: String? = null,
    @SerialName("payment_plan") val paymentPlan: String? = "tranches",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class AcademicYearDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("label") val label: String,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    @SerialName("term_structure") val termStructure: String = "trimester",
    @SerialName("is_current") val isCurrent: Boolean = false,
    @SerialName("is_archived") val isArchived: Boolean = false,
    @SerialName("code") val code: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class AcademicLevelDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("cycle") val cycle: String,
    @SerialName("year_label") val yearLabel: String,
    @SerialName("year_number") val yearNumber: Int = 1,
    @SerialName("grade_code") val gradeCode: String,
    @SerialName("sort_order") val sortOrder: Int = 1,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("label_fr") val labelFr: String? = null,
    @SerialName("label_ar") val labelAr: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ClassDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("academic_year_id") val academicYearId: String? = null,
    @SerialName("academic_level_id") val academicLevelId: String? = null,
    @SerialName("section") val section: String = "A",
    @SerialName("code") val code: String,
    @SerialName("name") val name: String? = null,
    // TIER 4 FIX — nullable capacity (null = unlimited enrollment), matching
    // the desktop model + SQL. The previous non-null default of 30 fabricated
    // a limit the server never set.
    @SerialName("capacity") val capacity: Int? = null,
    @SerialName("homeroom_teacher_id") val homeroomTeacherId: String? = null,
    @SerialName("homeroom_teacher_name") val homeroomTeacherName: String? = null,
    @SerialName("room") val room: String? = null,
    @SerialName("grade_code") val gradeCode: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class SubjectDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("code") val code: String,
    @SerialName("name_fr") val nameFr: String,
    @SerialName("name_ar") val nameAr: String? = null,
    @SerialName("name_en") val nameEn: String? = null,
    @SerialName("domain") val domain: String = "scolarite",
    @SerialName("default_coefficient") val defaultCoefficient: Int = 1,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("cycle") val cycle: String? = null,
    @SerialName("passing_grade") val passingGrade: Double? = 10.0,
    @SerialName("is_extracurricular") val isExtracurricular: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class AssessmentDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("class_subject_id") val classSubjectId: String? = null,
    @SerialName("class_id") val classId: String? = null,
    @SerialName("subject_id") val subjectId: String? = null,
    @SerialName("student_id") val studentId: String? = null,
    @SerialName("term") val term: Int = 1,
    @SerialName("kind") val kind: String = "devoir_1",
    @SerialName("label") val label: String? = null,
    @SerialName("max_score") val maxScore: Double = 20.0,
    @SerialName("weight") val weight: Double = 1.0,
    @SerialName("coefficient") val coefficient: Double = 1.0,
    @SerialName("scheduled_at") val scheduledAt: String? = null,
    @SerialName("devoir1") val devoir1: Double? = null,
    @SerialName("devoir2") val devoir2: Double? = null,
    @SerialName("examen") val examen: Double? = null,
    @SerialName("subject_average") val subjectAverage: Double? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class GradeDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("student_id") val studentId: String,
    @SerialName("assessment_id") val assessmentId: String,
    @SerialName("class_subject_id") val classSubjectId: String? = null,
    @SerialName("score") val score: Double,
    @SerialName("subject_average") val subjectAverage: Double? = null,
    @SerialName("is_absent") val isAbsent: Boolean = false,
    @SerialName("entered_by") val enteredBy: String? = null,
    @SerialName("entered_at") val enteredAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class AttendanceRecordDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("student_id") val studentId: String,
    @SerialName("class_id") val classId: String,
    @SerialName("class_subject_id") val classSubjectId: String? = null,
    @SerialName("date") val date: String? = null,
    @SerialName("record_date") val recordDate: String? = null,
    @SerialName("session") val session: String = "morning",
    @SerialName("status") val status: String,
    @SerialName("arrival_time") val arrivalTime: String? = null,
    @SerialName("note") val note: String? = null,
    @SerialName("recorded_by") val recordedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class HomeworkDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("class_id") val classId: String,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("subject_name") val subjectName: String? = null,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("teacher_name") val teacherName: String? = null,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("due_date") val dueDate: String,
    @SerialName("academic_year") val academicYear: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class PaymentDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("payment_number") val paymentNumber: String,
    @SerialName("receipt_number") val receiptNumber: String? = null,
    @SerialName("parent_id") val parentId: String,
    @SerialName("student_id") val studentId: String? = null,
    @SerialName("invoice_id") val invoiceId: String? = null,
    @SerialName("installment_id") val installmentId: String? = null,
    @SerialName("amount") val amount: Double,
    @SerialName("method") val method: String,
    @SerialName("category") val category: String? = "other",
    @SerialName("status") val status: String,
    @SerialName("check_number") val checkNumber: String? = null,
    @SerialName("check_bank_name") val checkBankName: String? = null,
    @SerialName("check_issue_date") val checkIssueDate: String? = null,
    @SerialName("check_clearance_date") val checkClearanceDate: String? = null,
    @SerialName("transfer_reference") val transferReference: String? = null,
    @SerialName("transfer_source_bank") val transferSourceBank: String? = null,
    @SerialName("proof_path") val proofPath: String? = null,
    @SerialName("collected_at") val collectedAt: String? = null,
    @SerialName("collected_by") val collectedBy: String? = null,
    @SerialName("notes") val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class InstallmentDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("parent_id") val parentId: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("service_enrollment_id") val serviceEnrollmentId: String? = null,
    @SerialName("invoice_id") val invoiceId: String? = null,
    @SerialName("tranche_number") val trancheNumber: Int = 1,
    @SerialName("amount_due") val amountDue: Double,
    @SerialName("amount_paid") val amountPaid: Double = 0.0,
    @SerialName("amount_pending") val amountPending: Double = 0.0,
    @SerialName("due_date") val dueDate: String,
    @SerialName("paid_date") val paidDate: String? = null,
    @SerialName("status") val status: String = "unpaid",
    @SerialName("academic_cycle") val academicCycle: String? = null,
    @SerialName("label") val label: String? = null,
    @SerialName("category") val category: String = "tuition",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class LedgerEntryDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("entry_number") val entryNumber: String? = null,
    @SerialName("parent_id") val parentId: String,
    @SerialName("student_id") val studentId: String? = null,
    @SerialName("account_id") val accountId: String,
    @SerialName("entry_type") val entryType: String,
    @SerialName("amount") val amount: Double,
    @SerialName("category") val category: String,
    @SerialName("description") val description: String? = null,
    @SerialName("entry_date") val entryDate: String? = null,
    @SerialName("source_type") val sourceType: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("method") val method: String? = null,
    @SerialName("receipt_number") val receiptNumber: String? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("reverses_id") val reversesId: String? = null,
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("actor_name") val actorName: String? = null,
    @SerialName("at") val at: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    // CANONICAL-FINANCIAL-LOGIC.md §7.5 + §8.4 — pull-side metadata.
    // The Supabase `ledger_entries.metadata` JSONB column is parsed as a
    // raw JsonElement to preserve any field the server stores; the entity
    // mapper stores the verbatim JSON string in `metadataJson`.
    @SerialName("metadata") val metadata: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class DepartmentDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("code") val code: String,
    @SerialName("name_fr") val nameFr: String,
    @SerialName("label_ar") val labelAr: String? = null,
    @SerialName("color_hex") val colorHex: String? = null,
    @SerialName("head_personnel_id") val headPersonnelId: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PersonnelDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("personnel_code") val personnelCode: String,
    @SerialName("user_id") val userId: String? = null,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("middle_name") val middleName: String? = null,
    @SerialName("staff_category") val staffCategory: String = "teaching",
    @SerialName("department_id") val departmentId: String? = null,
    @SerialName("position") val position: String? = null,
    @SerialName("hire_date") val hireDate: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("base_salary") val baseSalary: Double? = null,
    @SerialName("primary_phone") val primaryPhone: String? = null,
    @SerialName("email") val email: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class ReleveEntryDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("personnel_id") val personnelId: String,
    @SerialName("activity_type") val activityType: String = "course",
    @SerialName("class_id") val classId: String? = null,
    @SerialName("class_subject_id") val classSubjectId: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("clock_in_at") val clockInAt: String,
    @SerialName("clock_out_at") val clockOutAt: String? = null,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    @SerialName("recorded_by") val recordedBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ExpenseCategoryDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("code") val code: String,
    @SerialName("label_fr") val labelFr: String,
    @SerialName("label_ar") val labelAr: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ExpenseTicketDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("ticket_number") val ticketNumber: String,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("requested_amount") val requestedAmount: Double,
    @SerialName("final_spent_amount") val finalSpentAmount: Double? = null,
    @SerialName("justification") val justification: String? = null,
    @SerialName("urgency") val urgency: String = "medium",
    @SerialName("status") val status: String = "pending_approval",
    @SerialName("submitted_by") val submittedBy: String,
    @SerialName("submitted_at") val submittedAt: String? = null,
    @SerialName("approved_by") val approvedBy: String? = null,
    @SerialName("receipt_path") val receiptPath: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class NotificationDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("kind") val kind: String = "info",
    @SerialName("title") val title: String,
    @SerialName("body") val body: String? = null,
    @SerialName("priority") val priority: String = "medium",
    @SerialName("source") val source: String = "system",
    @SerialName("target_user_id") val targetUserId: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("triggered_at") val triggeredAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class TaskDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("title") val title: String,
    @SerialName("description") val description: String? = null,
    @SerialName("status") val status: String = "pending",
    @SerialName("priority") val priority: String = "medium",
    @SerialName("department_id") val departmentId: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("progress") val progress: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class DeviceTokenDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("token") val token: String,
    @SerialName("platform") val platform: String = "android",
    @SerialName("app_version") val appVersion: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class PricingConfigDto(
    @SerialName("id") val id: String,
    @SerialName("tenant_id") val tenantId: String? = null,
    @SerialName("academic_year_id") val academicYearId: String,
    @SerialName("label") val label: String,
    @SerialName("registration_fee") val registrationFee: Double = 5000.0,
    @SerialName("late_penalty_per_day") val latePenaltyPerDay: Double = 100.0,
    @SerialName("second_apron_fee") val secondApronFee: Double = 2000.0,
    @SerialName("early_payment_bonus_pct") val earlyPaymentBonusPct: Double = 5.0,
    @SerialName("early_payment_deadline") val earlyPaymentDeadline: String? = null,
    @SerialName("is_active") val isActive: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class GradeLevelTuitionDto(
    @SerialName("id") val id: String,
    @SerialName("pricing_config_id") val pricingConfigId: String,
    @SerialName("academic_level_id") val academicLevelId: String,
    @SerialName("annual_amount") val annualAmount: Double,
    @SerialName("tranche_1_amount") val tranche1Amount: Double,
    @SerialName("tranche_2_amount") val tranche2Amount: Double,
    @SerialName("tranche_3_amount") val tranche3Amount: Double,
)

@Serializable
data class TransportDestinationDto(
    @SerialName("id") val id: String,
    @SerialName("pricing_config_id") val pricingConfigId: String,
    @SerialName("code") val code: String,
    @SerialName("label_fr") val labelFr: String,
    @SerialName("label_ar") val labelAr: String? = null,
    @SerialName("annual_amount") val annualAmount: Double,
    @SerialName("tranche_1_amount") val tranche1Amount: Double,
    @SerialName("tranche_2_amount") val tranche2Amount: Double,
    @SerialName("tranche_3_amount") val tranche3Amount: Double,
)

@Serializable
data class DiscountDto(
    @SerialName("id") val id: String,
    @SerialName("pricing_config_id") val pricingConfigId: String,
    @SerialName("code") val code: String,
    @SerialName("label_fr") val labelFr: String,
    @SerialName("discount_type") val discountType: String,
    @SerialName("amount") val amount: Double,
    @SerialName("applies_to") val appliesTo: String = "total",
    @SerialName("is_active") val isActive: Boolean = true,
)

