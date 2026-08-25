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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Mappers between Room entities and domain models.
 * Every entity → domain conversion is a pure function — no I/O, no side effects.
 */
object LocalMappers {

    fun ParentEntity.toDomain() = Parent(
        id = id, tenantId = tenantId, code = code,
        firstName = firstName, lastName = lastName, displayName = displayName,
        phone = phone,
        whatsapp = whatsapp, email = email, occupation = occupation,
        address = address, transportDestination = transportDestination,
        // TIER 4 FIX — cityTier surfaced to the domain (0028 parity).
        cityTier = cityTier,
        // Vault §04.03 — batch registration master-info fields.
        nationalId = nationalId,
        relationship = relationship,
        preferredLanguage = preferredLanguage, avatarUrl = avatarUrl,
        createdAt = createdAt, updatedAt = updatedAt,
    )

    fun StudentEntity.toDomain() = com.example.domain.model.Student(
        id = id, tenantId = tenantId, code = code, parentId = parentId,
        firstName = firstName, lastName = lastName, displayName = displayName,
        gender = gender,
        birthDate = birthDate, enrollmentDate = enrollmentDate,
        level = level, gradeLevel = gradeLevel, classId = classId,
        photoUrl = photoUrl, medicalNotes = medicalNotes, status = status,
        // TIER 2 R12 — pass through paymentPlan from Room entity to domain.
        // The column was added in MIGRATION_4_5; the desktop's Student model
        // has the same field.
        paymentPlan = com.example.core.PaymentPlan.fromCode(paymentPlan),
        createdAt = createdAt, updatedAt = updatedAt,
    )

    fun AcademicClassEntity.toDomain(enrolledCount: Int = 0) = AcademicClass(
        id = id, tenantId = tenantId, name = name, level = level,
        gradeYear = gradeYear, homeroomTeacherId = homeroomTeacherId,
        homeroomTeacherName = homeroomTeacherName, room = room,
        capacity = capacity ?: 0, enrolledCount = enrolledCount,
        academicYear = academicYear,
    )

    fun SubjectEntity.toDomain() = Subject(
        id = id, tenantId = tenantId, name = name, nameAr = null,
        code = code, level = level, coefficient = coefficient,
        isExtracurricular = isExtracurricular, passingGrade = passingGrade,
        // Vault §06.02 — surface the per-COMPONENT coefficients so the GPA
        // engine + the Subjects directory edit dialog can read + edit them.
        coefficientDevoir1 = coefficientDevoir1,
        coefficientDevoir2 = coefficientDevoir2,
        coefficientExamen = coefficientExamen,
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
        isExtracurricular = isExtracurricular,
        enteredBy = enteredBy, enteredAt = enteredAt,
        // Vault §06.02 — surface the per-COMPONENT coefficient snapshot so
        // the GPA fallback path ([computeOverallGpa] when subjectAverage is
        // null) can recompute the average using the SAME coefficients that
        // were in effect when the marks were entered.
        coefficientDevoir1 = coefficientDevoir1,
        coefficientDevoir2 = coefficientDevoir2,
        coefficientExamen = coefficientExamen,
    )

    fun HomeworkEntity.toDomain(): Homework {
        val attachments = try { Json.decodeFromString<List<String>>(attachmentsJson) } catch (_: Exception) { emptyList() }
        return Homework(
            id = id, tenantId = tenantId, classId = classId, subjectId = subjectId,
            subjectName = subjectName, teacherId = teacherId, teacherName = teacherName,
            title = title, description = description, dueDate = dueDate,
            attachments = attachments,
            // Vault §06.06 — academicYear + pushedAt are persisted since
            // MIGRATION_9_10 (legacy rows fall back to "" / null).
            academicYear = academicYear ?: "", createdAt = createdAt,
            pushedAt = pushedAt,
        )
    }

    fun PaymentEntity.toDomain() = Payment(
        id = id, tenantId = tenantId, receiptNumber = receiptNumber,
        parentId = parentId, studentId = studentId, amount = amount,
        method = PaymentMethod.fromCode(method),
        status = PaymentStatus.fromCodeOrDefault(status),
        category = PaymentCategory.fromCode(category), installmentId = installmentId,
        proofUrl = proofUrl, notes = notes,
        collectedBy = collectedBy, collectedAt = collectedAt,
        createdAt = createdAt, updatedAt = updatedAt,
    )

    fun InstallmentEntity.toDomain() = Installment(
        id = id, tenantId = tenantId, parentId = parentId, studentId = studentId,
        category = PaymentCategory.fromCode(category), label = label,
        amountDue = amountDue, amountPaid = amountPaid,
        // TIER 4 FIX (D14/R12) — pending-clearance bucket no longer dropped.
        amountPending = amountPending,
        dueDate = dueDate, paidDate = paidDate,
        status = PaymentStatus.fromCodeOrDefault(status),
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
        // CANONICAL-FINANCIAL-LOGIC.md §7.5 — metadata MUST be preserved.
        // Parsed from the metadataJson column; defaults to empty map on error.
        metadata = parseMetadataJson(metadataJson),
    )

    fun ExpenseEntity.toDomain() = Expense(
        id = id, tenantId = tenantId, requestCode = requestCode, title = title,
        description = description, amount = amount, category = category,
        payee = payee, status = status, submittedBy = submittedBy,
        submittedAt = submittedAt, approvedBy = approvedBy, approvedAt = approvedAt,
        approvalNote = notes, disbursedBy = null, disbursedAt = disbursedAt,
        proofUrl = proofUrl, anomalyScore = anomalyScore,
        finalSpentAmount = finalSpentAmount,
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

    // ── Routing mappers (vehicles / stops / trips) ───────────────────────

    fun VehicleEntity.toDomain() = com.example.domain.model.Vehicle(
        id = id,
        plate = plate,
        driverId = driverId,
        driverName = driverName,
        capacity = capacity,
        hasWheelchairAccess = hasWheelchairAccess,
    )

    fun RoutingStopEntity.toDomain() = com.example.domain.model.RoutingStop(
        id = id,
        studentId = studentId,
        studentName = studentName,
        address = address,
        lat = lat,
        lng = lng,
        shift = com.example.domain.model.RoutingShift.fromCode(shift),
        orderInRoute = orderInRoute,
        estimatedMinutesFromPrevious = estimatedMinutesFromPrevious,
    )

    fun RoutingStopEntity.toUpdatedEntity(
        orderInRoute: Int,
        estimatedMinutesFromPrevious: Double,
    ) = copy(
        orderInRoute = orderInRoute,
        estimatedMinutesFromPrevious = estimatedMinutesFromPrevious,
    )

    fun TripLogEntity.toDomain() = com.example.domain.model.TripLog(
        id = id,
        vehicleId = vehicleId,
        driverId = driverId,
        startedAt = startTime ?: createdAt,
        endedAt = endTime,
        stopsPlanned = stopCount,
        stopsCompleted = stopsCompleted,
        totalDistanceKm = distanceKm ?: 0.0,
        notes = notes,
    )

    // ── metadata JSON helpers ────────────────────────────────────────────
    //
    // CANONICAL-FINANCIAL-LOGIC.md §7.5 + §8.4 — the ledger entry's
    // metadata (tranche / level / gradeLevel / paymentPlan / academicCycle
    // / clubCategory / therapyKind / period / sessionCount / serviceQualifier
    // / pricingSource / reversedEntryId / reason) MUST survive the full
    // sync cycle: domain → entity → push → Supabase → pull → entity → domain.

    private val metadataJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        prettyPrint = false
    }

    fun parseMetadataJson(raw: String?): Map<String, Any?> {
        if (raw.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = metadataJson.parseToJsonElement(raw).let {
                (it as? kotlinx.serialization.json.JsonObject) ?: return@runCatching emptyMap()
            }
            obj.toDomainMap()
        }.getOrDefault(emptyMap())
    }

    fun serializeMetadataJson(metadata: Map<String, Any?>): String {
        if (metadata.isEmpty()) return "{}"
        return runCatching {
            val obj = buildJsonObject {
                for ((k, v) in metadata) {
                    when (v) {
                        null -> put(k, JsonNull)
                        is Boolean -> put(k, v)
                        is Number -> put(k, v)
                        is String -> put(k, v)
                        is Collection<*> -> {
                            val arr = kotlinx.serialization.json.buildJsonArray {
                                v.forEach { el ->
                                    when (el) {
                                        null -> add(JsonNull)
                                        // FIX (compile): JsonArrayBuilder.add only accepts
                                        // JsonElement — wrap primitives explicitly.
                                        is Boolean -> add(kotlinx.serialization.json.JsonPrimitive(el))
                                        is Number -> add(kotlinx.serialization.json.JsonPrimitive(el))
                                        is String -> add(kotlinx.serialization.json.JsonPrimitive(el))
                                        else -> add(kotlinx.serialization.json.JsonPrimitive(el.toString()))
                                    }
                                }
                            }
                            put(k, arr)
                        }
                        is Map<*, *> -> {
                            // nested map — flatten by re-encoding as a JSON object via recursion
                            val nested = serializeMetadataJson(v.entries.associate { (k2, v2) -> k2.toString() to v2 })
                            put(k, metadataJson.parseToJsonElement(nested))
                        }
                        else -> put(k, v.toString())
                    }
                }
            }
            metadataJson.encodeToString(JsonObject.serializer(), obj)
        }.getOrDefault("{}")
    }
}

// ── Kotlin serialization helpers for metadata JSON ──────────────────────
// Renamed `toMap()` → `toDomainMap()` to avoid shadowing JsonObject's
// built-in `Map<String, JsonElement>.toMap()` which returns the raw JSON model.
private fun kotlinx.serialization.json.JsonObject.toDomainMap(): Map<String, Any?> =
    this.entries.associate { (k, v) ->
        k to when (v) {
            is kotlinx.serialization.json.JsonNull -> null
            is kotlinx.serialization.json.JsonPrimitive -> {
                v.contentOrNull?.let { c ->
                    c.toBooleanOrNull() ?: c.toLongOrNull() ?: c.toDoubleOrNull() ?: c
                }
            }
            is kotlinx.serialization.json.JsonObject -> v.toDomainMap()
            is kotlinx.serialization.json.JsonArray ->
                v.map { el ->
                    when (el) {
                        is kotlinx.serialization.json.JsonPrimitive -> el.contentOrNull
                        is kotlinx.serialization.json.JsonObject -> el.toDomainMap()
                        else -> null
                    }
                }
            else -> null
        }
    }

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = if (this is kotlinx.serialization.json.JsonNull) null
            else this.content.ifEmpty { null }

private fun String.toBooleanOrNull(): Boolean? =
    when (this.lowercase()) { "true" -> true; "false" -> false; else -> null }
