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
import com.example.domain.model.AmountBinItem
import com.example.domain.model.AppNotification
import com.example.domain.model.Assessment
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AuditLog
import com.example.domain.model.CategoryRevenueItem
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
import java.time.Instant
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

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
        val nowIso = Instant.now().toString()
        val todayIso = LocalDate.now(ZoneOffset.UTC).toString()
        val monthStart = OffsetDateTime.now(ZoneOffset.UTC)
            .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toString()
        val nextMonthStart = OffsetDateTime.now(ZoneOffset.UTC)
            .withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)
            .plusMonths(1).toInstant().toString()

        val activeStudents = g1.students.filter { it.status == "active" }
        val activeStaff = g1.staff.filter { it.status == "active" }
        val activeClasses = g2.classes.filter { it.isActive }

        // All-time cleared collections (Desktop Total Encaissé)
        val allPaidPayments = g1.payments.filter { it.status == "paid" }
        val totalRevenue = allPaidPayments.sumOf { it.amount }
        val totalOps = allPaidPayments.size

        // Mean, Median, and Volatility (Standard Deviation)
        val avgBasket = if (totalOps > 0) totalRevenue / totalOps else 0L
        val sortedAmounts = allPaidPayments.map { it.amount }.sorted()
        val medianBasket = if (totalOps > 0) {
            if (totalOps % 2 == 0) (sortedAmounts[totalOps / 2 - 1] + sortedAmounts[totalOps / 2]) / 2
            else sortedAmounts[totalOps / 2]
        } else 0L

        val volatility = if (totalOps > 1) {
            val meanDouble = totalRevenue.toDouble() / totalOps.toDouble()
            val sumDiffSquares = allPaidPayments.sumOf {
                val diff = it.amount.toDouble() - meanDouble
                diff * diff
            }
            kotlin.math.sqrt(sumDiffSquares / totalOps).toLong()
        } else 0L

        // Best Month
        val monthLabelsFr = listOf("Jan", "Fév", "Mar", "Avr", "Mai", "Juin", "Juil", "Août", "Sept", "Oct", "Nov", "Déc")
        val revenueByMonth = allPaidPayments.groupBy {
            val monthIdx = runCatching { LocalDate.parse(it.collectedAt.take(10)).monthValue - 1 }.getOrDefault(7)
            monthLabelsFr.getOrElse(monthIdx) { "Août" }
        }.mapValues { it.value.sumOf { p -> p.amount } }
        val bestMonthEntry = revenueByMonth.maxByOrNull { it.value }
        val bestMonthName = bestMonthEntry?.key ?: "Août"
        val bestMonthAmount = bestMonthEntry?.value ?: totalRevenue

        // Amount Bins Distribution (§15.16)
        val b0to5 = allPaidPayments.count { it.amount < 500_000L }
        val b5to10 = allPaidPayments.count { it.amount in 500_000L until 1_000_000L }
        val b10to20 = allPaidPayments.count { it.amount in 1_000_000L until 2_000_000L }
        val b20to50 = allPaidPayments.count { it.amount in 2_000_000L until 5_000_000L }
        val b50plus = allPaidPayments.count { it.amount >= 5_000_000L }
        val totalOpsDouble = totalOps.toDouble().coerceAtLeast(1.0)
        val amountBins = listOf(
            AmountBinItem("0–5k", b0to5, (b0to5 / totalOpsDouble) * 100.0),
            AmountBinItem("5k–10k", b5to10, (b5to10 / totalOpsDouble) * 100.0),
            AmountBinItem("10k–20k", b10to20, (b10to20 / totalOpsDouble) * 100.0),
            AmountBinItem("20k–50k", b20to50, (b20to50 / totalOpsDouble) * 100.0),
            AmountBinItem("50k+", b50plus, (b50plus / totalOpsDouble) * 100.0),
        )

        // Category Breakdown
        val scolariteAmount = allPaidPayments.filter { it.category == "tuition" || it.category == "other" }.sumOf { it.amount }
        val transportAmount = allPaidPayments.filter { it.category == "transport" }.sumOf { it.amount }
        val scolariteCount = allPaidPayments.count { it.category == "tuition" || it.category == "other" }
        val transportCount = allPaidPayments.count { it.category == "transport" }
        val totalRevDouble = totalRevenue.toDouble().coerceAtLeast(1.0)
        val categoryBreakdown = listOf(
            CategoryRevenueItem("scolarite", "Scolarité", scolariteAmount, scolariteCount, (scolariteAmount.toDouble() / totalRevDouble) * 100.0),
            CategoryRevenueItem("transport", "Transport", transportAmount, transportCount, (transportAmount.toDouble() / totalRevDouble) * 100.0),
        )

        val todayPayments = allPaidPayments.filter { it.collectedAt.startsWith(todayIso) }
        val todayRevenue = todayPayments.sumOf { it.amount }
        val todayPaymentsCount = todayPayments.size

        val monthlyPayments = allPaidPayments.filter {
            it.collectedAt >= monthStart && it.collectedAt < nextMonthStart
        }
        val monthlyRevenue = monthlyPayments.sumOf { it.amount }

        val pendingChecks = g1.payments.filter { it.method == "check" && it.status == "pending" }
        val pendingChecksCount = pendingChecks.size
        val pendingChecksAmount = pendingChecks.sumOf { it.amount }

        val submittedExpenses = g2.expenses.filter { it.status == "submitted" }
        val pendingExpensesCount = submittedExpenses.size
        val pendingExpensesAmount = submittedExpenses.sumOf { it.amount }

        val domainLedger = g2.ledger.map { LocalMappers.run { it.toDomain() } }
        val parentIds = domainLedger.map { it.parentId }.distinct()
        val ledgerOutstanding = parentIds.sumOf { pid ->
            val parentEntries = domainLedger.filter { it.parentId == pid }
            val dueDateMap = LedgerEngine.buildOverdueDueDateMap(parentEntries)
            LedgerEngine.computeParentSummary(parentEntries, pid, "", dueDateMap).totalOutstanding.coerceAtLeast(0L)
        }
        val instOutstanding = g2.installments
            .filter { it.status != "paid" }
            .sumOf { (it.amountDue - it.amountPaid - it.amountPending).coerceAtLeast(0L) }
        val totalOutstanding = if (ledgerOutstanding > 0L) ledgerOutstanding else instOutstanding

        val overdueDebtFromLedger = parentIds.sumOf { pid ->
            val parentEntries = domainLedger.filter { it.parentId == pid }
            val dueDateMap = LedgerEngine.buildOverdueDueDateMap(parentEntries)
            LedgerEngine.computeParentSummary(parentEntries, pid, "", dueDateMap).totalOverdue.coerceAtLeast(0L)
        }
        val overdueDebtFromInst = g2.installments
            .filter { it.status != "paid" && it.dueDate < nowIso }
            .sumOf { (it.amountDue - it.amountPaid - it.amountPending).coerceAtLeast(0L) }
        val overdueDebt = if (overdueDebtFromLedger > 0L) overdueDebtFromLedger else overdueDebtFromInst

        val overdueFamiliesFromLedger = parentIds.count { pid ->
            val parentEntries = domainLedger.filter { it.parentId == pid }
            val dueDateMap = LedgerEngine.buildOverdueDueDateMap(parentEntries)
            LedgerEngine.computeParentSummary(parentEntries, pid, "", dueDateMap).totalOverdue > 0L
        }
        val overdueFamiliesFromInst = g2.installments
            .filter { it.status != "paid" && it.dueDate < nowIso && (it.amountDue - it.amountPaid) > 0L }
            .map { it.parentId }
            .distinct()
            .size
        val overdueFamiliesCount = if (overdueFamiliesFromLedger > 0) overdueFamiliesFromLedger else overdueFamiliesFromInst

        // Recovery Funnel (§15) calculation
        val unpaidInstallmentsByParent = g2.installments
            .filter { it.status != "paid" && (it.amountDue - it.amountPaid) > 0L }
            .groupBy { it.parentId }
        var under60 = 0
        var bet61and90 = 0
        var over90 = 0
        unpaidInstallmentsByParent.forEach { (_, insts) ->
            val oldest = insts.filter { it.dueDate < nowIso }.minOfOrNull { it.dueDate }
            if (oldest != null) {
                val days = daysBetweenFloor(oldest)
                when {
                    days <= 60 -> under60++
                    days in 61..90 -> bet61and90++
                    else -> over90++
                }
            }
        }
        val funnelTotal = under60 + bet61and90 + over90
        val effectiveFunnelTotal = if (funnelTotal > 0) funnelTotal else overdueFamiliesCount
        val effectiveOver90 = if (funnelTotal > 0) over90 else overdueFamiliesCount
        val criticalPct = if (effectiveFunnelTotal > 0) ((effectiveOver90.toDouble() / effectiveFunnelTotal) * 100).toInt() else 100

        val todayAttendance = g2.attendance.filter { it.date == todayIso }
        val todayPresent = todayAttendance.count { it.status == "present" }
        val todayAbsent = todayAttendance.count { it.status == "absent_unexcused" || it.status == "absent_excused" }
        val attendanceRateToday = if (todayAttendance.isNotEmpty()) {
            (todayPresent.toDouble() / todayAttendance.size.toDouble() * 100.0)
        } else {
            100.0 // Canonical default when roll-call is clear
        }
        val classesWithRollCall = todayAttendance.map { it.classId }.distinct().size

        DashboardKpi(
            totalStudents = activeStudents.size,
            totalParents = g1.parents.size,
            totalStaff = activeStaff.size,
            totalFamilies = if (g1.parents.isNotEmpty()) g1.parents.size else activeStudents.map { it.parentId }.distinct().size,
            totalRevenue = totalRevenue,
            monthlyRevenue = monthlyRevenue,
            todayRevenue = todayRevenue,
            todayPaymentsCount = todayPaymentsCount,
            totalOperationsCount = totalOps,
            averageBasketAmount = avgBasket,
            medianBasketAmount = medianBasket,
            volatilityAmount = volatility,
            bestMonthName = bestMonthName,
            bestMonthAmount = bestMonthAmount,
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
            recoveryFunnelCriticalPct = criticalPct,
            recoveryFunnelOverdueTotal = effectiveFunnelTotal,
            recoveryFunnelUnder60Days = under60,
            recoveryFunnel61To90Days = bet61and90,
            recoveryFunnelOver90Days = effectiveOver90,
            amountBins = amountBins,
            categoryBreakdown = categoryBreakdown,
        )
    }

    override fun observeRevenueLast12Months(): Flow<List<com.example.domain.repository.RevenuePoint>> =
        db.paymentDao().observeAll().map { payments ->
            val now = LocalDate.now(ZoneOffset.UTC)
            (11 downTo 0).map { monthsBack ->
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

        // 1. Overdue Debt Alerts
        val overdueInstallments = installments.filter { it.status != "paid" && it.dueDate < nowIso }
        val overdueByParent = overdueInstallments.groupBy { it.parentId }
        val domainLedger = ledger.map { LocalMappers.run { it.toDomain() } }
        val ledgerByParent = domainLedger.groupBy { it.parentId }
        overdueByParent.entries
            .mapNotNull { (parentId, insts) ->
                val parent = parents.firstOrNull { it.id == parentId } ?: return@mapNotNull null
                val parentEntries = ledgerByParent[parentId] ?: emptyList()
                val dueDateMap = LedgerEngine.buildOverdueDueDateMap(parentEntries)
                val ledgerOverdue = if (parentEntries.isNotEmpty()) {
                    LedgerEngine.computeParentSummary(parentEntries, parentId, parent.fullName, dueDateMap)
                        .totalOverdue.coerceAtLeast(0L)
                } else 0L
                val instOverdue = insts.sumOf { (it.amountDue - it.amountPaid - it.amountPending).coerceAtLeast(0L) }
                val totalOverdue = if (ledgerOverdue > 0L) ledgerOverdue else instOverdue

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

    override suspend fun refreshKpis(): Result<Unit> = Result.Ok(Unit)

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