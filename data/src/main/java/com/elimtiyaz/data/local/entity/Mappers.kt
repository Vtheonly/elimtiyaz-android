package com.elimtiyaz.data.local.entity

import com.elimtiyaz.domain.model.AcademicClass
import com.elimtiyaz.domain.model.AccountAdjustment
import com.elimtiyaz.domain.model.AppNotification
import com.elimtiyaz.domain.model.Assessment
import com.elimtiyaz.domain.model.AttendanceRecord
import com.elimtiyaz.domain.model.AttendanceSession
import com.elimtiyaz.domain.model.AuditEntry
import com.elimtiyaz.domain.model.ClassSubject
import com.elimtiyaz.domain.model.Expense
import com.elimtiyaz.domain.model.ExpenseCategory
import com.elimtiyaz.domain.model.Gender
import com.elimtiyaz.domain.model.Homework
import com.elimtiyaz.domain.model.Installment
import com.elimtiyaz.domain.model.NotificationType
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.Payment
import com.elimtiyaz.domain.model.PaymentCategory
import com.elimtiyaz.domain.model.Personnel
import com.elimtiyaz.domain.model.PersonnelStatus
import com.elimtiyaz.domain.model.PromotionDecision
import com.elimtiyaz.domain.model.ReleveEntry
import com.elimtiyaz.domain.model.RoutingShift
import com.elimtiyaz.domain.model.RoutingStop
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.model.StudentStatus
import com.elimtiyaz.domain.model.Subject
import com.elimtiyaz.domain.model.TripLog
import com.elimtiyaz.domain.model.Vehicle

/**
 * Bidirectional mappers between Room entities and domain models. Room entities
 * store enums as their `name` to avoid needing TypeConverters for every enum
 * — these helpers do the conversion.
 */

/** Map a [ParentEntity] to a domain [Parent]. */
fun ParentEntity.toDomain(): Parent = Parent(
    id = id, tenantId = tenantId, code = code, firstName = firstName, lastName = lastName,
    gender = runCatching { Gender.valueOf(gender) }.getOrDefault(Gender.Unspecified),
    phone = phone, whatsapp = whatsapp, email = email, occupation = occupation, address = address,
    cityTier = cityTier, preferredLanguage = preferredLanguage, avatarUrl = avatarUrl,
    createdAt = createdAt, updatedAt = updatedAt,
)

/** Map a domain [Parent] to a [ParentEntity]. */
fun Parent.toEntity(): ParentEntity = ParentEntity(
    id = id, tenantId = tenantId, code = code, firstName = firstName, lastName = lastName,
    gender = gender.name, phone = phone, whatsapp = whatsapp, email = email, occupation = occupation,
    address = address, cityTier = cityTier, preferredLanguage = preferredLanguage, avatarUrl = avatarUrl,
    createdAt = createdAt, updatedAt = updatedAt,
)

/** Map a [StudentEntity] to a domain [Student]. */
fun StudentEntity.toDomain(parent: Parent? = null): Student = Student(
    id = id, tenantId = tenantId, code = code, parentId = parentId, firstName = firstName,
    lastName = lastName, gender = runCatching { Gender.valueOf(gender) }.getOrDefault(Gender.Unspecified),
    birthDate = birthDate, enrollmentDate = enrollmentDate, level = level, gradeYear = gradeYear,
    classId = classId, photoUrl = photoUrl, medicalNotes = medicalNotes, transportTier = transportTier,
    status = runCatching { StudentStatus.valueOf(status) }.getOrDefault(StudentStatus.Active),
    createdAt = createdAt, updatedAt = updatedAt, parent = parent,
)

/** Map a domain [Student] to a [StudentEntity]. */
fun Student.toEntity(): StudentEntity = StudentEntity(
    id = id, tenantId = tenantId, code = code, parentId = parentId, firstName = firstName, lastName = lastName,
    gender = gender.name, birthDate = birthDate, enrollmentDate = enrollmentDate, level = level, gradeYear = gradeYear,
    classId = classId, photoUrl = photoUrl, medicalNotes = medicalNotes, transportTier = transportTier,
    status = status.name, createdAt = createdAt, updatedAt = updatedAt,
)

/** Map an [AcademicClassEntity] to a domain [AcademicClass]. */
fun AcademicClassEntity.toDomain(): AcademicClass = AcademicClass(
    id = id, tenantId = tenantId, name = name, level = level, gradeYear = gradeYear,
    homeroomTeacherId = homeroomTeacherId, homeroomTeacherName = homeroomTeacherName,
    room = room, capacity = capacity, enrolledCount = enrolledCount, academicYear = academicYear,
)

/** Map a domain [AcademicClass] to an [AcademicClassEntity]. */
fun AcademicClass.toEntity(): AcademicClassEntity = AcademicClassEntity(
    id = id, tenantId = tenantId, name = name, level = level, gradeYear = gradeYear,
    homeroomTeacherId = homeroomTeacherId, homeroomTeacherName = homeroomTeacherName,
    room = room, capacity = capacity, enrolledCount = enrolledCount, academicYear = academicYear,
)

/** Map a [SubjectEntity] to a domain [Subject]. */
fun SubjectEntity.toDomain(): Subject = Subject(
    id = id, tenantId = tenantId, name = name, nameAr = nameAr, code = code, level = level,
    coefficient = coefficient, isExtracurricular = isExtracurricular, passingGrade = passingGrade,
)

/** Map a domain [Subject] to a [SubjectEntity]. */
fun Subject.toEntity(): SubjectEntity = SubjectEntity(
    id = id, tenantId = tenantId, name = name, nameAr = nameAr, code = code, level = level,
    coefficient = coefficient, isExtracurricular = isExtracurricular, passingGrade = passingGrade,
)

/** Map a [ClassSubjectEntity] to a domain [ClassSubject]. */
fun ClassSubjectEntity.toDomain(): ClassSubject = ClassSubject(
    id = id, classId = classId, subjectId = subjectId, teacherId = teacherId, teacherName = teacherName,
    weeklyHours = weeklyHours, coefficient = coefficient,
)

/** Map a domain [ClassSubject] to a [ClassSubjectEntity]. */
fun ClassSubject.toEntity(): ClassSubjectEntity = ClassSubjectEntity(
    id = id, classId = classId, subjectId = subjectId, teacherId = teacherId, teacherName = teacherName,
    weeklyHours = weeklyHours, coefficient = coefficient,
)

/** Map an [AssessmentEntity] to a domain [Assessment]. */
fun AssessmentEntity.toDomain(): Assessment = Assessment(
    id = id, studentId = studentId, subjectId = subjectId, classId = classId, term = term,
    academicYear = academicYear, devoir1 = devoir1, devoir2 = devoir2, examen = examen,
    subjectAverage = subjectAverage, coefficient = coefficient, enteredBy = enteredBy, enteredAt = enteredAt,
)

/** Map a domain [Assessment] to an [AssessmentEntity]. */
fun Assessment.toEntity(): AssessmentEntity = AssessmentEntity(
    id = id, studentId = studentId, subjectId = subjectId, classId = classId, term = term,
    academicYear = academicYear, devoir1 = devoir1, devoir2 = devoir2, examen = examen,
    subjectAverage = subjectAverage, coefficient = coefficient, enteredBy = enteredBy, enteredAt = enteredAt,
)

/** Map an [AttendanceRecordEntity] to a domain [AttendanceRecord]. */
fun AttendanceRecordEntity.toDomain(): AttendanceRecord = AttendanceRecord(
    id = id, studentId = studentId, classId = classId, date = date,
    session = runCatching { AttendanceSession.valueOf(session) }.getOrDefault(AttendanceSession.Morning),
    status = status, note = note, recordedBy = recordedBy, recordedAt = recordedAt, syncedAt = syncedAt,
)

/** Map a domain [AttendanceRecord] to an [AttendanceRecordEntity]. */
fun AttendanceRecord.toEntity(): AttendanceRecordEntity = AttendanceRecordEntity(
    id = id, studentId = studentId, classId = classId, date = date, session = session.name, status = status,
    note = note, recordedBy = recordedBy, recordedAt = recordedAt, syncedAt = syncedAt,
)

/** Map a [HomeworkEntity] to a domain [Homework]. */
fun HomeworkEntity.toDomain(): Homework = Homework(
    id = id, classId = classId, subjectId = subjectId, subjectName = subjectName, teacherId = teacherId,
    teacherName = teacherName, title = title, description = description, dueDate = dueDate,
    attachments = attachments, academicYear = academicYear, createdAt = createdAt, pushedAt = pushedAt,
    acknowledgedCount = acknowledgedCount,
)

/** Map a domain [Homework] to a [HomeworkEntity]. */
fun Homework.toEntity(): HomeworkEntity = HomeworkEntity(
    id = id, classId = classId, subjectId = subjectId, subjectName = subjectName, teacherId = teacherId,
    teacherName = teacherName, title = title, description = description, dueDate = dueDate,
    attachments = attachments, academicYear = academicYear, createdAt = createdAt, pushedAt = pushedAt,
    acknowledgedCount = acknowledgedCount,
)

/** Map a [PaymentEntity] to a domain [Payment]. */
fun PaymentEntity.toDomain(): Payment = Payment(
    id = id, tenantId = tenantId, receiptNumber = receiptNumber, parentId = parentId, studentId = studentId,
    amount = amount, method = method, status = status,
    category = runCatching { PaymentCategory.valueOf(category) }.getOrDefault(PaymentCategory.Other),
    installmentId = installmentId, proofUrl = proofUrl, notes = notes, collectedBy = collectedBy,
    collectedAt = collectedAt, createdAt = createdAt, updatedAt = updatedAt,
)

/** Map a domain [Payment] to a [PaymentEntity]. */
fun Payment.toEntity(): PaymentEntity = PaymentEntity(
    id = id, tenantId = tenantId, receiptNumber = receiptNumber, parentId = parentId, studentId = studentId,
    amount = amount, method = method, status = status, category = category.name, installmentId = installmentId,
    proofUrl = proofUrl, notes = notes, collectedBy = collectedBy, collectedAt = collectedAt,
    createdAt = createdAt, updatedAt = updatedAt,
)

/** Map an [InstallmentEntity] to a domain [Installment]. */
fun InstallmentEntity.toDomain(): Installment = Installment(
    id = id, parentId = parentId, studentId = studentId,
    category = runCatching { PaymentCategory.valueOf(category) }.getOrDefault(PaymentCategory.Other),
    label = label, amountDue = amountDue, amountPaid = amountPaid, dueDate = dueDate, paidDate = paidDate,
    status = status,
)

/** Map a domain [Installment] to an [InstallmentEntity]. */
fun Installment.toEntity(): InstallmentEntity = InstallmentEntity(
    id = id, parentId = parentId, studentId = studentId, category = category.name, label = label,
    amountDue = amountDue, amountPaid = amountPaid, dueDate = dueDate, paidDate = paidDate, status = status,
)

/** Map an [ExpenseEntity] to a domain [Expense]. */
fun ExpenseEntity.toDomain(): Expense = Expense(
    id = id, tenantId = tenantId, requestCode = requestCode, title = title, description = description,
    amount = amount,
    category = runCatching { ExpenseCategory.valueOf(category) }.getOrDefault(ExpenseCategory.Other),
    payee = payee, status = status, submittedBy = submittedBy, submittedAt = submittedAt, approvedBy = approvedBy,
    approvedAt = approvedAt, approvalNote = approvalNote, disbursedBy = disbursedBy, disbursedAt = disbursedAt,
    proofUrl = proofUrl, proofUploadedBy = proofUploadedBy, proofUploadedAt = proofUploadedAt,
    anomalyScore = anomalyScore, anomalyNote = anomalyNote,
)

/** Map a domain [Expense] to an [ExpenseEntity]. */
fun Expense.toEntity(): ExpenseEntity = ExpenseEntity(
    id = id, tenantId = tenantId, requestCode = requestCode, title = title, description = description,
    amount = amount, category = category.name, payee = payee, status = status, submittedBy = submittedBy,
    submittedAt = submittedAt, approvedBy = approvedBy, approvedAt = approvedAt, approvalNote = approvalNote,
    disbursedBy = disbursedBy, disbursedAt = disbursedAt, proofUrl = proofUrl, proofUploadedBy = proofUploadedBy,
    proofUploadedAt = proofUploadedAt, anomalyScore = anomalyScore, anomalyNote = anomalyNote,
)

/** Map a [PersonnelEntity] to a domain [Personnel]. */
fun PersonnelEntity.toDomain(): Personnel = Personnel(
    id = id, tenantId = tenantId, firstName = firstName, lastName = lastName,
    staffCategory = runCatching { com.elimtiyaz.domain.model.StaffCategory.valueOf(staffCategory) }
        .getOrDefault(com.elimtiyaz.domain.model.StaffCategory.Teacher),
    phone = phone, email = email, hireDate = hireDate, salary = salary,
    weeklyHoursTarget = weeklyHoursTarget, weeklyHoursLogged = weeklyHoursLogged, avatarUrl = avatarUrl,
    status = runCatching { PersonnelStatus.valueOf(status) }.getOrDefault(PersonnelStatus.Active),
)

/** Map a domain [Personnel] to a [PersonnelEntity]. */
fun Personnel.toEntity(): PersonnelEntity = PersonnelEntity(
    id = id, tenantId = tenantId, firstName = firstName, lastName = lastName, staffCategory = staffCategory.name,
    phone = phone, email = email, hireDate = hireDate, salary = salary, weeklyHoursTarget = weeklyHoursTarget,
    weeklyHoursLogged = weeklyHoursLogged, avatarUrl = avatarUrl, status = status.name,
)

/** Map a [ReleveEntryEntity] to a domain [ReleveEntry]. */
fun ReleveEntryEntity.toDomain(): ReleveEntry = ReleveEntry(
    id = id, personnelId = personnelId, personnelName = personnelName, date = date, hoursIn = hoursIn,
    hoursOut = hoursOut, activity = activity, classId = classId, subjectId = subjectId, recordedAt = recordedAt,
)

/** Map a domain [ReleveEntry] to a [ReleveEntryEntity]. */
fun ReleveEntry.toEntity(): ReleveEntryEntity = ReleveEntryEntity(
    id = id, personnelId = personnelId, personnelName = personnelName, date = date, hoursIn = hoursIn,
    hoursOut = hoursOut, activity = activity, classId = classId, subjectId = subjectId, recordedAt = recordedAt,
)

/** Map an [AuditEntryEntity] to a domain [AuditEntry]. */
fun AuditEntryEntity.toDomain(): AuditEntry = AuditEntry(
    id = id, tenantId = tenantId, action = action, entityType = entityType, entityId = entityId,
    actorId = actorId, actorName = actorName, diff = diff, note = note, ipAddress = ipAddress,
    userAgent = userAgent, at = at,
)

/** Map a domain [AuditEntry] to an [AuditEntryEntity]. */
fun AuditEntry.toEntity(): AuditEntryEntity = AuditEntryEntity(
    id = id, tenantId = tenantId, action = action, entityType = entityType, entityId = entityId,
    actorId = actorId, actorName = actorName, diff = diff, note = note, ipAddress = ipAddress,
    userAgent = userAgent, at = at,
)

/** Map an [AppNotificationEntity] to a domain [AppNotification]. */
fun AppNotificationEntity.toDomain(): AppNotification = AppNotification(
    id = id, title = title, body = body,
    type = runCatching { NotificationType.valueOf(type) }.getOrDefault(NotificationType.System),
    entityType = entityType, entityId = entityId, readAt = readAt, createdAt = createdAt,
)

/** Map a domain [AppNotification] to an [AppNotificationEntity]. */
fun AppNotification.toEntity(): AppNotificationEntity = AppNotificationEntity(
    id = id, title = title, body = body, type = type.name, entityType = entityType, entityId = entityId,
    readAt = readAt, createdAt = createdAt,
)

/** Map a [RoutingStopEntity] to a domain [RoutingStop]. */
fun RoutingStopEntity.toDomain(): RoutingStop = RoutingStop(
    id = id, studentId = studentId, studentName = studentName, address = address, lat = lat, lng = lng,
    shift = runCatching { RoutingShift.valueOf(shift) }.getOrDefault(RoutingShift.Morning),
    orderInRoute = orderInRoute, estimatedMinutesFromPrevious = estimatedMinutesFromPrevious,
)

/** Map a domain [RoutingStop] to a [RoutingStopEntity]. */
fun RoutingStop.toEntity(): RoutingStopEntity = RoutingStopEntity(
    id = id, studentId = studentId, studentName = studentName, address = address, lat = lat, lng = lng,
    shift = shift.name, orderInRoute = orderInRoute, estimatedMinutesFromPrevious = estimatedMinutesFromPrevious,
)

/** Map a [VehicleEntity] to a domain [Vehicle]. */
fun VehicleEntity.toDomain(): Vehicle = Vehicle(
    id = id, plate = plate, driverId = driverId, driverName = driverName,
    capacity = capacity, hasWheelchairLift = hasWheelchairLift,
)

/** Map a domain [Vehicle] to a [VehicleEntity]. */
fun Vehicle.toEntity(): VehicleEntity = VehicleEntity(
    id = id, plate = plate, driverId = driverId, driverName = driverName,
    capacity = capacity, hasWheelchairLift = hasWheelchairLift,
)

/** Map a [TripLogEntity] to a domain [TripLog]. */
fun TripLogEntity.toDomain(): TripLog = TripLog(
    id = id, vehicleId = vehicleId, driverId = driverId, startedAt = startedAt, endedAt = endedAt,
    stopsPlanned = stopsPlanned, stopsCompleted = stopsCompleted, totalDistanceKm = totalDistanceKm, notes = notes,
)

/** Map a domain [TripLog] to a [TripLogEntity]. */
fun TripLog.toEntity(): TripLogEntity = TripLogEntity(
    id = id, vehicleId = vehicleId, driverId = driverId, startedAt = startedAt, endedAt = endedAt,
    stopsPlanned = stopsPlanned, stopsCompleted = stopsCompleted, totalDistanceKm = totalDistanceKm, notes = notes,
)

/** Construct a domain [AccountAdjustment] from a synthetic map row. */
fun Map<String, String?>.toAccountAdjustment(): AccountAdjustment = AccountAdjustment(
    id = this["id"].orEmpty(),
    parentId = this["parent_id"].orEmpty(),
    amount = this["amount"]?.toDoubleOrNull() ?: 0.0,
    reason = this["reason"].orEmpty(),
    approvedBy = this["approved_by"].orEmpty(),
    approvedAt = this["approved_at"].orEmpty(),
    receiptRef = this["receipt_ref"],
)

/** Convenience for the rare AcademicHistory mapping (used by promote flow). */
@Suppress("unused")
private fun PromotionDecision.safeName(): String = name
