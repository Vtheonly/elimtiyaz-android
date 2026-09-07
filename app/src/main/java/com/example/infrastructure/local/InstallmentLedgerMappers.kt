package com.example.infrastructure.local

import com.example.BuildConfig
import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.core.Permission
import com.example.core.Result
import com.example.core.Role
import com.example.core.Session
import com.example.core.allocatePaymentToInstallments
import com.example.core.createChargeEntry
import com.example.core.createPaymentEntry
import com.example.core.createReversalEntry
import com.example.core.deriveAccountId
import com.example.core.generateEntryId
import com.example.core.LedgerEngine
import com.example.core.WaterfallInstallment
import com.example.domain.model.Parent
import com.example.domain.model.Student
import com.example.domain.model.Payment
import com.example.domain.model.Installment
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.BatchRegisterResult
import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.CreateStudentInput
import com.example.domain.repository.CollectPaymentInput
import com.example.domain.repository.InstallmentRepository
import com.example.domain.repository.LedgerRepository
import com.example.domain.repository.ParentRepository
import com.example.domain.repository.PaymentRepository
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.UpdateParentInput
import com.example.domain.repository.UpdateStudentInput
import com.example.infrastructure.room.AuditLogDao
import com.example.infrastructure.room.AuditLogEntity
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.room.InstallmentDao
import com.example.infrastructure.room.InstallmentEntity
import com.example.infrastructure.room.LedgerEntryDao
import com.example.infrastructure.room.LedgerEntryEntity
import com.example.infrastructure.room.LocalMappers
import com.example.infrastructure.room.ParentDao
import com.example.infrastructure.room.ParentEntity
import com.example.infrastructure.room.PaymentDao
import com.example.infrastructure.room.PaymentEntity
import com.example.infrastructure.room.StudentDao
import com.example.infrastructure.room.StudentEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─── Helper extensions ──────────────────────────────────────────────────────

internal fun com.example.core.LedgerEntry.toEntity() = LedgerEntryEntity(
    id = id, tenantId = tenantId, accountId = accountId, parentId = parentId,
    studentId = studentId, category = category.code, amount = amount, type = type.code,
    sourceType = sourceType.code, sourceId = sourceId, method = method?.code,
    receiptNumber = receiptNumber, paymentStatus = paymentStatus?.code,
    reversesId = reversesId, description = description, actorId = actorId,
    actorName = actorName, at = at,
    // CANONICAL-FINANCIAL-LOGIC.md §7.5 + §8.4 — persist metadata so pull-side
    // replay has access to tranche/level/gradeLevel/paymentPlan/academicCycle
    // /clubCategory/therapyKind/period/sessionCount/serviceQualifier/pricingSource
    // /reversedEntryId/reason.
    metadataJson = com.example.infrastructure.room.LocalMappers.serializeMetadataJson(metadata),
)


internal fun inst(tenantId: String, id: String, parentId: String, studentId: String, category: String, label: String, amountDue: Long, dueDate: String, now: String) = InstallmentEntity(
    id = id, tenantId = tenantId, parentId = parentId, studentId = studentId,
    category = category, label = label, amountDue = amountDue, amountPaid = 0L, amountPending = 0L,
    dueDate = dueDate, paidDate = null,
    status = if (dueDate < now) "overdue" else "pending",
    academicCycle = null, customSchedule = false, customScheduleNote = null,
    createdAt = now, updatedAt = now,
)
