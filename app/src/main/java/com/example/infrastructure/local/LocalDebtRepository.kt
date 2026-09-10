package com.example.infrastructure.local

import com.example.core.Errors
import com.example.core.Result
import com.example.core.absenceAlertThreshold
import com.example.core.currentTermWindow
import com.example.core.agingBucketFromDays
import com.example.core.daysBetweenFloor
import com.example.core.formatDzd
import com.example.core.LedgerEngine
import com.example.domain.model.AcademicClass
import com.example.domain.model.AppNotification
import com.example.domain.model.Assessment
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AuditLog
import com.example.domain.model.ClassRollCallStatus
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DashboardOperationalAlert
import com.example.domain.model.DebtSummary
import com.example.domain.model.Department
import com.example.domain.model.Expense
import com.example.domain.model.GradeLevelTuition
import com.example.domain.model.Homework
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.domain.model.PaymentMethodSummary
import com.example.domain.model.Personnel
import com.example.domain.model.PricingConfig
import com.example.domain.model.ReleveEntry
import com.example.domain.model.Student
import com.example.domain.model.Subject
import com.example.domain.repository.AuditFilter
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.CreateClassInput
import com.example.domain.repository.CreateDepartmentInput
import com.example.domain.repository.CreatePersonnelInput
import com.example.domain.repository.CreateSubjectInput
import com.example.domain.repository.DashboardRepository
import com.example.domain.repository.DebtRepository
import com.example.domain.repository.DepartmentRepository
import com.example.domain.repository.EnterGradeInput
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.GradeRepository
import com.example.domain.repository.HomeworkRepository
import com.example.domain.repository.NotificationRepository
import com.example.domain.repository.ParentFinancialProfile
import com.example.domain.repository.PricingRepository
import com.example.domain.repository.PushHomeworkInput
import com.example.domain.repository.ReleveRepository
import com.example.domain.repository.RollCallEntry
import com.example.domain.repository.RoutingRepository
import com.example.domain.repository.StorageRepository
import com.example.domain.repository.SubmitExpenseInput
import com.example.domain.repository.SubjectRepository
import com.example.domain.repository.UpdateClassInput
import com.example.domain.repository.UpdatePersonnelInput
import com.example.domain.repository.UpdateSubjectInput
import com.example.domain.repository.WorkflowRepository
import com.example.domain.model.GeoPoint
import com.example.infrastructure.routing.OsrmClient
import com.example.infrastructure.routing.TspSolver
import com.example.infrastructure.room.AcademicClassDao
import com.example.infrastructure.room.AcademicClassEntity
import com.example.infrastructure.room.AssessmentDao
import com.example.infrastructure.room.AssessmentEntity
import com.example.infrastructure.room.AttendanceDao
import com.example.infrastructure.room.AttendanceEntity
import com.example.infrastructure.room.AuditLogDao
import com.example.infrastructure.room.AuditLogEntity
import com.example.infrastructure.room.ClassSubjectDao
import com.example.infrastructure.room.ClassSubjectEntity
import com.example.infrastructure.room.DepartmentDao
import com.example.infrastructure.room.DepartmentEntity
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.room.ExpenseDao
import com.example.infrastructure.room.ExpenseEntity
import com.example.infrastructure.room.HomeworkDao
import com.example.infrastructure.room.HomeworkEntity
import com.example.infrastructure.room.InstallmentEntity
import com.example.infrastructure.room.LedgerEntryEntity
import com.example.infrastructure.room.LocalMappers
import com.example.infrastructure.room.NotificationDao
import com.example.infrastructure.room.NotificationEntity
import com.example.infrastructure.room.ParentDao
import com.example.infrastructure.room.ParentEntity
import com.example.infrastructure.room.PaymentDao
import com.example.infrastructure.room.PaymentEntity
import com.example.infrastructure.room.PersonnelDao
import com.example.infrastructure.room.PersonnelEntity
import com.example.infrastructure.room.PricingConfigDao
import com.example.infrastructure.room.PricingConfigEntity
import com.example.infrastructure.room.PricingDiscountEntity
import com.example.infrastructure.room.ReleveEntryDao
import com.example.infrastructure.room.ReleveEntryEntity
import com.example.infrastructure.room.StudentDao
import com.example.infrastructure.room.StudentEntity
import com.example.infrastructure.room.SubjectDao
import com.example.infrastructure.room.SubjectEntity
import com.example.infrastructure.room.TransportPricingEntity
import com.example.infrastructure.room.TripLogDao
import com.example.infrastructure.room.TripLogEntity
import com.example.infrastructure.room.VehicleDao
import com.example.infrastructure.room.VehicleEntity
import com.example.infrastructure.room.RoutingStopDao
import com.example.infrastructure.room.RoutingStopEntity
import com.example.infrastructure.room.WorkflowRunDao
import com.example.infrastructure.room.WorkflowRunEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalDebtRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val db: ElImtiyazDatabase,
) : DebtRepository {

    override fun observeSummary(): Flow<List<DebtSummary>> = combine(
        db.parentDao().observeAll(),
        db.ledgerEntryDao().observeAll(),
        db.installmentDao().observeAll(),
        db.studentDao().observeAll(),
    ) { parents, ledgerEntries, installments, students ->
        val nowIso = Instant.now().toString()
        val domainLedger = ledgerEntries.map { LocalMappers.run { it.toDomain() } }
        val ledgerByParent = domainLedger.groupBy { it.parentId }
        val installmentsByParent = installments.groupBy { it.parentId }

        parents.map { parent ->
            val parentEntries = ledgerByParent[parent.id] ?: emptyList()
            val parentInsts = installmentsByParent[parent.id] ?: emptyList()
            val studentCount = students.count { it.parentId == parent.id }

            val ledgerOutstanding = if (parentEntries.isNotEmpty()) {
                val dueDateMap = LedgerEngine.buildOverdueDueDateMap(parentEntries)
                LedgerEngine.computeParentSummary(parentEntries, parent.id, parent.fullName, dueDateMap)
                    .totalOutstanding.coerceAtLeast(0L)
            } else 0L

            val instOutstanding = parentInsts
                .filter { it.status != "paid" }
                .sumOf { (it.amountDue - it.amountPaid - it.amountPending).coerceAtLeast(0L) }

            val outstanding = if (ledgerOutstanding > 0L) ledgerOutstanding else instOutstanding

            val unpaidOverdueInsts = parentInsts.filter {
                it.status != "paid" && it.dueDate < nowIso && (it.amountDue - it.amountPaid) > 0L
            }
            val oldestDue = unpaidOverdueInsts.minOfOrNull { it.dueDate }

            val maxDays = when {
                oldestDue != null -> daysBetweenFloor(oldestDue)
                parentEntries.isNotEmpty() -> LedgerEngine.maxDaysOverdueFromLedger(parentEntries)
                else -> 0L
            }

            DebtSummary(
                parentId = parent.id,
                parentName = parent.fullName,
                parentPhone = parent.phone,
                studentCount = studentCount,
                outstandingAmount = outstanding,
                daysOverdue = maxDays,
                bucket = agingBucketFromDays(maxDays),
            )
        }.filter { it.outstandingAmount > 0L }.sortedByDescending { it.outstandingAmount }
    }

    override fun observeParentProfile(parentId: String): Flow<ParentFinancialProfile?> = combine(
        db.parentDao().observeById(parentId),
        db.ledgerEntryDao().observeByParent(parentId),
        db.installmentDao().observeByParent(parentId),
        db.paymentDao().observeByParent(parentId),
    ) { parent, ledgerEntries, installments, payments ->
        if (parent == null) null
        else {
            val domainEntries = ledgerEntries.map { LocalMappers.run { it.toDomain() } }
            val dueDateMap = LedgerEngine.buildOverdueDueDateMap(domainEntries)
            val summary = LedgerEngine.computeParentSummary(domainEntries, parentId, parent.fullName, dueDateMap)
            val adjustments = domainEntries
                .filter { it.type == com.example.core.LedgerEntryType.ADJUSTMENT && it.reversesId == null }
                .map { e ->
                    com.example.domain.repository.AccountAdjustment(
                        id = e.id,
                        parentId = e.parentId,
                        amount = e.amount,
                        reason = e.description,
                        approvedBy = e.actorId,
                        approvedAt = e.at,
                        receiptRef = e.receiptNumber,
                    )
                }
                .sortedByDescending { it.approvedAt }
            ParentFinancialProfile(
                parentId = parentId,
                parentName = parent.fullName,
                totalDue = summary.totalCharged,
                totalPaid = summary.totalPaid,
                totalOutstanding = summary.totalOutstanding.coerceAtLeast(0L),
                overdueAmount = summary.totalOverdue.coerceAtLeast(0L),
                installments = installments.map { LocalMappers.run { it.toDomain() } },
                recentPayments = payments.map { LocalMappers.run { it.toDomain() } },
                adjustments = adjustments,
            )
        }
    }

    override suspend fun sendReminder(parentId: String, actorId: String, actorName: String): Result<Unit> {
        val parent = db.parentDao().getById(parentId)
            ?: return Result.Err(Errors.notFound("Parent $parentId introuvable"))

        val entries = db.ledgerEntryDao().listByParent(parentId).map { LocalMappers.run { it.toDomain() } }
        val summary = LedgerEngine.computeParentSummary(
            entries, parentId, parent.fullName, LedgerEngine.buildOverdueDueDateMap(entries),
        )
        val outstanding = summary.totalOutstanding.coerceAtLeast(0L)

        db.notificationDao().upsert(
            NotificationEntity(
                id = "ntf-rem-${UUID.randomUUID()}",
                tenantId = auditContext.tenantId(),
                title = "Rappel de paiement : ${parent.fullName}",
                body = "Relance envoyée par $actorName — solde restant dû : " +
                    "${(outstanding / 100).formatDzd()} DZD.",
                type = "payment_overdue",
                priority = if (summary.totalOverdue > 0L) "high" else "medium",
                source = "debt_dashboard",
                sourceLabel = "Recouvrement",
                entityType = "parent",
                entityId = parentId,
                targetUserId = null,
                isRead = false,
                createdAt = Instant.now().toString(),
            ),
        )
        db.auditLogDao().upsert(
            auditContext.auditLog(
                "debt.reminder_sent", "parent", parentId, actorId, actorName,
                after = """{"outstanding":$outstanding}""",
            ),
        )
        return Result.Ok(Unit)
    }
}