package com.example.infrastructure.room

import com.example.core.LedgerEntry
import com.example.core.LedgerEntryType
import com.example.core.LedgerSourceType
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.domain.model.Student

/**
 * Mappers between domain models and Room cache entities.
 *
 * Cache entities store enum-typed fields as their `.name` strings to keep
 * the schema simple and migration-stable. Conversion is bidirectional and
 * lossless (any unknown enum string maps to a safe default).
 */

// ── Parent ────────────────────────────────────────────────────────────

fun Parent.toCacheEntity(): ParentCacheEntity = ParentCacheEntity(
    id = id, tenantId = tenantId, code = code,
    firstName = firstName, lastName = lastName, displayName = displayName, phone = phone,
    whatsapp = whatsapp, email = email, occupation = occupation,
    address = address, transportDestination = transportDestination,
    preferredLanguage = preferredLanguage, avatarUrl = avatarUrl,
    createdAt = createdAt, updatedAt = updatedAt,
    syncedAt = System.currentTimeMillis(),
)

fun ParentCacheEntity.toDomain(): Parent = Parent(
    id = id, tenantId = tenantId, code = code,
    firstName = firstName, lastName = lastName, displayName = displayName, phone = phone,
    whatsapp = whatsapp, email = email, occupation = occupation,
    address = address, transportDestination = transportDestination,
    preferredLanguage = preferredLanguage, avatarUrl = avatarUrl,
    createdAt = createdAt, updatedAt = updatedAt,
)

// ── Student ───────────────────────────────────────────────────────────

fun Student.toCacheEntity(): StudentCacheEntity = StudentCacheEntity(
    id = id, tenantId = tenantId, code = code, parentId = parentId,
    firstName = firstName, lastName = lastName, displayName = displayName, gender = gender,
    birthDate = birthDate, enrollmentDate = enrollmentDate,
    level = level, gradeLevel = gradeLevel, classId = classId,
    photoUrl = photoUrl, medicalNotes = medicalNotes, status = status,
    createdAt = createdAt, updatedAt = updatedAt,
    syncedAt = System.currentTimeMillis(),
)

fun StudentCacheEntity.toDomain(): Student = Student(
    id = id, tenantId = tenantId, code = code, parentId = parentId,
    firstName = firstName, lastName = lastName, displayName = displayName, gender = gender,
    birthDate = birthDate, enrollmentDate = enrollmentDate,
    level = level, gradeLevel = gradeLevel, classId = classId,
    photoUrl = photoUrl, medicalNotes = medicalNotes, status = status,
    createdAt = createdAt, updatedAt = updatedAt,
)

// ── Payment ───────────────────────────────────────────────────────────

fun Payment.toCacheEntity(): PaymentCacheEntity = PaymentCacheEntity(
    id = id, tenantId = tenantId, receiptNumber = receiptNumber,
    parentId = parentId, studentId = studentId, amount = amount,
    method = method.name, status = status.name, category = category.name,
    installmentId = installmentId, proofUrl = proofUrl, notes = notes,
    collectedBy = collectedBy, collectedAt = collectedAt,
    createdAt = createdAt, updatedAt = updatedAt,
    syncedAt = System.currentTimeMillis(),
)

fun PaymentCacheEntity.toDomain(): Payment = Payment(
    id = id, tenantId = tenantId, receiptNumber = receiptNumber,
    parentId = parentId, studentId = studentId, amount = amount,
    method = runCatching { PaymentMethod.valueOf(method) }.getOrDefault(PaymentMethod.CASH),
    status = runCatching { PaymentStatus.valueOf(status) }.getOrDefault(PaymentStatus.PENDING),
    // TIER 4 FIX (D50) — total `fromCode` (unknown → OTHER) instead of the
    // valueOf+runCatching pattern (valueOf expects the ENUM name, not the
    // wire code — every non-enum-name code silently coerced to OTHER).
    category = PaymentCategory.fromCode(category),
    installmentId = installmentId, proofUrl = proofUrl, notes = notes,
    collectedBy = collectedBy, collectedAt = collectedAt,
    createdAt = createdAt, updatedAt = updatedAt,
)

// ── LedgerEntry ───────────────────────────────────────────────────────

fun LedgerEntry.toCacheEntity(): LedgerCacheEntity = LedgerCacheEntity(
    id = id, tenantId = tenantId, accountId = accountId,
    parentId = parentId, studentId = studentId,
    category = category.name, amount = amount, type = type.name,
    sourceType = sourceType.name, sourceId = sourceId,
    method = method?.name, receiptNumber = receiptNumber,
    paymentStatus = paymentStatus?.name, reversesId = reversesId,
    description = description, actorId = actorId, actorName = actorName,
    entryDate = at, syncedAt = System.currentTimeMillis(),
)

fun LedgerCacheEntity.toDomain(): LedgerEntry = LedgerEntry(
    id = id, tenantId = tenantId, accountId = accountId,
    parentId = parentId, studentId = studentId,
    category = runCatching { PaymentCategory.valueOf(category) }.getOrDefault(PaymentCategory.OTHER),
    amount = amount,
    type = LedgerEntryType.fromCode(type),
    sourceType = LedgerSourceType.fromCode(sourceType),
    sourceId = sourceId,
    method = method?.let { PaymentMethod.fromCode(it) },
    receiptNumber = receiptNumber,
    paymentStatus = paymentStatus?.let { PaymentStatus.fromCode(it) },
    reversesId = reversesId,
    description = description, actorId = actorId, actorName = actorName,
    at = entryDate,
    // TIER 4 FIX — parse the persisted metadata (added in MIGRATION_6_7)
    // instead of dropping it. Falls back to an empty map for legacy rows.
    metadata = com.example.infrastructure.room.LocalMappers.parseMetadataJson(metadataJson),
)
