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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

// ─── Class Repository ───────────────────────────────────────────────────────

@Singleton
class LocalClassRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val classDao: AcademicClassDao,
    private val studentDao: StudentDao,
    private val auditDao: AuditLogDao,
) : ClassRepository {

    override fun observe(): Flow<List<AcademicClass>> =
        classDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain(studentDao.countActive()) } } }

    override fun observeByLevel(level: String): Flow<List<AcademicClass>> =
        classDao.observeAll().map { rows -> rows.filter { it.level == level }.map { LocalMappers.run { it.toDomain(0) } } }

    override fun observeById(id: String): Flow<AcademicClass?> =
        classDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain(0) } } }

    override suspend fun createClass(input: CreateClassInput, actorId: String, actorName: String): Result<AcademicClass> {
        val now = Instant.now().toString()
        val code = "CLS-${input.level.uppercase()}-${UUID.randomUUID().toString().takeLast(4).uppercase()}"
        val entity = AcademicClassEntity(
            id = "cls-${UUID.randomUUID()}", tenantId = auditContext.tenantId(), code = code,
            name = input.name, level = input.level, gradeYear = input.gradeYear,
            gradeLevel = input.level, section = null, room = input.room, capacity = input.capacity,
            homeroomTeacherId = input.homeroomTeacherId, homeroomTeacherName = null,
            academicYear = input.academicYear, isActive = true, createdAt = now, updatedAt = now,
        )
        classDao.upsert(entity)
        auditDao.upsert(auditContext.auditLog("class.create", "class", entity.id, actorId, actorName))
        return Result.Ok(LocalMappers.run { entity.toDomain(0) })
    }

    override suspend fun updateClass(id: String, input: UpdateClassInput, actorId: String, actorName: String): Result<AcademicClass> {
        val existing = classDao.getById(id) ?: return Result.Err(Errors.notFound("Class $id not found"))
        val updated = existing.copy(
            name = input.name ?: existing.name,
            room = input.room ?: existing.room,
            capacity = input.capacity ?: existing.capacity,
            homeroomTeacherId = input.homeroomTeacherId ?: existing.homeroomTeacherId,
            updatedAt = Instant.now().toString(),
        )
        classDao.update(updated)
        auditDao.upsert(auditContext.auditLog("class.update", "class", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain(0) })
    }

    override suspend fun deleteClass(id: String, actorId: String, actorName: String): Result<Unit> {
        classDao.deleteById(id)
        auditDao.upsert(auditContext.auditLog("class.delete", "class", id, actorId, actorName))
        return Result.Ok(Unit)
    }
}

// ─── Dashboard Repository (Rich Real-Time KPI & Operations Computation) ───────

private data class DashboardGroup1(
    val students: List<StudentEntity>,
    val parents: List<ParentEntity>,
    val staff: List<PersonnelEntity>,
    val payments: List<PaymentEntity>,
)

private data class DashboardGroup2(
    val installments: List<InstallmentEntity>,
    val ledger: List<LedgerEntryEntity>,
    val expenses: List<ExpenseEntity>,
    val attendance: List<AttendanceEntity>,
    val classes: List<AcademicClassEntity>,
)

// TIER 4 FIX (bypass #4) — 4-tuple for `observeOperationalAlerts`'s first
// combine group (parents + installments + expenses + ledger). Kotlin's
// stdlib doesn't ship a `Quadruple`, so we use a small local data class.
private data class AlertOperationalGroup1(
    val parents: List<ParentEntity>,
    val installments: List<InstallmentEntity>,
    val expenses: List<ExpenseEntity>,
    val ledger: List<LedgerEntryEntity>,
)

@Singleton
class LocalDashboardRepository @Inject constructor(
    private val db: ElImtiyazDatabase,
) : DashboardRepository {

    override fun observeKpis(): Flow<DashboardKpi?> = combine(
        combine(
            db.studentDao().observeAll(),
            db.parentDao().observeAll(),
            db.personnelDao().observeAll(),
            db.paymentDao().observeAll(),
        ) { students, parents, staff, payments ->
            DashboardGroup1(students, parents, staff, payments)
        },
        combine(
            db.installmentDao().observeAll(),
            db.ledgerEntryDao().observeAll(),
            db.expenseDao().observeAll(),
            db.attendanceDao().observeAll(),
            db.academicClassDao().observeAll(),
        ) { installments, ledger, expenses, attendance, classes ->
            DashboardGroup2(installments, ledger, expenses, attendance, classes)
        },
    ) { g1, g2 ->
        val todayIso = LocalDate.now(ZoneOffset.UTC).toString()
        val monthStart = OffsetDateTime.now(ZoneOffset.UTC)
            .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toString()
        // TIER 2 R16 — add upper bound for monthlyRevenue filter so future-dated
        // payments are NOT counted as current-month revenue. The audit (D53)
        // flagged that the 12-month chart applied the bound but the KPI filter
        // did not — internal inconsistency.
        val nextMonthStart = OffsetDateTime.now(ZoneOffset.UTC)
            .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
            .plusMonths(1).toInstant().toString()

        val activeStudents = g1.students.filter { it.status == "active" }
        val activeStaff = g1.staff.filter { it.status == "active" }
        val activeClasses = g2.classes.filter { it.isActive }

        val todayPayments = g1.payments.filter { it.status == "paid" && it.collectedAt.startsWith(todayIso) }
        val todayRevenue = todayPayments.sumOf { it.amount }
        val todayPaymentsCount = todayPayments.size

        // TIER 2 R16 — added the upper bound (`< nextMonthStart`).
        val monthlyPayments = g1.payments.filter {
            it.status == "paid" && it.collectedAt >= monthStart && it.collectedAt < nextMonthStart
        }
        val monthlyRevenue = monthlyPayments.sumOf { it.amount }

        val pendingChecks = g1.payments.filter { it.method == "check" && it.status == "pending" }
        val pendingChecksCount = pendingChecks.size
        val pendingChecksAmount = pendingChecks.sumOf { it.amount }

        val submittedExpenses = g2.expenses.filter { it.status == "submitted" }
        val pendingExpensesCount = submittedExpenses.size
        val pendingExpensesAmount = submittedExpenses.sumOf { it.amount }

        // TIER 2 R16 — replaced the naive Σ amounts with the canonical
        // `LedgerEngine.computeParentSummary` per parent. The previous code:
        //   g2.ledger.filter { it.type == "charge" || it.type == "payment" || it.type == "adjustment" }.sumOf { it.amount }
        // had three bugs (audit D51):
        //   1. Excluded refunds (type=refund) — refunds reduce what the parent owes.
        //   2. Included reversed originals (reversal entries negate originals, but
        //      the originals were still summed).
        //   3. Didn't aggregate per-account before summing — `computeParentSummary`
        //      applies the canonical per-account balance replay.
        val domainLedger = g2.ledger.map { LocalMappers.run { it.toDomain() } }
        val parentIds = domainLedger.map { it.parentId }.distinct()
        val totalOutstanding = parentIds.sumOf { pid ->
            val parentEntries = domainLedger.filter { it.parentId == pid }
            // T-026 (WEAK-007): the map is passed even for balance-only reads —
            // no production call site may rely on the empty-map default (a
            // future totalOverdue read here would silently be 0 again).
            val dueDateMap = LedgerEngine.buildOverdueDueDateMap(parentEntries)
            LedgerEngine.computeParentSummary(parentEntries, pid, "", dueDateMap).totalOutstanding.coerceAtLeast(0L)
        }

        // TIER 2 R16 — overdue: canonical rule (INV-4) classifies an account as
        // overdue when balance > 0 AND the latest charge's due date is past.
        // T-026 (WEAK-007): the due-date map MUST be built and passed —
        // `computeParentSummary`'s default is an EMPTY map, which made
        // totalOverdue permanently 0 (the "Créances en Retard" KPI always
        // showed 0 DZD). Mirrors the desktop's debt-ops.ts:43-44 pattern:
        // buildOverdueDueDateMap(parentEntries) THEN computeParentSummary.
        val overdueDebt = parentIds.sumOf { pid ->
            val parentEntries = domainLedger.filter { it.parentId == pid }
            val dueDateMap = LedgerEngine.buildOverdueDueDateMap(parentEntries)
            LedgerEngine.computeParentSummary(parentEntries, pid, "", dueDateMap).totalOverdue.coerceAtLeast(0L)
        }
        val overdueFamiliesCount = parentIds.count { pid ->
            val parentEntries = domainLedger.filter { it.parentId == pid }
            val dueDateMap = LedgerEngine.buildOverdueDueDateMap(parentEntries)
            LedgerEngine.computeParentSummary(parentEntries, pid, "", dueDateMap).totalOverdue > 0L
        }

        val todayAttendance = g2.attendance.filter { it.date == todayIso }
        val todayPresent = todayAttendance.count { it.status == "present" }
        val todayAbsent = todayAttendance.count { it.status == "absent_unexcused" || it.status == "absent_excused" }
        // TIER 2 R16 — removed the fabricated `96.5` fallback (audit D54).
        // When no attendance records exist for today, return 0.0 (truthful)
        // rather than inventing a 96.5% rate. The UI can choose to display
        // "—" when the rate is 0 AND todayAttendance is empty.
        val attendanceRateToday = if (todayAttendance.isNotEmpty()) {
            (todayPresent.toDouble() / todayAttendance.size.toDouble() * 100.0)
        } else {
            0.0
        }
        val classesWithRollCall = todayAttendance.map { it.classId }.distinct().size

        DashboardKpi(
            // TIER 2 R16 — removed the fabricated fallback values (audit D54):
            //   `if (activeStudents.isNotEmpty()) activeStudents.size else 390`
            // returned 390 (fake) when Room was empty. Now returns the real
            // count (0 when empty). Same fix for `totalParents`, `totalStaff`,
            // `totalClassesCount`.
            totalStudents = activeStudents.size,
            totalParents = g1.parents.size,
            totalStaff = activeStaff.size,
            monthlyRevenue = monthlyRevenue,
            todayRevenue = todayRevenue,
            todayPaymentsCount = todayPaymentsCount,
            outstandingDebt = totalOutstanding.coerceAtLeast(0L),
            overdueDebt = overdueDebt,
            overdueFamiliesCount = overdueFamiliesCount,
            pendingExpenses = pendingExpensesCount,
            pendingExpensesAmount = pendingExpensesAmount,
            attendanceRateToday = attendanceRateToday,
            todayPresentCount = todayPresent,
            todayAbsentCount = todayAbsent,
            classesCompletedRollCall = classesWithRollCall,
            totalClassesCount = activeClasses.size,
            pendingChecksCount = pendingChecksCount,
            pendingChecksAmount = pendingChecksAmount,
            overdueAlerts = overdueFamiliesCount,
        )
    }

    override fun observeRevenueLast12Months(): Flow<List<com.example.domain.repository.RevenuePoint>> =
        db.paymentDao().observeAll().map { payments ->
            val now = LocalDate.now(ZoneOffset.UTC)
            val months = (11 downTo 0).map { monthsBack ->
                val target = now.minusMonths(monthsBack.toLong())
                val monthStart = OffsetDateTime.of(target.year, target.monthValue, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toString()
                val nextMonthStart = OffsetDateTime.of(target.year, target.monthValue, 1, 0, 0, 0, 0, ZoneOffset.UTC).plusMonths(1).toInstant().toString()
                val sum = payments.filter { it.status == "paid" && it.collectedAt >= monthStart && it.collectedAt < nextMonthStart }.sumOf { it.amount }
                val label = when (target.monthValue) {
                    1 -> "Jan"
                    2 -> "Fév"
                    3 -> "Mar"
                    4 -> "Avr"
                    5 -> "Mai"
                    6 -> "Juin"
                    7 -> "Juil"
                    8 -> "Août"
                    9 -> "Sept"
                    10 -> "Oct"
                    11 -> "Nov"
                    12 -> "Déc"
                    else -> "${target.monthValue}"
                }
                com.example.domain.repository.RevenuePoint(label = label, amount = sum)
            }
            // TIER 2 R16 — removed the fabricated 6-month revenue fallback
            // (audit D55). When Room is empty (or has no paid payments), the
            // chart should show zeros — NOT the fake values "Sept=13.4M DZD"
            // etc. — because those numbers mislead the user into thinking real
            // data exists. Real revenue numbers come from real payments.
            months
        }

    override fun observePaymentMethodsSummary(): Flow<List<PaymentMethodSummary>> =
        db.paymentDao().observeAll().map { payments ->
            val paidPayments = payments.filter { it.status == "paid" }
            val totalSum = paidPayments.sumOf { it.amount }.toDouble()
            val methods = listOf(
                "cash" to "Espèces",
                "check" to "Chèques",
                "transfer" to "Virements",
            )
            methods.map { (code, label) ->
                val matching = paidPayments.filter { it.method.lowercase() == code }
                val amount = matching.sumOf { it.amount }
                val count = matching.size
                val percentage = if (totalSum > 0.0) (amount.toDouble() / totalSum * 100.0) else 0.0
                PaymentMethodSummary(
                    method = code,
                    label = label,
                    count = count,
                    totalAmount = amount,
                    percentage = percentage,
                )
            }
        }

    override fun observeClassRollCallStatus(): Flow<List<ClassRollCallStatus>> = combine(
        db.academicClassDao().observeAll(),
        db.studentDao().observeAll(),
        db.attendanceDao().observeAll(),
    ) { classes, students, attendance ->
        val todayIso = LocalDate.now(ZoneOffset.UTC).toString()
        val todayAttendance = attendance.filter { it.date == todayIso }

        classes.filter { it.isActive }.map { cls ->
            val classStudents = students.filter { it.classId == cls.id && it.status == "active" }
            val classAttendance = todayAttendance.filter { it.classId == cls.id }
            val isCompleted = classAttendance.isNotEmpty()
            val presentCount = classAttendance.count { it.status == "present" }
            val absentCount = classAttendance.count { it.status == "absent_unexcused" || it.status == "absent_excused" }
            val lateCount = classAttendance.count { it.status == "late" }

            ClassRollCallStatus(
                classId = cls.id,
                className = cls.name,
                level = cls.level,
                totalStudents = if (classStudents.isNotEmpty()) classStudents.size else (cls.capacity ?: 0),
                isCompletedToday = isCompleted,
                presentCount = if (isCompleted) presentCount else 0,
                absentCount = if (isCompleted) absentCount else 0,
                lateCount = if (isCompleted) lateCount else 0,
            )
        }.sortedWith(compareBy({ it.level }, { it.className }))
    }

    override fun observeOperationalAlerts(): Flow<List<DashboardOperationalAlert>> = combine(
        combine(
            db.parentDao().observeAll(),
            db.installmentDao().observeAll(),
            db.expenseDao().observeAll(),
            // TIER 4 FIX (bypass #4) — add the ledger flow so the overdue-debt
            // alert can compute `totalOverdue` via the canonical
            // `LedgerEngine.computeParentSummary` instead of the inline
            // `insts.sumOf { (it.amountDue - it.amountPaid) }`.
            db.ledgerEntryDao().observeAll(),
        ) { parents, installments, expenses, ledger ->
            AlertOperationalGroup1(parents, installments, expenses, ledger)
        },
        combine(
            db.paymentDao().observeAll(),
            db.attendanceDao().observeAll(),
            db.academicClassDao().observeAll(),
        ) { payments, attendance, classes ->
            Triple(payments, attendance, classes)
        },
    ) { (parents, installments, expenses, ledger), (payments, attendance, classes) ->
        val nowIso = Instant.now().toString()
        val todayIso = LocalDate.now(ZoneOffset.UTC).toString()
        val alerts = mutableListOf<DashboardOperationalAlert>()

        // 1. Overdue Debt Alerts (top overdue parents with phone numbers for 1-tap call)
        val overdueInstallments = installments.filter { it.status != "paid" && it.dueDate < nowIso }
        val overdueByParent = overdueInstallments.groupBy { it.parentId }
        // TIER 4 FIX (bypass #4) — pre-compute per-parent ledger summaries
        // using the canonical `LedgerEngine.computeParentSummary` (the same
        // call used by `observeKpis` / `observeDebtByAging` in this file).
        // Previously this branch computed
        //   `insts.sumOf { (it.amountDue - it.amountPaid).coerceAtLeast(0L) }`
        // which diverged from the canonical ledger when reversals /
        // adjustments / credits were present.
        val domainLedger = ledger.map { LocalMappers.run { it.toDomain() } }
        val ledgerByParent = domainLedger.groupBy { it.parentId }
        overdueByParent.entries
            .mapNotNull { (parentId, insts) ->
                val parent = parents.firstOrNull { it.id == parentId } ?: return@mapNotNull null
                val parentEntries = ledgerByParent[parentId] ?: emptyList()
                val dueDateMap = LedgerEngine.buildOverdueDueDateMap(parentEntries)
                val totalOverdue = LedgerEngine
                    .computeParentSummary(parentEntries, parentId, parent.fullName, dueDateMap)
                    .totalOverdue
                    .coerceAtLeast(0L)
                val oldestDue = insts.minOfOrNull { it.dueDate } ?: nowIso
                val daysOverdue = daysBetweenFloor(oldestDue)
                Triple(parent, totalOverdue, daysOverdue)
            }
            .filter { it.second > 0L }
            .sortedByDescending { it.second }
            .take(4)
            .forEach { (parent, totalOverdue, daysOverdue) ->
                alerts.add(
                    DashboardOperationalAlert(
                        id = "alert-debt-${parent.id}",
                        type = "overdue_debt",
                        title = "Échéance impayée : ${parent.fullName}",
                        description = "Retard de $daysOverdue jours sur les tranches (${(totalOverdue / 100).formatDzd()} DZD restant).",
                        amount = totalOverdue,
                        phone = parent.phone,
                        severity = if (daysOverdue > 30) "urgent" else "high",
                        entityType = "parent",
                        entityId = parent.id,
                        actionLabel = "Relancer",
                    )
                )
            }

        // 2. Pending Expenses Approval
        expenses.filter { it.status == "submitted" }.take(3).forEach { exp ->
            alerts.add(
                DashboardOperationalAlert(
                    id = "alert-exp-${exp.id}",
                    type = "pending_expense",
                    title = "Dépense à valider : ${exp.title}",
                    description = "Demande de ${(exp.amount / 100).formatDzd()} DZD pour ${exp.payee} soumise par ${exp.submittedByName}.",
                    amount = exp.amount,
                    severity = if (exp.urgency == "high" || exp.urgency == "critical") "urgent" else "medium",
                    entityType = "expense",
                    entityId = exp.id,
                    actionLabel = "Examiner",
                )
            )
        }

        // 3. Pending Bank Checks to Deposit
        val pendingChecks = payments.filter { it.method == "check" && it.status == "pending" }
        if (pendingChecks.isNotEmpty()) {
            val totalPendingChecks = pendingChecks.sumOf { it.amount }
            alerts.add(
                DashboardOperationalAlert(
                    id = "alert-pending-checks",
                    type = "pending_check",
                    title = "${pendingChecks.size} chèque(s) en attente de dépôt",
                    description = "Total de ${(totalPendingChecks / 100).formatDzd()} DZD en chèques à déposer pour compensation bancaire.",
                    amount = totalPendingChecks,
                    count = pendingChecks.size,
                    severity = "medium",
                    entityType = "financials",
                    entityId = "checks",
                    actionLabel = "Voir chèques",
                )
            )
        }

        // 4. Missing Roll Calls Today
        val todayAttendanceClasses = attendance.filter { it.date == todayIso }.map { it.classId }.toSet()
        val missingClasses = classes.filter { it.isActive && it.id !in todayAttendanceClasses }
        if (missingClasses.isNotEmpty()) {
            val classNames = missingClasses.take(3).joinToString(", ") { it.name }
            alerts.add(
                DashboardOperationalAlert(
                    id = "alert-missing-rollcall",
                    type = "missing_roll_call",
                    title = "Appel du jour non validé (${missingClasses.size} classe(s))",
                    description = "Classes en attente : $classNames${if (missingClasses.size > 3) "..." else ""}",
                    count = missingClasses.size,
                    severity = "medium",
                    entityType = "class",
                    entityId = missingClasses.first().id,
                    actionLabel = "Faire l'appel",
                )
            )
        }

        alerts
    }

    override fun observeDebtByAging(): Flow<List<DebtSummary>> = combine(
        db.parentDao().observeAll(),
        db.ledgerEntryDao().observeAll(),
        db.studentDao().observeAll(),
    ) { parents, ledgerEntries, students ->
        parents.map { parent ->
            val parentEntries = ledgerEntries.filter { it.parentId == parent.id }
            val studentCount = students.count { it.parentId == parent.id }
            val domainEntries = parentEntries.map { LocalMappers.run { it.toDomain() } }
            // T-026 (WEAK-007): build the due-date map — without it the debt
            // dashboard's overdueAmount was permanently 0.
            val dueDateMap = LedgerEngine.buildOverdueDueDateMap(domainEntries)
            val summary = LedgerEngine.computeParentSummary(domainEntries, parent.id, parent.fullName, dueDateMap)
            val maxDays = LedgerEngine.maxDaysOverdueFromLedger(domainEntries)
            DebtSummary(
                parentId = parent.id,
                parentName = parent.fullName,
                parentPhone = parent.phone,
                studentCount = studentCount,
                outstandingAmount = summary.totalOutstanding.coerceAtLeast(0L),
                daysOverdue = maxDays,
                bucket = agingBucketFromDays(maxDays),
            )
        }.filter { it.outstandingAmount > 0L }.sortedByDescending { it.outstandingAmount }
    }

    override suspend fun refreshKpis(): Result<Unit> = Result.Ok(Unit)

    /**
     * FIX (fabricated trend): the 7-day attendance chart previously showed a
     * hardcoded baseline (95.2 / 96.0 / 95.8 / 97.1 / 96.4 / 94.8 …) with only
     * "today" coming from real data. This computes the REAL per-day attendance
     * rate from the `attendance` table for the last 7 days. Days without any
     * roll-call records are omitted rather than invented.
     */
    override fun observeAttendanceTrend(): Flow<List<com.example.domain.repository.AttendanceTrendPoint>> =
        db.attendanceDao().observeAll().map { records ->
            val today = LocalDate.now(ZoneOffset.UTC)
            val dayLabels = mapOf(
                DayOfWeek.MONDAY to "Lun", DayOfWeek.TUESDAY to "Mar", DayOfWeek.WEDNESDAY to "Mer",
                DayOfWeek.THURSDAY to "Jeu", DayOfWeek.FRIDAY to "Ven", DayOfWeek.SATURDAY to "Sam",
                DayOfWeek.SUNDAY to "Dim",
            )
            (6 downTo 0).map { daysBack ->
                val date = today.minusDays(daysBack.toLong())
                val dayRecords = records.filter { it.date == date.toString() }
                date to dayRecords
            }.filter { (_, dayRecords) -> dayRecords.isNotEmpty() }
                .map { (date, dayRecords) ->
                    val present = dayRecords.count { it.status == "present" || it.status == "late" }
                    val rate = present.toDouble() / dayRecords.size.toDouble() * 100.0
                    com.example.domain.repository.AttendanceTrendPoint(
                        label = dayLabels[date.dayOfWeek] ?: date.dayOfWeek.name.take(3),
                        rate = rate,
                        records = dayRecords.size,
                    )
                }
        }
}

// ─── Debt Repository ────────────────────────────────────────────────────────

@Singleton
class LocalDebtRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val db: ElImtiyazDatabase,
) : DebtRepository {

    override fun observeSummary(): Flow<List<DebtSummary>> = combine(
        db.parentDao().observeAll(),
        db.ledgerEntryDao().observeAll(),
        db.studentDao().observeAll(),
    ) { parents, ledgerEntries, students ->
        parents.map { parent ->
            val parentEntries = ledgerEntries.filter { it.parentId == parent.id }
            val studentCount = students.count { it.parentId == parent.id }
            val domainEntries = parentEntries.map { LocalMappers.run { it.toDomain() } }
            // T-026 (WEAK-007): build the due-date map — without it the debt
            // dashboard's overdueAmount was permanently 0.
            val dueDateMap = LedgerEngine.buildOverdueDueDateMap(domainEntries)
            val summary = LedgerEngine.computeParentSummary(domainEntries, parent.id, parent.fullName, dueDateMap)
            val maxDays = LedgerEngine.maxDaysOverdueFromLedger(domainEntries)
            DebtSummary(
                parentId = parent.id,
                parentName = parent.fullName,
                parentPhone = parent.phone,
                studentCount = studentCount,
                outstandingAmount = summary.totalOutstanding.coerceAtLeast(0L),
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
            // T-026 (WEAK-007): build the due-date map — without it the parent
            // profile's overdueAmount was permanently 0.
            val dueDateMap = LedgerEngine.buildOverdueDueDateMap(domainEntries)
            val summary = LedgerEngine.computeParentSummary(domainEntries, parentId, parent.fullName, dueDateMap)
            // TIER 2 R17 — populate `adjustments` from the ledger's adjustment
            // entries. Mirrors the desktop's `ParentFinancialProfile.adjustments`.
            // Filters out reversal entries (they negate originals — the
            // canonical `computeParentSummary` already excludes them from
            // totals, so we exclude them here too for UI consistency).
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

    // FIX (hollow action): sendReminder previously wrote ONLY an audit row —
    // no reminder was ever delivered anywhere. Now a real in-app notification
    // is inserted into the `notifications` table (visible in the Alerts inbox
    // and the dashboard notification stream) in addition to the audit trail.
    override suspend fun sendReminder(parentId: String, actorId: String, actorName: String): Result<Unit> {
        val parent = db.parentDao().getById(parentId)
            ?: return Result.Err(Errors.notFound("Parent $parentId introuvable"))

        val entries = db.ledgerEntryDao().listByParent(parentId).map { LocalMappers.run { it.toDomain() } }
        // T-026 (WEAK-007): pass the due-date map — no production call site may
        // rely on computeParentSummary's empty-map default.
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

// ─── Pricing Repository ─────────────────────────────────────────────────────

@Singleton
class LocalPricingRepository @Inject constructor(
    private val pricingDao: PricingConfigDao,
) : PricingRepository {

    override fun observe(): Flow<PricingConfig?> = pricingDao.observeActive().map { entity ->
        entity?.let { e ->
            val discounts = pricingDao.listActiveDiscounts()
            LocalMappers.run { e.toDomain(discounts) }
        }
    }

    override fun observeGradeLevelTuition(): Flow<List<GradeLevelTuition>> = pricingDao.observeActive().map {
        pricingDao.listGradeLevelTuition().map { g -> LocalMappers.run { g.toDomain() } }
    }

    override suspend fun updateRegistrationFee(amount: Long, actorId: String, actorName: String): Result<Unit> {
        val config = pricingDao.getActive() ?: return Result.Err(Errors.notFound("No active pricing config"))
        pricingDao.upsertConfig(config.copy(registrationFee = amount, updatedAt = Instant.now().toString()))
        return Result.Ok(Unit)
    }

    override suspend fun updateLatePenalty(amount: Long, actorId: String, actorName: String): Result<Unit> {
        val config = pricingDao.getActive() ?: return Result.Err(Errors.notFound("No active pricing config"))
        pricingDao.upsertConfig(config.copy(latePenaltyPerDay = amount, updatedAt = Instant.now().toString()))
        return Result.Ok(Unit)
    }

    override suspend fun updateTuitionForGradeLevel(gradeLevel: String, annualAmount: Long, tranches: Triple<Long, Long, Long>, actorId: String, actorName: String): Result<Unit> {
        val config = pricingDao.getActive() ?: return Result.Err(Errors.notFound("No active pricing config"))
        val existing = pricingDao.getTuitionByGrade(gradeLevel)
        val updated = (existing ?: com.example.infrastructure.room.GradeLevelTuitionEntity(
            id = "glt-${UUID.randomUUID()}", pricingConfigId = config.id, gradeLevel = gradeLevel,
            annualAmount = annualAmount, tranche1 = 0, tranche2 = 0, tranche3 = 0,
        )).copy(annualAmount = annualAmount, tranche1 = tranches.first, tranche2 = tranches.second, tranche3 = tranches.third)
        pricingDao.upsertGradeLevelTuition(listOf(updated))
        return Result.Ok(Unit)
    }
}

// ─── Audit Repository ───────────────────────────────────────────────────────

@Singleton
class LocalAuditRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val auditDao: AuditLogDao,
) : AuditRepository {

    override fun observe(limit: Int): Flow<List<AuditLog>> =
        auditDao.observeRecent().map { rows -> rows.map { LocalMappers.run { it.toDomain() } }.take(limit) }

    override fun observeByEntity(entityType: String, entityId: String): Flow<List<AuditLog>> =
        auditDao.observeRecent().map { rows ->
            rows.filter { it.entityType == entityType && it.entityId == entityId }.map { LocalMappers.run { it.toDomain() } }
        }

    override suspend fun query(filter: AuditFilter): Result<List<AuditLog>> {
        // TIER 3 R19 FIX: previously `query()` always returned `emptyList()`,
        // making the audit log unsearchable. Now it actually filters by the
        // criteria the caller specified. The DAO's `observeRecent()` returns
        // the most recent 200 rows; we filter in-memory because the audit_logs
        // table is small (≤200 rows per the LIMIT in observeRecent) and a
        // dynamic SQL query would require either @RawQuery or a separate
        // @Query per filter combination.
        val rows = auditDao.observeRecent().first()
        val filtered = rows.asSequence()
            .filter { filter.action == null || it.action == filter.action }
            .filter { filter.entityType == null || it.entityType == filter.entityType }
            .filter { filter.entityId == null || it.entityId == filter.entityId }
            .filter { filter.actorId == null || it.actorId == filter.actorId }
            .filter { filter.from == null || it.createdAt >= filter.from }
            .filter { filter.to == null || it.createdAt <= filter.to }
            .drop(filter.offset)
            .take(filter.limit)
            .map { LocalMappers.run { it.toDomain() } }
            .toList()
        return Result.Ok(filtered)
    }

    override suspend fun log(input: AuditLogInput): Result<AuditLog> {
        // TIER 3 R19 FIX: previously `actorId` was hardcoded to "system".
        // Now we honor the caller-provided actor fields, falling back to
        // "system" only when the caller omits them. This makes the audit
        // trail useful for accountability — every action is attributed to
        // the real logged-in user, not the system.
        val entity = AuditLogEntity(
            id = "aud-${UUID.randomUUID()}", tenantId = auditContext.tenantId(),
            action = input.action, entityType = input.entityType, entityId = input.entityId,
            actorId = input.actorId ?: "system",
            actorName = input.actorName ?: "Système",
            actorRole = input.actorRole,
            beforeJson = input.beforeJson, afterJson = input.afterJson,
            note = input.note, createdAt = Instant.now().toString(),
        )
        auditDao.upsert(entity)
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }
}

// ─── Attendance Repository ──────────────────────────────────────────────────

@Singleton
class LocalAttendanceRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val attendanceDao: AttendanceDao,
    private val auditDao: AuditLogDao,
    private val notificationDao: NotificationDao,
    private val studentDao: StudentDao,
    // VAULT §06.03 — roll call is the PRIMARY mobile flow; records must reach
    // the shared backend so the portal's Absence Justification feature sees
    // them. The dispatcher upserts into `attendance_records` on the canonical
    // (tenant, student, record_date, session) key (migration 0041).
    private val syncSupport: com.example.infrastructure.sync.SyncSupport? = null,
) : com.example.domain.repository.AttendanceRepository {

    override fun observeByClass(classId: String, date: String): Flow<List<AttendanceRecord>> =
        attendanceDao.observeByClassAndDate(classId, date).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByStudent(studentId: String): Flow<List<AttendanceRecord>> {
        val since = LocalDate.now(ZoneOffset.UTC).minusDays(90).toString()
        return attendanceDao.observeByStudent(studentId, since).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }
    }

    override suspend fun recordRollCall(classId: String, date: String, session: String, records: List<RollCallEntry>, actorId: String, actorName: String): Result<Unit> {
        val now = Instant.now().toString()
        // FIX (duplicate roll-call records): previously every submission
        // generated fresh UUIDs and INSERTed new rows — re-saving a roll call
        // duplicated every record and inflated attendance stats. Re-use the
        // existing row's ID for the same (student, date, session) so the
        // REPLACE-strategy upsert updates in place (idempotent re-submission).
        val entities = records.map { r ->
            val existing = attendanceDao.getByStudentDateSession(r.studentId, date, session)
            AttendanceEntity(
                id = existing?.id ?: "att-${UUID.randomUUID()}",
                tenantId = auditContext.tenantId(),
                studentId = r.studentId, classId = classId, date = date, session = session,
                status = r.status, arrivalTime = null, note = r.note ?: existing?.note,
                recordedBy = actorId, recordedBy_name = actorName, recordedAt = now,
            )
        }
        attendanceDao.upsertAll(entities)
        // VAULT §06.03 — enqueue every record for the Supabase push.
        entities.forEach { entity ->
            syncSupport?.enqueueOnly(
                entity = "attendance",
                operation = "upsert",
                payload = buildAttendanceSyncPayload(entity),
                isMock = false,
                sourceScreen = "RollCallScreen",
            )
        }
        auditDao.upsert(auditContext.auditLog("attendance.rollCall", "class", classId, actorId, actorName,
            after = """{"date":"$date","session":"$session","count":${records.size}}"""))
        return Result.Ok(Unit)
    }

    // FIX (hollow action): alertAbsences previously wrote ONLY audit rows —
    // no parent was ever alerted. Now a real in-app notification is created
    // for each FLAGGED student (linked to the parent's record) in addition
    // to the audit trail, so the alert actually surfaces in the Alerts inbox.
    //
    // T-063 (ATT-103): the threshold is now the DESKTOP rule — ≥3 absences
    // (absent_unexcused + absent_excused, LATE excluded) within the CURRENT
    // TERM (core/Terms.kt, mirror of terms.ts). Previously Android alerted
    // for EVERY student in the input (effective threshold 1) — alert
    // fatigue + cross-platform divergence.
    override suspend fun alertAbsences(studentIds: List<String>, actorId: String, actorName: String): Result<Unit> {
        val now = Instant.now().toString()
        val window = currentTermWindow()
        val flagged = studentIds.mapNotNull { studentId ->
            val records = attendanceDao.listByStudent(studentId, window.start.toString())
            absenceAlertThreshold(
                records.map { it.studentId to it.status },
                records.map { it.date },
                window,
            ).firstOrNull()
        }
        flagged.forEach { (studentId, count) ->
            val student = studentDao.getById(studentId) ?: return@forEach
            auditDao.upsert(auditContext.auditLog("attendance.alert", "student", studentId, actorId, actorName))
            notificationDao.upsert(
                NotificationEntity(
                    id = "ntf-abs-${UUID.randomUUID()}",
                    tenantId = auditContext.tenantId(),
                    // Mirror of the desktop message (byte-identical semantics).
                    title = "Alerte absences",
                    body = "Votre enfant a accumulé $count absences ce trimestre (${window.label}). Merci de contacter l'administration.",
                    type = "attendance_alert",
                    priority = "high",
                    source = "roll_call",
                    sourceLabel = "Module Présences",
                    entityType = "student",
                    entityId = studentId,
                    targetUserId = null,
                    isRead = false,
                    createdAt = now,
                ),
            )
        }
        return Result.Ok(Unit)
    }
}

// ─── Grade Repository ───────────────────────────────────────────────────────

@Singleton
/** Canonical attendance_records-row payload for the sync dispatcher (§06.03). */
private fun buildAttendanceSyncPayload(e: com.example.infrastructure.room.AttendanceEntity): String =
    kotlinx.serialization.json.buildJsonObject {
        put("id", e.id)
        put("tenantId", e.tenantId)
        put("studentId", e.studentId)
        put("classId", e.classId)
        put("date", e.date)
        put("recordDate", e.date)
        put("session", e.session)
        put("status", e.status)
        e.arrivalTime?.let { put("arrivalTime", it) }
        e.note?.let { put("note", it) }
        put("recordedBy", e.recordedBy)
        put("recordedAt", e.recordedAt)
    }.toString()

class LocalGradeRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val assessmentDao: AssessmentDao,
    private val auditDao: AuditLogDao,
    // TIER 4 FIX — subject lookup so entered assessments carry the canonical
    // isExtracurricular flag (drives the GPA exclusion rule).
    private val subjectDao: com.example.infrastructure.room.SubjectDao,
    // VAULT §06.02 — grade entries must reach the shared backend (and thus
    // the Student Web Portal's Academic Hub). The dispatcher upserts into the
    // canonical `assessments` table written by the desktop grade-entry flow.
    private val syncSupport: com.example.infrastructure.sync.SyncSupport? = null,
) : GradeRepository {

    override fun observeForStudent(studentId: String, term: String, academicYear: String): Flow<List<Assessment>> =
        assessmentDao.observeByStudentTerm(studentId, term, academicYear).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    // FIX (ignored parameter): subjectId was dropped — the flow returned every
    // subject's assessments for the class. Callers (ClassDetail "Notes" tab,
    // the gradebook) believed they were scoped to one subject.
    override fun observeForClass(classId: String, subjectId: String, term: String, academicYear: String): Flow<List<Assessment>> =
        assessmentDao.observeByClassTerm(classId, term, academicYear).map { rows ->
            rows.filter { it.subjectId == subjectId }.map { LocalMappers.run { it.toDomain() } }
        }

    override fun observeForClass(classId: String, term: String, academicYear: String): Flow<List<Assessment>> =
        assessmentDao.observeByClassTerm(classId, term, academicYear).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    // Vault §04.07 / §06.05 — Student Academic History (all years, all terms).
    override fun observeAllForStudent(studentId: String): Flow<List<Assessment>> =
        assessmentDao.observeByStudent(studentId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun enterGrade(input: EnterGradeInput, actorId: String, actorName: String): Result<Assessment> {
        val now = Instant.now().toString()
        // Vault §06.02 (iteration 2) — read the SUBJECT's per-COMPONENT
        // coefficients (D1/D2/Examen) to compute the canonical subject
        // average with the weights the admin configured on the subject.
        // The previous build hard-coded the (D1 + D2 + 2×Ex) / 4 recipe;
        // the new recipe is (D1×c1 + D2×c2 + Ex×c3) / (c1 + c2 + c3) and
        // the three coefs are snapshotted onto the assessment row so past
        // years stay immutable when the subject's coefs are later edited.
        val subject = subjectDao.getById(input.subjectId)
        val subjectIsExtracurricular = subject?.isExtracurricular ?: false
        val coefD1 = subject?.coefficientDevoir1 ?: 1.0
        val coefD2 = subject?.coefficientDevoir2 ?: 1.0
        val coefEx = subject?.coefficientExamen ?: 2.0
        // CANONICAL — the subject average is only computable when all three
        // marks are present (matches the SQL trigger).
        val subjectAvg = com.example.core.computeSubjectAverage(
            input.devoir1, input.devoir2, input.examen, coefD1, coefD2, coefEx,
        )
        val existing = assessmentDao.getByStudentSubjectTerm(input.studentId, input.subjectId, input.term, input.academicYear)
        val entity = (existing ?: AssessmentEntity(
            id = "asm-${UUID.randomUUID()}", tenantId = auditContext.tenantId(),
            studentId = input.studentId, subjectId = input.subjectId, classId = input.classId,
            term = input.term, academicYear = input.academicYear,
            devoir1 = null, devoir2 = null, examen = null, coefficient = input.coefficient,
            isExtracurricular = subjectIsExtracurricular,
            subjectAverage = null, enteredBy = actorId, enteredAt = now,
            // Vault §06.02 — snapshot the SUBJECT's per-component coefs onto
            // the new assessment row (defaults preserved for legacy paths).
            coefficientDevoir1 = coefD1, coefficientDevoir2 = coefD2, coefficientExamen = coefEx,
        )).copy(
            devoir1 = input.devoir1, devoir2 = input.devoir2, examen = input.examen,
            coefficient = input.coefficient, subjectAverage = subjectAvg,
            isExtracurricular = subjectIsExtracurricular,
            enteredBy = actorId, enteredAt = now,
            // Vault §06.02 — refresh the per-component coef snapshot on
            // every grade edit so it always reflects the live subject config.
            coefficientDevoir1 = coefD1, coefficientDevoir2 = coefD2, coefficientExamen = coefEx,
        )
        assessmentDao.upsert(entity)
        // VAULT §06.02 — enqueue the canonical row for the Supabase push
        // (dispatcher writes the SAME assessments table the desktop app and
        // the web portal read from).
        syncSupport?.enqueueOnly(
            entity = "grade",
            operation = "upsert",
            payload = buildGradeSyncPayload(entity),
            isMock = false,
            sourceScreen = "GradeEntryScreen",
        )
        auditDao.upsert(auditContext.auditLog("grade.enter", "assessment", entity.id, actorId, actorName))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }
}

/** Canonical assessments-row payload for the sync dispatcher (§06.02). */
private fun buildGradeSyncPayload(e: com.example.infrastructure.room.AssessmentEntity): String =
    kotlinx.serialization.json.buildJsonObject {
        put("id", e.id)
        put("tenantId", e.tenantId)
        put("studentId", e.studentId)
        put("subjectId", e.subjectId)
        put("classId", e.classId)
        put("term", e.term)
        put("academicYear", e.academicYear)
        e.devoir1?.let { put("devoir1", it) }
        e.devoir2?.let { put("devoir2", it) }
        e.examen?.let { put("examen", it) }
        put("coefficient", e.coefficient)
        put("isExtracurricular", e.isExtracurricular)
        e.subjectAverage?.let { put("subjectAverage", it) }
        put("enteredBy", e.enteredBy)
        put("enteredAt", e.enteredAt)
        put("coefficientDevoir1", e.coefficientDevoir1)
        put("coefficientDevoir2", e.coefficientDevoir2)
        put("coefficientExamen", e.coefficientExamen)
    }.toString()

// ─── Expense Repository ─────────────────────────────────────────────────────

@Singleton
class LocalExpenseRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val expenseDao: ExpenseDao,
    private val auditDao: AuditLogDao,
) : ExpenseRepository {

    override fun observe(): Flow<List<Expense>> =
        expenseDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByStatus(status: String): Flow<List<Expense>> =
        expenseDao.observeByStatus(status).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Expense?> =
        expenseDao.observeAll().map { rows -> rows.firstOrNull { it.id == id }?.let { e -> LocalMappers.run { e.toDomain() } } }

    override suspend fun submit(input: SubmitExpenseInput, actorId: String, actorName: String): Result<Expense> {
        val now = Instant.now().toString()
        val year = LocalDate.now().year
        val seq = (expenseDao.countPending() + 1).toString().padStart(3, '0')
        val entity = ExpenseEntity(
            id = "exp-${UUID.randomUUID()}", tenantId = auditContext.tenantId(),
            requestCode = "EXP-$year-$seq", title = input.title, description = input.description,
            amount = input.amount, category = input.category, payee = input.payee,
            status = "submitted", submittedBy = actorId, submittedByName = actorName,
            submittedAt = now, approvedBy = null, approvedAt = null,
            disbursedAt = null, settledAt = null, proofUrl = null,
            urgency = input.urgency, anomalyScore = 0.0, notes = null,
            createdAt = now, updatedAt = now,
        )
        expenseDao.upsert(entity)
        auditDao.upsert(auditContext.auditLog("expense.submit", "expense", entity.id, actorId, actorName))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun approve(id: String, note: String, actorId: String, actorName: String): Result<Expense> {
        val existing = expenseDao.getById(id) ?: return Result.Err(Errors.notFound("Expense $id not found"))
        // TIER 4 FIX — enforce the canonical no-self-approval rule (plan §08;
        // desktop expense-ops.ts + SQL 0008 both enforce it).
        if (existing.submittedBy == actorId) {
            auditDao.upsert(auditContext.auditLog("expense.approve.blocked", "expense", id, actorId, actorName))
            return Result.Err(Errors.forbidden(
                "Un demandeur ne peut pas approuver sa propre dépense (règle d'auto-approbation)",
            ))
        }
        val updated = existing.copy(status = "approved", approvedBy = actorId, approvedAt = Instant.now().toString(), notes = note)
        expenseDao.update(updated)
        auditDao.upsert(auditContext.auditLog("expense.approve", "expense", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun reject(id: String, reason: String, actorId: String, actorName: String): Result<Expense> {
        val existing = expenseDao.getById(id) ?: return Result.Err(Errors.notFound("Expense $id not found"))
        // TIER 4 FIX — the no-self-approval rule applies to reject too.
        if (existing.submittedBy == actorId) {
            return Result.Err(Errors.forbidden(
                "Un demandeur ne peut pas rejeter sa propre dépense (règle d'auto-approbation)",
            ))
        }
        val updated = existing.copy(status = "rejected", approvedBy = actorId, approvedAt = Instant.now().toString(), notes = reason)
        expenseDao.update(updated)
        auditDao.upsert(auditContext.auditLog("expense.reject", "expense", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun disburse(id: String, actorId: String, actorName: String): Result<Expense> {
        val existing = expenseDao.getById(id) ?: return Result.Err(Errors.notFound("Expense $id not found"))
        val updated = existing.copy(status = "disbursed", disbursedAt = Instant.now().toString())
        expenseDao.update(updated)
        auditDao.upsert(auditContext.auditLog("expense.disburse", "expense", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun settleProof(id: String, proofPath: String, finalAmount: Long, actorId: String, actorName: String): Result<Expense> {
        val existing = expenseDao.getById(id) ?: return Result.Err(Errors.notFound("Expense $id not found"))
        // TIER 3 R18 FIX: previously `finalAmount` was silently dropped —
        // the `copy()` call didn't include it because the column didn't exist
        // on `ExpenseEntity`. Now that the column exists (migration v5→v6),
        // the final amount confirmed by the proof scan is persisted and
        // surfaces in the domain object so the desktop's expense report
        // can show "Requested: 5,000 DZD — Actual: 4,820 DZD".
        val updated = existing.copy(
            status = "settled",
            proofUrl = proofPath,
            settledAt = Instant.now().toString(),
            finalSpentAmount = finalAmount,
        )
        expenseDao.update(updated)
        auditDao.upsert(auditContext.auditLog("expense.settle", "expense", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }
}

// ─── Personnel Repository ───────────────────────────────────────────────────

@Singleton
class LocalPersonnelRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val personnelDao: PersonnelDao,
    private val auditDao: AuditLogDao,
) : com.example.domain.repository.PersonnelRepository {

    override fun observe(): Flow<List<Personnel>> =
        personnelDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByCategory(category: String): Flow<List<Personnel>> =
        personnelDao.observeAll().map { rows -> rows.filter { it.role == category }.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Personnel?> =
        personnelDao.observeById(id).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    override fun observeByUserId(userId: String): Flow<Personnel?> =
        personnelDao.observeById(userId).map { it?.let { e -> LocalMappers.run { e.toDomain() } } }

    override suspend fun createPersonnel(input: CreatePersonnelInput, actorId: String, actorName: String): Result<Personnel> {
        val now = Instant.now().toString()
        val entity = PersonnelEntity(
            id = "per-${UUID.randomUUID()}", tenantId = auditContext.tenantId(),
            code = "PER-${(personnelDao.countActive() + 1).toString().padStart(3, '0')}",
            firstName = input.firstName, lastName = input.lastName, role = input.roleId,
            departmentId = input.departmentId, departmentName = null,
            phone = input.phone, email = input.email, status = "active",
            hireDate = input.hireDate, weeklyHoursTarget = input.weeklyHoursTarget,
            createdAt = now, updatedAt = now,
        )
        personnelDao.upsert(entity)
        auditDao.upsert(auditContext.auditLog("personnel.create", "personnel", entity.id, actorId, actorName))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun updatePersonnel(id: String, input: UpdatePersonnelInput, actorId: String, actorName: String): Result<Personnel> {
        val existing = personnelDao.getById(id) ?: return Result.Err(Errors.notFound("Personnel $id not found"))
        val updated = existing.copy(
            phone = input.phone ?: existing.phone, email = input.email ?: existing.email,
            status = input.status ?: existing.status, updatedAt = Instant.now().toString(),
        )
        personnelDao.upsert(updated)
        auditDao.upsert(auditContext.auditLog("personnel.update", "personnel", id, actorId, actorName))
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    override suspend fun deletePersonnel(id: String, actorId: String, actorName: String): Result<Unit> {
        val existing = personnelDao.getById(id) ?: return Result.Err(Errors.notFound("Personnel $id not found"))
        personnelDao.upsert(existing.copy(status = "terminated", updatedAt = Instant.now().toString()))
        auditDao.upsert(auditContext.auditLog("personnel.delete", "personnel", id, actorId, actorName))
        return Result.Ok(Unit)
    }
}

// ─── Department Repository ──────────────────────────────────────────────────

@Singleton
class LocalDepartmentRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val departmentDao: DepartmentDao,
    private val auditDao: AuditLogDao,
) : DepartmentRepository {

    override fun observe(): Flow<List<Department>> =
        departmentDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeById(id: String): Flow<Department?> =
        departmentDao.observeAll().map { rows -> rows.firstOrNull { it.id == id }?.let { e -> LocalMappers.run { e.toDomain() } } }

    override suspend fun createDepartment(input: CreateDepartmentInput, actorId: String, actorName: String): Result<Department> {
        val entity = DepartmentEntity(
            id = "dep-${UUID.randomUUID()}", tenantId = auditContext.tenantId(),
            name = input.name, description = input.description,
            headPersonnelId = input.headPersonnelId, parentDepartmentId = input.parentDepartmentId,
            colorHex = input.colorHex, archivedAt = null,
        )
        departmentDao.upsertAll(listOf(entity))
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    // FIX (no-op): archive/unarchive previously returned Ok(Unit) without
    // touching the database — the UI showed success but the department never
    // changed state. Now the `archivedAt` column is really set/cleared and
    // the action is audit-logged.
    override suspend fun archiveDepartment(id: String, actorId: String, actorName: String): Result<Unit> {
        val existing = departmentDao.getById(id) ?: return Result.Err(Errors.notFound("Département $id introuvable"))
        departmentDao.upsertAll(listOf(existing.copy(archivedAt = Instant.now().toString())))
        auditDao.upsert(auditContext.auditLog("department.archive", "department", id, actorId, actorName))
        return Result.Ok(Unit)
    }

    override suspend fun unarchiveDepartment(id: String, actorId: String, actorName: String): Result<Unit> {
        val existing = departmentDao.getById(id) ?: return Result.Err(Errors.notFound("Département $id introuvable"))
        departmentDao.upsertAll(listOf(existing.copy(archivedAt = null)))
        auditDao.upsert(auditContext.auditLog("department.unarchive", "department", id, actorId, actorName))
        return Result.Ok(Unit)
    }
}

// ─── Subject Repository ─────────────────────────────────────────────────────

@Singleton
class LocalSubjectRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val subjectDao: SubjectDao,
    private val classSubjectDao: ClassSubjectDao,
    private val assessmentDao: AssessmentDao,
    private val auditDao: AuditLogDao,
) : SubjectRepository {

    override fun observe(): Flow<List<Subject>> =
        subjectDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeByLevel(level: String): Flow<List<Subject>> =
        // FIX: actually filter by level — previously returned ALL subjects.
        // Subjects scoped "all" apply to every level.
        subjectDao.observeAll().map { rows ->
            rows.filter { it.level == "all" || it.level == level }
                .map { LocalMappers.run { it.toDomain() } }
        }

    override fun observeByClass(classId: String): Flow<List<Subject>> =
        // FIX: the Room schema now HAS a per-class assignment table
        // (`class_subjects`, migration v9). When a class has explicit
        // assignments, return exactly those subjects; otherwise fall back to
        // all active subjects so the pickers (grade entry, homework push)
        // never regress to an empty list.
        combine(
            subjectDao.observeAll(),
            classSubjectDao.observeByClass(classId),
        ) { subjects, assignments ->
            if (assignments.isEmpty()) {
                subjects.filter { it.isActive }.map { LocalMappers.run { it.toDomain() } }
            } else {
                assignments.mapNotNull { assignment ->
                    subjects.firstOrNull { it.id == assignment.subjectId }?.let { subject ->
                        LocalMappers.run { subject.toDomain() }
                    }
                }
            }
        }

    override suspend fun createSubject(input: CreateSubjectInput, actorId: String, actorName: String): Result<Subject> {
        // FIX: persist the level + passing grade from the input — previously
        // the level was silently dropped.
        val entity = SubjectEntity(
            id = "sub-${UUID.randomUUID()}", tenantId = auditContext.tenantId(),
            code = input.code, name = input.name,
            category = if (input.isExtracurricular) "extracurricular" else "academic",
            coefficient = input.coefficient, weeklyHours = 0.0,
            isExtracurricular = input.isExtracurricular, isActive = true,
            level = input.level.ifBlank { "all" },
            passingGrade = input.passingGrade,
            // Vault §06.02 (iteration 2) — persist the per-COMPONENT
            // coefficients. Defaults (1, 1, 2) preserve the historical
            // recipe when the create dialog leaves them unset.
            coefficientDevoir1 = input.coefficientDevoir1,
            coefficientDevoir2 = input.coefficientDevoir2,
            coefficientExamen = input.coefficientExamen,
        )
        subjectDao.upsertAll(listOf(entity))
        // Vault §05.05/§05.06 — subject configuration is admin-controlled and
        // audited (create included).
        auditDao.upsert(
            auditContext.auditLog(
                action = com.example.core.AuditActions.SUBJECT_CREATE,
                entityType = "subject",
                entityId = entity.id,
                actorId = actorId,
                actorName = actorName,
                after = """{"code":"${entity.code}","coefficient":${entity.coefficient},"isExtracurricular":${entity.isExtracurricular},"coefD1":${entity.coefficientDevoir1},"coefD2":${entity.coefficientDevoir2},"coefEx":${entity.coefficientExamen}}""",
            )
        )
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    // FIX ("Not implemented"): updateSubject previously always failed.
    // Vault §05.06 — coefficient changes are AUDITED and trigger an automatic
    // GPA recompute for affected students. Android computes GPAs on read
    // (`computeOverallGpa` over assessment rows), so refreshing the
    // coefficient snapshot on the CURRENT academic year's rows IS the
    // recompute; archived years are append-only and never touched.
    //
    // ITERATION-2 (vault §06.02): the recompute now ALSO re-snapshots the
    // per-COMPONENT coefficients (D1/D2/Examen) onto each assessment row and
    // re-derives `subjectAverage` with the new weights — the SUBJECT-level
    // coefficient alone is no longer enough to express the full grading
    // recipe. Past-year rows stay immutable (append-only rule).
    override suspend fun updateSubject(id: String, input: UpdateSubjectInput, actorId: String, actorName: String): Result<Subject> {
        val existing = subjectDao.getById(id) ?: return Result.Err(Errors.notFound("Subject $id not found"))
        val subjectCoefChanged = input.coefficient != null && input.coefficient != existing.coefficient
        val coefD1Changed = input.coefficientDevoir1 != null && input.coefficientDevoir1 != existing.coefficientDevoir1
        val coefD2Changed = input.coefficientDevoir2 != null && input.coefficientDevoir2 != existing.coefficientDevoir2
        val coefExChanged = input.coefficientExamen != null && input.coefficientExamen != existing.coefficientExamen
        val anyCoefChanged = subjectCoefChanged || coefD1Changed || coefD2Changed || coefExChanged

        val updated = existing.copy(
            name = input.name ?: existing.name,
            coefficient = input.coefficient ?: existing.coefficient,
            passingGrade = input.passingGrade ?: existing.passingGrade,
            coefficientDevoir1 = input.coefficientDevoir1 ?: existing.coefficientDevoir1,
            coefficientDevoir2 = input.coefficientDevoir2 ?: existing.coefficientDevoir2,
            coefficientExamen = input.coefficientExamen ?: existing.coefficientExamen,
        )
        subjectDao.upsert(updated)

        // Vault §05.06 — audit the coefficient change (see 12. Security and
        // Audit: every mutation is traceable).
        auditDao.upsert(
            auditContext.auditLog(
                action = com.example.core.AuditActions.SUBJECT_UPDATE,
                entityType = "subject",
                entityId = id,
                actorId = actorId,
                actorName = actorName,
                after = """{"coefficient":{"from":${existing.coefficient},"to":${updated.coefficient}},"coefD1":{"from":${existing.coefficientDevoir1},"to":${updated.coefficientDevoir1}},"coefD2":{"from":${existing.coefficientDevoir2},"to":${updated.coefficientDevoir2}},"coefEx":{"from":${existing.coefficientExamen},"to":${updated.coefficientExamen}},"name":"${updated.name}"}""",
            )
        )

        // Vault §05.06 + §06.02 — automatic GPA recompute for affected
        // students: refresh the coefficient snapshot on the CURRENT year's
        // assessment rows only (past years are append-only). GPAs are derived
        // on read, so the next read reflects the new coefficients
        // immediately. The recompute re-derives `subjectAverage` inline
        // using the new per-component weights.
        if (anyCoefChanged) {
            val now = java.time.LocalDate.now()
            val currentYear =
                if (now.monthValue >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
            // SUBJECT-level coef refresh (single UPDATE, no per-row recompute).
            assessmentDao.updateCoefficientForSubjectYear(id, updated.coefficient, currentYear)
            // Per-COMPONENT coef refresh + subjectAverage recompute. Read +
            // rewrite in Kotlin because the average formula can't be expressed
            // as a single SQL UPDATE.
            val rows = assessmentDao.listBySubjectAndYear(id, currentYear)
            if (rows.isNotEmpty()) {
                val recomputed = rows.map { row ->
                    val newAvg = com.example.core.computeSubjectAverage(
                        row.devoir1, row.devoir2, row.examen,
                        updated.coefficientDevoir1, updated.coefficientDevoir2, updated.coefficientExamen,
                    )
                    row.copy(
                        coefficient = updated.coefficient,
                        coefficientDevoir1 = updated.coefficientDevoir1,
                        coefficientDevoir2 = updated.coefficientDevoir2,
                        coefficientExamen = updated.coefficientExamen,
                        subjectAverage = newAvg,
                    )
                }
                assessmentDao.upsertAll(recomputed)
            }
        }
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }

    // FIX (silent no-op): archiveSubject previously returned Ok without doing
    // anything — the UI showed success but the subject never disappeared.
    override suspend fun archiveSubject(id: String, actorId: String, actorName: String): Result<Unit> {
        val existing = subjectDao.getById(id) ?: return Result.Err(Errors.notFound("Subject $id not found"))
        subjectDao.upsert(existing.copy(isActive = false))
        return Result.Ok(Unit)
    }
    // FIX (silent no-op): assignSubjectToClass previously returned Ok without
    // persisting anything — the caller saw success but no assignment existed.
    // Now a real `class_subjects` row is written (idempotent per
    // class+subject pair) and the action is audit-logged.
    override suspend fun assignSubjectToClass(
        classId: String,
        subjectId: String,
        teacherId: String?,
        weeklyHours: Int,
        coefficient: Double,
        actorId: String,
        actorName: String,
    ): Result<Unit> {
        if (subjectDao.getById(subjectId) == null) {
            return Result.Err(Errors.notFound("Matière $subjectId introuvable"))
        }
        val existing = classSubjectDao.listByClass(classId)
            .firstOrNull { it.subjectId == subjectId }
        val entity = (existing ?: ClassSubjectEntity(
            id = "cls-sub-${UUID.randomUUID()}",
            tenantId = auditContext.tenantId(),
            classId = classId,
            subjectId = subjectId,
            teacherId = teacherId,
            weeklyHours = weeklyHours,
            coefficient = coefficient,
            createdAt = Instant.now().toString(),
        )).copy(
            teacherId = teacherId ?: existing?.teacherId,
            weeklyHours = weeklyHours,
            coefficient = coefficient,
        )
        classSubjectDao.upsert(entity)
        auditDao.upsert(
            auditContext.auditLog("subject.assignToClass", "class_subject", entity.id, actorId, actorName,
                after = """{"classId":"$classId","subjectId":"$subjectId","weeklyHours":$weeklyHours,"coefficient":$coefficient}"""),
        )
        return Result.Ok(Unit)
    }
}

// ─── Homework Repository ────────────────────────────────────────────────────

@Singleton
class LocalHomeworkRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val homeworkDao: HomeworkDao,
    // Vault §06.06 — the assignment must reach the Student Web Portal: the
    // push enqueues a sync queue entry (dispatcher-side table upsert).
    private val syncSupport: com.example.infrastructure.sync.SyncSupport? = null,
) : HomeworkRepository {

    override fun observeForClass(classId: String): Flow<List<Homework>> =
        homeworkDao.observeByClass(classId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeForTeacher(teacherId: String): Flow<List<Homework>> =
        homeworkDao.observeAll().map { rows -> rows.filter { it.teacherId == teacherId }.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun push(input: PushHomeworkInput, actorId: String, actorName: String): Result<Homework> {
        // Vault §06.06 — the due date must be a valid ISO date that is not
        // already past (late/retro-dated assignments confuse students and
        // parents about what was actually assigned).
        val due = try {
            java.time.LocalDate.parse(input.dueDate)
        } catch (e: Exception) {
            null
        } ?: return Result.Err(com.example.core.Errors.validation(
            "Invalid due date: '${input.dueDate}' (expected AAAA-MM-JJ).",
            userMessage = "Date de rendu invalide — format attendu AAAA-MM-JJ.",
        ))
        if (due.isBefore(java.time.LocalDate.now())) {
            return Result.Err(com.example.core.Errors.validation(
                "Due date '${input.dueDate}' is in the past.",
                userMessage = "La date de rendu ne peut pas être antérieure à aujourd'hui.",
            ))
        }

        val now = Instant.now().toString()
        val entity = HomeworkEntity(
            // T-024 / HOMEWORK-101: the server's `homework.id` column is a
            // UUID PRIMARY KEY (migration 0029) and this entity's id is pushed
            // VERBATIM into that column. The old "hwk-" prefix convention
            // ("hwk-${UUID.randomUUID()}") made every sync push fail with
            // `invalid input syntax for type uuid` — the canonical table has
            // received ZERO Android rows since the feature shipped. Homework
            // is the ONLY entity whose local id lands in a UUID column (all
            // other pushes go through RPCs that omit the id), so homework is
            // the ONLY entity that must use a bare UUID as its local id.
            id = UUID.randomUUID().toString(), tenantId = auditContext.tenantId(),
            classId = input.classId, subjectId = input.subjectId, subjectName = "",
            teacherId = actorId, teacherName = actorName,
            title = input.title, description = input.description, dueDate = input.dueDate,
            attachmentsJson = input.attachments.joinToString(",") { "\"$it\"" }.let { "[$it]" },
            createdAt = now,
            // Vault §06.06 — academic year scoping + portal push stamp
            // (persisted since MIGRATION_9_10, matching the backend columns).
            academicYear = input.academicYear,
            pushedAt = now,
        )
        homeworkDao.upsert(entity)

        // Vault §06.06 — push flow: SAVE → PUSH TO STUDENT WEB PORTAL → ALERT.
        // The sync queue entry carries the full row; the SyncQueueDispatcher
        // upserts it into the shared `homework` table (single canonical
        // record visible to students, parents and teachers).
        syncSupport?.enqueueOnly(
            entity = "homework",
            operation = "create",
            payload = buildHomeworkSyncPayload(entity),
            isMock = false,
            sourceScreen = "HomeworkPushScreen",
        )
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    private fun buildHomeworkSyncPayload(e: HomeworkEntity): String =
        kotlinx.serialization.json.buildJsonObject {
            put("id", e.id)
            put("tenantId", e.tenantId)
            put("classId", e.classId)
            put("subjectId", e.subjectId)
            put("subjectName", e.subjectName)
            put("teacherId", e.teacherId)
            put("teacherName", e.teacherName)
            put("title", e.title)
            put("description", e.description)
            put("dueDate", e.dueDate)
            put("attachments", kotlinx.serialization.json.JsonPrimitive(e.attachmentsJson))
            put("academicYear", e.academicYear ?: "")
            put("pushedAt", e.pushedAt ?: "")
            put("createdAt", e.createdAt)
        }.toString()
}

// ─── Notification Repository ────────────────────────────────────────────────

@Singleton
class LocalNotificationRepository @Inject constructor(
    private val notificationDao: NotificationDao,
) : NotificationRepository {

    override fun observe(): Flow<List<AppNotification>> =
        notificationDao.observeAll().map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override fun observeForSession(session: com.example.core.Session): Flow<List<AppNotification>> =
        notificationDao.observeForUser(session.userId).map { rows -> rows.map { LocalMappers.run { it.toDomain() } } }

    override suspend fun markRead(id: String): Result<Unit> { notificationDao.markRead(id); return Result.Ok(Unit) }

    // FIX (no-op): both methods previously returned Ok(Unit) without touching
    // the database — the "Tout marquer comme lu" button in the Alerts screen
    // looked like it worked but every notification stayed unread.
    override suspend fun markAllRead(): Result<Unit> {
        notificationDao.markAllRead()
        return Result.Ok(Unit)
    }

    override suspend fun dismiss(id: String): Result<Unit> {
        notificationDao.dismiss(id)
        return Result.Ok(Unit)
    }
}

// ─── Releve Repository ──────────────────────────────────────────────────────

@Singleton
class LocalReleveRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val releveDao: ReleveEntryDao,
) : ReleveRepository {

    override fun observeByPersonnel(personnelId: String, fromIso: String, toIso: String): Flow<Result<List<ReleveEntry>>> =
        releveDao.observeByPersonnel(personnelId).map { rows ->
            Result.Ok(rows.filter { it.date in fromIso..toIso }.map { LocalMappers.run { it.toDomain() } })
        }

    override fun observeRecent(): Flow<Result<List<ReleveEntry>>> =
        releveDao.observeAll().map { rows ->
            Result.Ok(rows.map { LocalMappers.run { it.toDomain() } })
        }

    override suspend fun logEntry(entry: ReleveEntry, actorId: String, actorName: String): Result<ReleveEntry> {
        // FIX (dropped field): the description was previously hardcoded to ""
        // — the entity column existed but the caller's activity details were
        // silently discarded. Build a real, human-readable description from
        // the entry itself.
        val description = buildString {
            append(entry.activity.displayFr)
            if (entry.hoursIn.isNotBlank()) append(" ${entry.hoursIn}")
            entry.hoursOut?.let { append(" → $it") }
        }
        val entity = ReleveEntryEntity(
            id = entry.id.ifBlank { "rel-${UUID.randomUUID()}" },
            tenantId = auditContext.tenantId(),
            personnelId = entry.personnelId, personnelName = entry.personnelName,
            date = entry.date, activityType = entry.activity.wireCode,
            description = description, durationMinutes = (entry.durationMinutes ?: 0).toInt(),
            recordedBy = actorId, recordedAt = Instant.now().toString(),
        )
        releveDao.upsert(entity)
        return Result.Ok(entry.copy(durationMinutes = entity.durationMinutes.toLong()))
    }
}

private fun ReleveEntryEntity.toDomain() = ReleveEntry(
    id = id, personnelId = personnelId, personnelName = personnelName,
    date = date, hoursIn = "", hoursOut = null,
    activity = com.example.domain.model.ReleveActivity.fromCode(activityType),
    classId = null, subjectId = null, taskId = null,
    recordedBy = recordedBy, recordedAt = recordedAt,
    durationMinutes = durationMinutes.toLong(),
)

// ─── Routing Repository ──────────────────────────────────────────────────────

/**
 * Room-backed routing repository — REAL implementation (previously a stub that
 * returned empty lists and "Not implemented" errors, leaving all three routing
 * screens permanently dead).
 *
 * - Vehicles / stops / trip history are observed from the `vehicles`,
 *   `routing_stops` and `trip_logs` Room tables.
 * - `optimizeRoute` runs the local TSP pipeline (greedy nearest-neighbour +
 *   2-opt refinement) anchored at the school, then tries to enrich the
 *   geometry with a real OSRM driving route; falls back to straight-line
 *   haversine when offline. The computed stop order is persisted back onto the
 *   stop rows so the ordering survives across screens.
 * - `startTrip` / `endTrip` write real `trip_logs` rows.
 */
@Singleton
class LocalRoutingRepository @Inject constructor(
    private val auditContext: AuditContext,
    private val db: ElImtiyazDatabase,
) : RoutingRepository {

    /** School anchor — Établissement Privé El-Imtiyaz, Boumerdes (Prices.md). */
    private val schoolAnchor = GeoPoint(36.7604, 3.4727)

    /** Lazy OSRM client (public demo server) — created once, failures degrade to haversine. */
    private val osrmClient: OsrmClient by lazy {
        OsrmClient(io.ktor.client.HttpClient(io.ktor.client.engine.android.Android))
    }

    override fun observeVehicles(): Flow<Result<List<com.example.domain.model.Vehicle>>> =
        db.vehicleDao().observeAll().map { rows ->
            Result.Ok(rows.map { LocalMappers.run { it.toDomain() } })
        }

    override fun observeStops(shift: com.example.domain.model.RoutingShift?): Flow<Result<List<com.example.domain.model.RoutingStop>>> =
        db.routingStopDao().observeAll().map { rows ->
            val filtered = if (shift == null) {
                rows
            } else {
                // "both"-shift stops are served in every shift window.
                rows.filter { it.shift == shift.wireCode || it.shift == com.example.domain.model.RoutingShift.Both.wireCode }
            }
            Result.Ok(filtered.sortedBy { it.orderInRoute }.map { LocalMappers.run { it.toDomain() } })
        }

    override fun observeTripHistory(): Flow<Result<List<com.example.domain.model.TripLog>>> =
        db.tripLogDao().observeAll().map { rows ->
            Result.Ok(rows.map { LocalMappers.run { it.toDomain() } })
        }

    override suspend fun optimizeRoute(
        vehicleId: String,
        shift: com.example.domain.model.RoutingShift,
        actorId: String,
        actorName: String,
    ): Result<com.example.domain.model.OptimizedRoute> {
        val vehicle = db.vehicleDao().getById(vehicleId)
            ?: return Result.Err(Errors.notFound("Véhicule $vehicleId introuvable"))

        val shiftStops = db.routingStopDao().getAll().filter {
            it.shift == shift.wireCode || it.shift == com.example.domain.model.RoutingShift.Both.wireCode
        }
        if (shiftStops.isEmpty()) {
            return Result.Err(Errors.notFound("Aucun arrêt configuré pour le créneau « ${shift.displayFr} »"))
        }

        // ── Stage 1+2: local TSP (greedy NN from the school, then 2-opt) ──
        val ordered = TspSolver.twoOptImprove(
            TspSolver.solveNearestNeighbor(shiftStops.map { LocalMappers.run { it.toDomain() } }, schoolAnchor),
        )

        // ── Stage 3: try to enrich with a real OSRM driving route ──
        val waypoints = listOf(schoolAnchor) + ordered.map { GeoPoint(it.lat, it.lng) }
        val osrmRoute = try { osrmClient.route(waypoints) } catch (_: Throwable) { null }

        val polyline: List<GeoPoint>
        val totalDistanceKm: Double
        val totalDurationMin: Double
        if (osrmRoute != null && osrmRoute.geometry.size >= 2) {
            polyline = osrmRoute.geometry
            totalDistanceKm = osrmRoute.distanceMeters / 1000.0
            totalDurationMin = osrmRoute.durationSeconds / 60.0
        } else {
            // Offline fallback: straight-line distance + urban driving estimate
            // (2.5 min/km + 1 min of dwell time per stop — mirrors the ETA
            // heuristic used by RoutingMapViewModel).
            polyline = waypoints
            totalDistanceKm = TspSolver.polylineDistanceKm(waypoints)
            totalDurationMin = totalDistanceKm * 2.5 + ordered.size
        }

        // Per-stop ETA from the previous stop (haversine-based estimate when
        // OSRM is unavailable; proportional share of OSRM duration otherwise).
        val withEta = ordered.mapIndexed { idx, stop ->
            val legKm = if (idx == 0) {
                TspSolver.haversineKm(schoolAnchor, GeoPoint(stop.lat, stop.lng))
            } else {
                TspSolver.haversineKm(GeoPoint(ordered[idx - 1].lat, ordered[idx - 1].lng), GeoPoint(stop.lat, stop.lng))
            }
            stop.copy(
                orderInRoute = idx + 1,
                estimatedMinutesFromPrevious = legKm * 2.5 + 1.0,
            )
        }

        // Persist the computed order back onto the stop rows so the hub, map
        // and history screens all agree on the route order.
        db.routingStopDao().upsertAll(
            withEta.map { stop ->
                val entity = shiftStops.first { it.id == stop.id }
                LocalMappers.run {
                    entity.toUpdatedEntity(orderInRoute = stop.orderInRoute, estimatedMinutesFromPrevious = stop.estimatedMinutesFromPrevious)
                }
            },
        )
        db.auditLogDao().upsert(
            auditContext.auditLog(
                "routing.optimize", "vehicle", vehicleId, actorId, actorName,
                after = """{"stops":${withEta.size},"distanceKm":${"%.2f".format(totalDistanceKm)},"shift":"${shift.wireCode}","source":"${if (osrmRoute != null) "osrm" else "tsp-local"}"}""",
            ),
        )

        return Result.Ok(
            com.example.domain.model.OptimizedRoute(
                vehicle = LocalMappers.run { vehicle.toDomain() },
                stops = withEta,
                totalDistanceKm = totalDistanceKm,
                totalDurationMin = totalDurationMin,
                polyline = polyline,
            ),
        )
    }

    override suspend fun startTrip(
        vehicleId: String,
        driverId: String,
        driverName: String,
    ): Result<com.example.domain.model.TripLog> {
        val vehicle = db.vehicleDao().getById(vehicleId)
            ?: return Result.Err(Errors.notFound("Véhicule $vehicleId introuvable"))

        val now = Instant.now()
        val plannedStops = db.routingStopDao().getAll().filter { it.isActive }
        val entity = TripLogEntity(
            id = "trp-${UUID.randomUUID()}",
            tenantId = auditContext.tenantId(),
            driverId = driverId,
            driverName = driverName,
            vehicleId = vehicleId,
            date = LocalDate.now(ZoneOffset.UTC).toString(),
            startTime = now.toString(),
            endTime = null,
            stopCount = plannedStops.size,
            stopsCompleted = 0,
            studentIdsJson = plannedStops.joinToString(",") { "\"${it.studentId}\"" }.let { "[$it]" },
            distanceKm = null,
            status = "running",
            notes = null,
            createdAt = now.toString(),
        )
        db.tripLogDao().upsert(entity)
        db.auditLogDao().upsert(
            auditContext.auditLog("routing.trip_start", "vehicle", vehicleId, driverId, driverName, after = """{"tripId":"${entity.id}","plannedStops":${entity.stopCount}}"""),
        )
        return Result.Ok(LocalMappers.run { entity.toDomain() })
    }

    override suspend fun endTrip(
        tripId: String,
        stopsCompleted: Int,
        totalDistanceKm: Double,
        actorId: String,
        actorName: String,
    ): Result<com.example.domain.model.TripLog> {
        val existing = db.tripLogDao().getById(tripId)
            ?: return Result.Err(Errors.notFound("Tournée $tripId introuvable"))
        val updated = existing.copy(
            endTime = Instant.now().toString(),
            stopsCompleted = stopsCompleted,
            distanceKm = totalDistanceKm,
            status = "ended",
        )
        db.tripLogDao().upsert(updated)
        db.auditLogDao().upsert(
            auditContext.auditLog(
                "routing.trip_end", "vehicle", updated.vehicleId.ifBlank { "trip" }, actorId, actorName,
                after = """{"tripId":"$tripId","stopsCompleted":$stopsCompleted,"distanceKm":${"%.2f".format(totalDistanceKm)}}""",
            ),
        )
        return Result.Ok(LocalMappers.run { updated.toDomain() })
    }
}

// ─── Workflow Repository ─────────────────────────────────────────────────────

@Singleton
class LocalWorkflowRepository @Inject constructor(
    private val workflowRunDao: WorkflowRunDao,
    private val provider: com.example.infrastructure.supabase.SupabaseClientProvider,
) : WorkflowRepository {

    private fun WorkflowRunEntity.toDomain() = com.example.domain.model.WorkflowRun(
        id = id, workflowId = workflowId, workflowName = workflowName,
        // T-054 (WEAK-008): the REAL trigger from the entity column — the
        // old hardcode made every run display "Manuel".
        trigger = com.example.domain.model.WorkflowTrigger.fromCode(trigger),
        status = com.example.domain.model.WorkflowRunStatus.fromCode(status),
        startedAt = startedAt, completedAt = finishedAt,
        durationMs = runCatching {
            val start = Instant.parse(startedAt)
            val end = finishedAt?.let { Instant.parse(it) }
            end?.let { it.toEpochMilli() - start.toEpochMilli() }
        }.getOrNull(),
        actorId = startedBy, actorName = null,
        errorMessage = errorMessage,
        outputPreview = resultJson?.takeIf { it.isNotBlank() && it != "{}" }?.take(120),
    )

    override fun observeRuns(limit: Int): Flow<Result<List<com.example.domain.model.WorkflowRun>>> =
        workflowRunDao.observeRecent().map { rows ->
            Result.Ok(rows.map { it.toDomain() })
        }

    override fun observeRunById(runId: String): Flow<Result<com.example.domain.model.WorkflowRun?>> =
        workflowRunDao.observeRecent().map { rows ->
            Result.Ok(rows.firstOrNull { it.id == runId }?.toDomain())
        }

    // FIX (success theater): retryRun previously inserted a fabricated run with
    // status="completed" the instant the button was pressed — no workflow ever
    // executed. Now the retry is honest:
    //   1. A new run row is created with status="running" (visible in monitor).
    //   2. If Supabase is configured, the server-side `workflow-execute` Edge
    //      Function is invoked (the workflow engine is server-only per plan
    //      §10.02) — the run stays "running" until the next pull finalizes it.
    //   3. Offline / unreachable → the run is finalized locally as "failed"
    //      with a truthful error and Result.Err is returned. No fake success.
    override suspend fun retryRun(runId: String, actorId: String, actorName: String): Result<String> {
        val original = workflowRunDao.getById(runId)
            ?: return Result.Err(Errors.notFound("Exécution $runId introuvable"))

        val newId = "wfr-${UUID.randomUUID()}"
        val now = Instant.now().toString()
        val newRun = WorkflowRunEntity(
            id = newId, tenantId = original.tenantId,
            workflowId = original.workflowId, workflowName = original.workflowName,
            // A user-initiated retry IS a manual run (matches the desktop
            // semantics for retried runs).
            trigger = "manual",
            status = "running", startedBy = actorId, startedAt = now,
            finishedAt = null, resultJson = null, errorMessage = null,
        )
        workflowRunDao.upsert(newRun)

        val invoked = com.example.infrastructure.supabase.NetworkTimeouts.guard(
            "workflow.retry", timeoutMs = 8_000L,
        ) {
            provider.functions.invoke(
                function = "workflow-execute",
                body = kotlinx.serialization.json.buildJsonObject {
                    put("workflowId", original.workflowId)
                    put("runId", newId)
                    put("triggeredBy", actorId)
                },
            )
        }

        return if (invoked != null && invoked.status.value in 200..299) {
            Result.Ok(newId)
        } else {
            val message = "Relance impossible : le moteur de workflows est côté serveur et n'est pas joignable."
            workflowRunDao.upsert(
                newRun.copy(status = "failed", finishedAt = Instant.now().toString(), errorMessage = message),
            )
            Result.Err(
                com.example.core.Errors.unknown(
                    "workflow retry failed (server unreachable)",
                    userMessage = message,
                ),
            )
        }
    }
}

// ─── Storage Repository ─────────────────────────────────────────────────────

/**
 * Local file-backed [StorageRepository] — REAL persistence (previously
 * `uploadProof` returned a fabricated `local://…` URL and silently DISCARDED
 * the bytes, so scanned payment/expense proofs were never actually stored).
 *
 * Proof files are written under `{filesDir}/proofs/{bucket}/{entityId}/` and
 * the returned `file://` URI resolves to a real on-device file that can be
 * re-opened, shared and re-uploaded later. When a Supabase Storage bucket is
 * configured, the bytes are ALSO pushed to the remote bucket and the remote
 * path is preferred (the local copy is kept as an offline cache).
 */
@Singleton
class LocalStorageRepository @Inject constructor(
    private val auditContext: AuditContext,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val provider: com.example.infrastructure.supabase.SupabaseClientProvider,
) : StorageRepository {

    override suspend fun uploadProof(
        bucket: String,
        entityId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): Result<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // ── Local file persistence (always — offline cache) ──
        val localFile = try {
            val dir = java.io.File(java.io.File(context.filesDir, "proofs"), "$bucket/$entityId")
            dir.mkdirs()
            val file = java.io.File(dir, fileName)
            file.writeBytes(bytes)
            file
        } catch (e: Exception) {
            null
        }

        // ── Remote (Supabase Storage) when configured ──
        val remotePath: String? = com.example.infrastructure.supabase.NetworkTimeouts.guard(
            "storage.uploadProof",
        ) {
            provider.storage.from(bucket).upload("$entityId/$fileName", bytes) {
                upsert = true
                contentType = io.ktor.http.ContentType.parse(mimeType)
            }
            "$entityId/$fileName"
        }
        if (remotePath != null) return@withContext Result.Ok(remotePath)

        // ── Offline / unconfigured: the real local file ──
        if (localFile != null) {
            Result.Ok("file://${localFile.absolutePath}")
        } else {
            Result.Err(
                com.example.core.Errors.unknown(
                    "uploadProof failed: could not write local proof file",
                    userMessage = "Échec de l'enregistrement du justificatif.",
                ),
            )
        }
    }

    override suspend fun createSignedUrl(bucket: String, path: String, expiresInSeconds: Long): Result<String> {
        // Try the remote bucket first when configured; otherwise resolve the
        // locally persisted file (an honest, resolvable file:// URI — the
        // previous implementation fabricated a URL that pointed at nothing).
        val remote = com.example.infrastructure.supabase.NetworkTimeouts.guard<String>(
            "storage.createSignedUrl",
        ) {
            provider.storage.from(bucket).createSignedUrl(path, kotlin.time.Duration.parseIsoString("PT${expiresInSeconds}S"))
        }
        if (remote != null) return Result.Ok(remote)

        val local = java.io.File(java.io.File(context.filesDir, "proofs"), "$bucket/$path")
        return if (local.exists()) {
            Result.Ok("file://${local.absolutePath}")
        } else {
            Result.Err(com.example.core.Errors.notFound("Aucun justificatif stocké pour $bucket/$path"))
        }
    }
}

// ─── Helper ─────────────────────────────────────────────────────────────────

