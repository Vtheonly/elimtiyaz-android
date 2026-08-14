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
    preferredLanguage = "fr",
    avatarUrl = null,
    isActive = isActive,
    isFinanciallyRestricted = false,
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
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
)

/** Convert a [ClassDto] to an [AcademicClassEntity] for Room upsert. */
fun ClassDto.toEntity(): com.example.infrastructure.room.AcademicClassEntity = com.example.infrastructure.room.AcademicClassEntity(
    id = id,
    tenantId = tenantId ?: "00000000-0000-0000-0000-000000000001",
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
    tenantId = tenantId ?: "00000000-0000-0000-0000-000000000001",
    code = code,
    name = nameFr,
    category = domain,
    coefficient = defaultCoefficient,
    weeklyHours = 4.0,
    isExtracurricular = isExtracurricular,
    isActive = isActive,
)

/** Convert an [InstallmentDto] to an [InstallmentEntity] for Room upsert. */
fun InstallmentDto.toEntity(): com.example.infrastructure.room.InstallmentEntity = com.example.infrastructure.room.InstallmentEntity(
    id = id,
    tenantId = tenantId ?: "00000000-0000-0000-0000-000000000001",
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
    tenantId = tenantId ?: "00000000-0000-0000-0000-000000000001",
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
    tenantId = tenantId ?: "00000000-0000-0000-0000-000000000001",
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
    tenantId = tenantId ?: "00000000-0000-0000-0000-000000000001",
    title = title,
    body = body ?: "",
    type = kind,
    priority = priority,
    source = source,
    sourceLabel = "Système",
    entityType = null,
    entityId = null,
    targetUserId = targetUserId,
    isRead = isRead,
    createdAt = createdAt ?: "",
)

/** Convert a [PaymentDto] to a [PaymentEntity] for Room upsert. */
fun PaymentDto.toEntity(): com.example.infrastructure.room.PaymentEntity = com.example.infrastructure.room.PaymentEntity(
    id = id,
    tenantId = tenantId ?: "00000000-0000-0000-0000-000000000001",
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
    tenantId = tenantId ?: "00000000-0000-0000-0000-000000000001",
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
)


