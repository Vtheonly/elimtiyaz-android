package com.example.infrastructure.room

import com.example.core.LedgerEntry
import com.example.core.LedgerEntryType
import com.example.core.LedgerSourceType
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.domain.model.AcademicClass
import com.example.domain.model.AppNotification
import com.example.domain.model.Assessment
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AuditLog
import com.example.domain.model.Department
import com.example.domain.model.Expense
import com.example.domain.model.GradeLevelTuition
import com.example.domain.model.Homework
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.domain.model.Personnel
import com.example.domain.model.PricingConfig
import com.example.domain.model.PricingDiscount
import com.example.domain.model.Subject
import kotlinx.serialization.json.Json

/**
 * Mappers between Room entities and domain models.
 * Every entity → domain conversion is a pure function — no I/O, no side effects.
 */
object LocalMappers {

    fun ParentEntity.toDomain() = Parent(
        id = id, tenantId = tenantId, code = code,
        firstName = firstName, lastName = lastName, phone = phone,
        whatsapp = whatsapp, email = email, occupation = occupation,
        address = address, transportDestination = transportDestination,
        preferredLanguage = preferredLanguage, avatarUrl = avatarUrl,
        createdAt = createdAt, updatedAt = updatedAt,
    )

    fun StudentEntity.toDomain() = com.example.domain.model.Student(
        id = id, tenantId = tenantId, code = code, parentId = parentId,
        firstName = firstName, lastName = lastName, gender = gender,
        birthDate = birthDate, enrollmentDate = enrollmentDate,
        level = level, gradeLevel = gradeLevel, classId = classId,
        photoUrl = photoUrl, medicalNotes = medicalNotes, status = status,
        createdAt = createdAt, updatedAt = updatedAt,
    )

    fun AcademicClassEntity.toDomain(enrolledCount: Int = 0) = AcademicClass(
        id = id, tenantId = tenantId, name = name, level = level,
        gradeYear = gradeYear, homeroomTeacherId = homeroomTeacherId,
        homeroomTeacherName = homeroomTeacherName, room = room,
        capacity = capacity, enrolledCount = enrolledCount,
        academicYear = academicYear,
    )

    fun SubjectEntity.toDomain() = Subject(
        id = id, tenantId = tenantId, name = name, nameAr = null,
        code = code, level = "all", coefficient = coefficient,
        isExtracurricular = isExtracurricular,
    )

    fun AttendanceEntity.toDomain() = AttendanceRecord(
        id = id, tenantId = tenantId, studentId = studentId, classId = classId,
        date = date, session = session, status = status,
        note = note, recordedBy = recordedBy, recordedAt = recordedAt,
    )

    fun AssessmentEntity.toDomain() = Assessment(
        id = id, tenantId = tenantId, studentId = studentId, subjectId = subjectId,
        classId = classId, term = term, academicYear = academicYear,
        devoir1 = devoir1, devoir2 = devoir2, examen = examen,
        coefficient = coefficient, subjectAverage = subjectAverage,
        enteredBy = enteredBy, enteredAt = enteredAt,
    )

    fun HomeworkEntity.toDomain(): Homework {
        val attachments = try { Json.decodeFromString<List<String>>(attachmentsJson) } catch (_: Exception) { emptyList() }
        return Homework(
            id = id, tenantId = tenantId, classId = classId, subjectId = subjectId,
            subjectName = subjectName, teacherId = teacherId, teacherName = teacherName,
            title = title, description = description, dueDate = dueDate,
            attachments = attachments, academicYear = "", createdAt = createdAt,
        )
    }

    fun PaymentEntity.toDomain() = Payment(
        id = id, tenantId = tenantId, receiptNumber = receiptNumber,
        parentId = parentId, studentId = studentId, amount = amount,
        method = PaymentMethod.fromCode(method), status = PaymentStatus.fromCode(status),
        category = PaymentCategory.fromCode(category), installmentId = installmentId,
        proofUrl = proofUrl, notes = notes,
        collectedBy = collectedBy, collectedAt = collectedAt,
        createdAt = createdAt, updatedAt = updatedAt,
    )

    fun InstallmentEntity.toDomain() = Installment(
        id = id, tenantId = tenantId, parentId = parentId, studentId = studentId,
        category = PaymentCategory.fromCode(category), label = label,
        amountDue = amountDue, amountPaid = amountPaid,
        dueDate = dueDate, paidDate = paidDate,
        status = PaymentStatus.fromCode(status),
        academicCycle = academicCycle, customSchedule = customSchedule,
        customScheduleNote = customScheduleNote,
    )

    fun LedgerEntryEntity.toDomain() = LedgerEntry(
        id = id, tenantId = tenantId, accountId = accountId, parentId = parentId,
        studentId = studentId, category = PaymentCategory.fromCode(category),
        amount = amount, type = LedgerEntryType.fromCode(type),
        sourceType = LedgerSourceType.fromCode(sourceType), sourceId = sourceId,
        method = method?.let { PaymentMethod.fromCode(it) },
        receiptNumber = receiptNumber,
        paymentStatus = paymentStatus?.let { PaymentStatus.fromCode(it) },
        reversesId = reversesId, description = description,
        actorId = actorId, actorName = actorName, at = at,
        metadata = emptyMap(),
    )

    fun ExpenseEntity.toDomain() = Expense(
        id = id, tenantId = tenantId, requestCode = requestCode, title = title,
        description = description, amount = amount, category = category,
        payee = payee, status = status, submittedBy = submittedBy,
        submittedAt = submittedAt, approvedBy = approvedBy, approvedAt = approvedAt,
        approvalNote = notes, disbursedBy = null, disbursedAt = disbursedAt,
        proofUrl = proofUrl, anomalyScore = anomalyScore,
    )

    fun PersonnelEntity.toDomain() = Personnel(
        id = id, tenantId = tenantId, userId = null,
        firstName = firstName, lastName = lastName,
        staffCategory = role, roleId = role,
        departmentId = departmentId, position = departmentName ?: role,
        phone = phone ?: "", email = email,
        hireDate = hireDate ?: "", terminationDate = null,
        salary = null, status = status, avatarUrl = null,
        weeklyHoursTarget = weeklyHoursTarget,
    )

    fun DepartmentEntity.toDomain() = Department(
        id = id, tenantId = tenantId, name = name, description = description,
        headPersonnelId = headPersonnelId, parentDepartmentId = parentDepartmentId,
        colorHex = colorHex, archivedAt = archivedAt,
    )

    fun PricingConfigEntity.toDomain(discounts: List<PricingDiscountEntity>): PricingConfig = PricingConfig(
        id = id, tenantId = tenantId, isActive = isActive,
        registrationFee = registrationFee, latePenaltyPerDay = latePenaltyPerDay,
        secondApronFee = secondApronFee, updatedAt = updatedAt,
        discounts = discounts.map { it.toDomain() },
    )

    fun PricingDiscountEntity.toDomain() = PricingDiscount(
        id = id, tenantId = tenantId, code = code, label = label,
        amount = amount, discountType = discountType,
    )

    fun GradeLevelTuitionEntity.toDomain() = GradeLevelTuition(
        id = id, pricingConfigId = pricingConfigId, gradeLevel = gradeLevel,
        annualAmount = annualAmount,
        tranche1 = tranche1, tranche2 = tranche2, tranche3 = tranche3,
    )

    fun AuditLogEntity.toDomain() = AuditLog(
        id = id, tenantId = tenantId, action = action, entityType = entityType,
        entityId = entityId, actorId = actorId, actorName = actorName,
        actorRole = actorRole, beforeJson = beforeJson, afterJson = afterJson,
        note = note, occurredAt = createdAt,
    )

    fun NotificationEntity.toDomain() = AppNotification(
        id = id, tenantId = tenantId, title = title, body = body, type = type,
        priority = priority, source = source, sourceLabel = sourceLabel,
        entityType = entityType, entityId = entityId, targetUserId = targetUserId,
        targetRole = null, triggeredAt = null,
        readAt = if (isRead) createdAt else null, createdAt = createdAt,
        createdBy = "system",
    )
}
