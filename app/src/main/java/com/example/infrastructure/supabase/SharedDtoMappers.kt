package com.example.infrastructure.supabase

import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.domain.model.Student
import com.example.infrastructure.room.ParentEntity
import com.example.infrastructure.room.StudentEntity

/**
 * Mappers from the shared Supabase DTOs ([ParentDto], [StudentDto], [PaymentDto],
 * [LedgerEntryDto]) to the Android domain models ([Parent], [Student], [Payment])
 * and to the Room entities ([ParentEntity], [StudentEntity]).
 *
 * These mappers are the BRIDGE between the shared Supabase schema (migration 0027
 * + 0028) and the Android local cache. They ensure the Android app reads EXACTLY
 * the same data shape the Desktop app writes — same column names, same semantics,
 * same displayName rule.
 *
 * Migration 0028 added these columns to the shared schema:
 *   - parents.transport_destination, parents.city_tier
 *   - students.grade_level_code, students.transport_tier, students.payment_plan
 * The mappers below propagate these fields into the domain models + Room entities
 * so the Android UI shows the COMPLETE data the Desktop imported.
 *
 * TENANT CONVENTION (DTO-TENANT, session 18 — closes the T-051 out-of-scope
 * note): PULL-side mappers default a NULL server tenant_id to the EMPTY
 * STRING — never to the demo tenant UUID. The canonical tables carry
 * tenant_id NOT NULL, so a null here is a defensive/anomalous case, and
 * stamping it with the demo UUID (the old behaviour) silently attributed
 * server rows to the demo tenant, contaminating the store exactly the way
 * WEAK-012 did on the query side. Nothing on the local read path filters
 * by tenantId (verified), so "" cannot hide rows; pushes use the
 * session-stamped tenantId from AuditContext (T-051), not these values.
 * The demo tenant remains ONLY in AuditContext.DEMO_TENANT_ID (the
 * signed-out seeding fallback) and DatabaseSeeder (the demo sandbox).
 */

/** Convert a [ParentDto] (Supabase row) to the [Parent] domain model. */
fun ParentDto.toDomain(): Parent = Parent(
    id = id,
    tenantId = tenantId ?: "",
    code = parentCode ?: "PAR-?",
    firstName = firstName,
    lastName = lastName,
    displayName = displayName,
    phone = primaryPhone,
    whatsapp = secondaryPhone,
    email = email,
    occupation = occupation,
    address = address,
    transportDestination = transportDestination,
    // Vault §04.03 — batch registration master-info fields (backend parity).
    nationalId = nationalId,
    relationship = relationship,
    preferredLanguage = "fr",
    avatarUrl = null,
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
)

/** Convert a [ParentDto] to a [ParentEntity] for Room upsert. */
fun ParentDto.toEntity(): ParentEntity = ParentEntity(
    id = id,
    tenantId = tenantId ?: "",
    code = parentCode ?: "PAR-?",
    firstName = firstName,
    lastName = lastName,
    displayName = displayName,
    phone = primaryPhone,
    whatsapp = secondaryPhone,
    email = email,
    occupation = occupation,
    address = address,
    transportDestination = transportDestination,
    // TIER 4 FIX — preserve the server values instead of hardcoding:
    //   * cityTier drives transport pricing on desktop (0028 schema).
    //   * isFinanciallyRestricted is a server-set CRM restriction flag.
    cityTier = cityTier,
    // Vault §04.03 — master-info fields pulled from the server.
    nationalId = nationalId,
    relationship = relationship,
    preferredLanguage = "fr",
    avatarUrl = null,
    isActive = isActive,
    isFinanciallyRestricted = isFinanciallyRestricted,
    activationCode = null,
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
)

/** Convert a [StudentDto] (Supabase row) to the [Student] domain model. */
fun StudentDto.toDomain(): Student = Student(
    id = id,
    tenantId = tenantId ?: "",
    code = studentCode ?: "ELV-?",
    parentId = parentId ?: "",
    firstName = firstName.ifBlank { displayName ?: "Élève" },
    lastName = lastName,
    displayName = displayName,
    // The Student domain model uses `gender: String` (not an enum). The DB
    // column is "male"/"female"/null. Default to "unspecified" when null.
    gender = gender ?: "unspecified",
    birthDate = dateOfBirth ?: "",
    enrollmentDate = enrollmentDate ?: "",
    // `level` on the domain is primaire|cem|lycee. The new `grade_level_code`
    // column (migration 0028) stores the canonical code ("1ap", "CE1", ...).
    // We store the same value in both `level` and `gradeLevel` — the domain
    // layer's academicLevelFromGradeLevel() helper can derive the academic
    // level from the grade level code when needed.
    level = gradeLevelCode ?: "1ap",
    gradeLevel = gradeLevelCode ?: "1ap",
    classId = classId,
    photoUrl = null,
    medicalNotes = medicalNotes,
    status = enrollmentStatus ?: "active",
    // TIER 2 R12 — pass through `paymentPlan` from the shared Supabase
    // schema (migration 0028 added the `payment_plan` column). The DTO
    // already parsed the field — the domain layer was just dropping it.
    paymentPlan = com.example.core.PaymentPlan.fromCode(paymentPlan),
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
)

/** Convert a [StudentDto] to a [StudentEntity] for Room upsert. */
fun StudentDto.toEntity(): StudentEntity = StudentEntity(
    id = id,
    tenantId = tenantId ?: "",
    code = studentCode ?: "ELV-?",
    parentId = parentId ?: "",
    firstName = firstName.ifBlank { displayName ?: "Élève" },
    lastName = lastName,
    displayName = displayName,
    gender = gender ?: "unspecified",
    birthDate = dateOfBirth ?: "",
    enrollmentDate = enrollmentDate ?: "",
    level = gradeLevelCode ?: "1ap",
    gradeLevel = gradeLevelCode ?: "1ap",
    classId = classId,
    photoUrl = null,
    medicalNotes = medicalNotes,
    status = enrollmentStatus ?: "active",
    // TIER 2 R12 — store `paymentPlan` in the Room entity so the domain
    // layer's `StudentEntity.toDomain()` can pass it through. The column
    // was added by `MIGRATION_4_5`.
    paymentPlan = paymentPlan ?: "tranches",
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
)

/** Convert a [ClassDto] to an [AcademicClassEntity] for Room upsert. */
fun ClassDto.toEntity(): com.example.infrastructure.room.AcademicClassEntity = com.example.infrastructure.room.AcademicClassEntity(
    id = id,
    tenantId = tenantId ?: "",
    code = code,
    name = name ?: "Classe $section",
    level = gradeCode ?: "1ap",
    gradeYear = 1,
    gradeLevel = gradeCode ?: "1ap",
    section = section,
    room = room ?: "Salle 1",
    capacity = capacity,
    homeroomTeacherId = homeroomTeacherId ?: "ens-1",
    homeroomTeacherName = homeroomTeacherName ?: "Professeur",
    academicYear = academicYearId ?: "2024-2025",
    isActive = isActive,
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
)

/** Convert a [SubjectDto] to a [SubjectEntity] for Room upsert. */
fun SubjectDto.toEntity(): com.example.infrastructure.room.SubjectEntity = com.example.infrastructure.room.SubjectEntity(
    id = id,
    tenantId = tenantId ?: "",
    code = code,
    name = nameFr,
    category = domain,
    coefficient = defaultCoefficient.toDouble(),
    weeklyHours = 4.0,
    isExtracurricular = isExtracurricular,
    isActive = isActive,
    // Vault §06.02 (iteration 2) — pull the per-COMPONENT coefficients
    // from the shared subject schema when the backend exposes them. Null
    // (backend hasn't migrated yet) → fall back to the historical (1, 1, 2)
    // defaults so the Android GPA computation stays bit-identical with the
    // previous build.
    coefficientDevoir1 = coefficientDevoir1 ?: 1.0,
    coefficientDevoir2 = coefficientDevoir2 ?: 1.0,
    coefficientExamen = coefficientExamen ?: 2.0,
)

/** Convert an [InstallmentDto] to an [InstallmentEntity] for Room upsert. */
fun InstallmentDto.toEntity(): com.example.infrastructure.room.InstallmentEntity = com.example.infrastructure.room.InstallmentEntity(
    id = id,
    tenantId = tenantId ?: "",
    parentId = parentId,
    studentId = studentId,
    category = category,
    label = label ?: "Tranche $trancheNumber",
    amountDue = (amountDue * 100).toLong(),
    amountPaid = (amountPaid * 100).toLong(),
    amountPending = (amountPending * 100).toLong(),
    dueDate = dueDate,
    paidDate = paidDate,
    status = status,
    academicCycle = academicCycle,
    customSchedule = false,
    customScheduleNote = null,
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
)

/** Convert a [DepartmentDto] to a [DepartmentEntity] for Room upsert. */
fun DepartmentDto.toEntity(): com.example.infrastructure.room.DepartmentEntity = com.example.infrastructure.room.DepartmentEntity(
    id = id,
    tenantId = tenantId ?: "",
    name = nameFr,
    description = description,
    headPersonnelId = headPersonnelId,
    parentDepartmentId = null,
    colorHex = colorHex ?: "#2563EB",
    archivedAt = null,
)

/** Convert a [PersonnelDto] to a [PersonnelEntity] for Room upsert. */
fun PersonnelDto.toEntity(): com.example.infrastructure.room.PersonnelEntity = com.example.infrastructure.room.PersonnelEntity(
    id = id,
    tenantId = tenantId ?: "",
    code = personnelCode,
    firstName = firstName,
    lastName = lastName,
    role = staffCategory,
    departmentId = departmentId ?: "dep-pedagogie",
    departmentName = "Pédagogie",
    phone = primaryPhone,
    email = email,
    status = if (isActive) "active" else "inactive",
    hireDate = hireDate,
    weeklyHoursTarget = 18,
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
)

/** Convert a [NotificationDto] to a [NotificationEntity] for Room upsert. */
fun NotificationDto.toEntity(): com.example.infrastructure.room.NotificationEntity = com.example.infrastructure.room.NotificationEntity(
    id = id,
    tenantId = tenantId ?: "",
    title = title,
    body = body ?: "",
    type = kind,
    priority = priority,
    source = source,
    sourceLabel = "Système",
    entityType = null,
    entityId = null,
    targetUserId = targetUserId,
    // T-039 / NOTIF-105: preserve the broadcast role so eviction works.
    targetRole = targetRole,
    // T-181 (T-173b / NOTIF-200): record the server's dismissal state so the
    // cache is honest (active pulls keep null — the pull filter guarantees
    // only non-dismissed rows arrive).
    dismissedAt = dismissedAt,
    isRead = isRead,
    createdAt = createdAt ?: "",
)

/** Convert a [WorkflowRunDto] to a [WorkflowRunEntity] for Room upsert. */
fun WorkflowRunDto.toEntity(): com.example.infrastructure.room.WorkflowRunEntity = com.example.infrastructure.room.WorkflowRunEntity(
    id = id,
    tenantId = tenantId ?: "",
    workflowId = workflowId,
    workflowName = workflowName ?: workflowId,
    // T-054 (WEAK-008): keep the server's REAL trigger (the column was
    // dropped at this boundary before — every run read back "manual").
    trigger = trigger ?: "manual",
    status = status,
    startedBy = startedBy ?: "system",
    startedAt = startedAt ?: "",
    finishedAt = finishedAt,
    resultJson = resultJson,
    errorMessage = errorMessage,
)

/** Convert a [PaymentDto] to a [PaymentEntity] for Room upsert. */
fun PaymentDto.toEntity(): com.example.infrastructure.room.PaymentEntity = com.example.infrastructure.room.PaymentEntity(
    id = id,
    tenantId = tenantId ?: "",
    receiptNumber = receiptNumber ?: paymentNumber,
    parentId = parentId,
    studentId = studentId,
    amount = (amount * 100).toLong(),
    method = method.lowercase(),
    status = status.lowercase(),
    category = category ?: "tuition",
    installmentId = installmentId,
    proofUrl = proofPath,
    checkNumber = checkNumber,
    checkBankName = checkBankName,
    checkIssueDate = checkIssueDate,
    checkClearanceDate = checkClearanceDate,
    transferReference = transferReference,
    transferSourceBank = transferSourceBank,
    notes = notes,
    collectedBy = collectedBy ?: "system",
    collectedBy_name = "Système",
    collectedAt = collectedAt ?: createdAt ?: "",
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
)

/** Convert a [LedgerEntryDto] to a [LedgerEntryEntity] for Room upsert. */
fun LedgerEntryDto.toEntity(): com.example.infrastructure.room.LedgerEntryEntity = com.example.infrastructure.room.LedgerEntryEntity(
    id = id,
    tenantId = tenantId ?: "",
    accountId = accountId,
    parentId = parentId,
    studentId = studentId,
    category = category,
    amount = (amount * 100).toLong(),
    type = entryType,
    sourceType = sourceType ?: "payment",
    sourceId = sourceId ?: id,
    method = method,
    receiptNumber = receiptNumber,
    paymentStatus = paymentStatus,
    reversesId = reversesId,
    description = description ?: "",
    actorId = actorId ?: "per-admin",
    actorName = actorName ?: "Administrateur",
    at = at ?: createdAt ?: "",
    // CANONICAL-FINANCIAL-LOGIC.md §7.5 + §8.4 — preserve pull-side metadata
    // verbatim. The DTO stores it as a JsonElement (so any structure the
    // server sends is accepted); we serialize back to a string for Room.
    metadataJson = metadata?.let { element ->
        runCatching {
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonElement.serializer(),
                element,
            )
        }.getOrDefault("{}")
    } ?: "{}",
)



// ─── T-039 / HOMEWORK-103 — academic pull mappers (canonical shapes) ─────────
//
// The pull layer historically fetched ONLY the financial cluster
// (parents/students/payments/ledger/installments + org tables). The academic
// tables (homework migration 0029, attendance_records 0041, assessments
// 0041) were never pulled — Android could never see homework/attendance/
// grades created on the desktop. These mappers feed the new pull functions
// in PullSyncRepository (pullHomework / pullAttendance / pullAssessments).

/** Convert a [HomeworkDto] (canonical `homework` row) to a [HomeworkEntity]. */
fun HomeworkDto.toEntity(): com.example.infrastructure.room.HomeworkEntity = com.example.infrastructure.room.HomeworkEntity(
    id = id,
    tenantId = tenantId ?: "",
    classId = classId,
    subjectId = subjectId,
    subjectName = subjectName ?: "",
    teacherId = teacherId ?: "",
    teacherName = teacherName ?: "",
    title = title,
    description = description,
    dueDate = dueDate,
    // jsonb array → the local JSON-string convention ("[]" when absent).
    attachmentsJson = attachments?.let { el ->
        runCatching {
            kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonElement.serializer(),
                el,
            )
        }.getOrNull()
    } ?: "[]",
    createdAt = createdAt ?: "",
    academicYear = academicYear,
    pushedAt = pushedAt,
)

/** Convert an [AttendanceRecordDto] (canonical `attendance_records` row) to an [AttendanceEntity]. */
fun AttendanceRecordDto.toEntity(): com.example.infrastructure.room.AttendanceEntity = com.example.infrastructure.room.AttendanceEntity(
    id = id,
    tenantId = tenantId ?: "",
    studentId = studentId,
    classId = classId,
    // 0041 column is record_date; the legacy `date` alias is kept as fallback.
    date = recordDate ?: date ?: "",
    session = session,
    status = status,
    arrivalTime = arrivalTime,
    note = note,
    recordedBy = recordedBy ?: "",
    // The server row carries no recorded-by NAME — the local column stays
    // empty for pulled rows (the local create path fills it with the actor).
    recordedBy_name = "",
    recordedAt = createdAt ?: "",
)

/** Convert an [AssessmentDto] (canonical 0041 assessments row) to an [AssessmentEntity]. */
fun AssessmentDto.toEntity(): com.example.infrastructure.room.AssessmentEntity {
    // WIRE: the DB column is INTEGER 1|2|3; the local domain uses "T1"|"T2"|"T3"
    // (the same inverse of pushGrade's T?→int mapping).
    val termWire = "T" + term.coerceIn(1, 3)
    return com.example.infrastructure.room.AssessmentEntity(
        id = id,
        tenantId = tenantId ?: "",
        studentId = studentId ?: "",
        subjectId = subjectId ?: "",
        classId = classId ?: "",
        term = termWire,
        academicYear = academicYear ?: "",
        devoir1 = devoir1,
        devoir2 = devoir2,
        examen = examen,
        coefficient = coefficient,
        isExtracurricular = false,
        subjectAverage = subjectAverage,
        enteredBy = enteredBy ?: "",
        enteredAt = enteredAt ?: createdAt ?: "",
        coefficientDevoir1 = coefficientDevoir1 ?: 1.0,
        coefficientDevoir2 = coefficientDevoir2 ?: 1.0,
        coefficientExamen = coefficientExamen ?: 2.0,
    )
}
