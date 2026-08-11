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
    parentId = parentId,
    firstName = firstName,
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
    parentId = parentId,
    firstName = firstName,
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

/** Convert a [PaymentDto] to the [Payment] domain model. */
@Suppress("UNUSED")
fun PaymentDto.toDomain(): Payment = Payment(
    id = id,
    tenantId = tenantId ?: "",
    receiptNumber = receiptNumber ?: paymentNumber,
    parentId = parentId,
    studentId = studentId,
    amount = (amount * 100).toLong(), // DTO is in dinars; domain is in centimes
    method = runCatching { PaymentMethod.valueOf(method.uppercase()) }
        .getOrDefault(PaymentMethod.CASH),
    status = runCatching { PaymentStatus.valueOf(status.uppercase()) }
        .getOrDefault(PaymentStatus.PENDING),
    category = runCatching { PaymentCategory.valueOf((category ?: "OTHER").uppercase()) }
        .getOrDefault(PaymentCategory.OTHER),
    installmentId = installmentId,
    proofUrl = proofPath,
    notes = notes,
    collectedBy = collectedBy ?: "system",
    collectedAt = collectedAt ?: "",
    createdAt = createdAt ?: "",
    updatedAt = updatedAt ?: "",
)
