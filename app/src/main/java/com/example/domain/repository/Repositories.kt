package com.example.domain.repository

import com.example.core.LedgerEntry
import com.example.core.ParentLedgerSummary
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.core.Result
import com.example.core.Reconcile
import com.example.core.Session
import com.example.domain.model.AcademicClass
import com.example.domain.model.AppNotification
import com.example.domain.model.Assessment
import com.example.domain.model.AttendanceRecord
import com.example.domain.model.AuditLog
import com.example.domain.model.DashboardKpi
import com.example.domain.model.DebtSummary
import com.example.domain.model.Department
import com.example.domain.model.Expense
import com.example.domain.model.GradeLevelTuition
import com.example.domain.model.Homework
import com.example.domain.model.Installment
import com.example.domain.model.Parent
import com.example.domain.model.Payment
import com.example.domain.model.Personnel
import com.example.domain.model.PricingConfig
import com.example.domain.model.Student
import com.example.domain.model.Subject
import kotlinx.coroutines.flow.Flow

/**
 * Repository contracts — mirror desktop `src/domain/repository/`.
 * Mutations return `Result<T>`; live reads return `Flow<T>`.
 * Every mutation is audit-logged via the `write_audit_log` RPC.
 */

interface AuthRepository {
    suspend fun signIn(email: String, password: String): Result<Session>
    suspend fun signOut(): Result<Unit>
    suspend fun refreshSession(): Result<Session?>
    suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit>
    fun observeSession(): Flow<Session?>
}

interface ParentRepository {
    fun observe(): Flow<List<Parent>>
    fun observeById(id: String): Flow<Parent?>
    fun search(query: String): Flow<List<Parent>>
    suspend fun createParent(input: CreateParentInput, actorId: String, actorName: String): Result<Parent>
    suspend fun updateParent(id: String, input: UpdateParentInput, actorId: String, actorName: String): Result<Parent>
    suspend fun deleteParent(id: String, actorId: String, actorName: String): Result<Unit>
}

data class CreateParentInput(
    val firstName: String, val lastName: String, val phone: String,
    val email: String? = null, val occupation: String? = null,
    val address: String? = null, val transportDestination: String? = null,
    val preferredLanguage: String = "fr",
)
data class UpdateParentInput(
    val firstName: String? = null, val lastName: String? = null,
    val phone: String? = null, val email: String? = null,
    val occupation: String? = null, val address: String? = null,
    val transportDestination: String? = null, val preferredLanguage: String? = null,
)

interface StudentRepository {
    fun observe(): Flow<List<Student>>
    fun observeByParent(parentId: String): Flow<List<Student>>
    fun observeByClass(classId: String): Flow<List<Student>>
    fun observeById(id: String): Flow<Student?>
    fun search(query: String): Flow<List<Student>>
    suspend fun createStudent(input: CreateStudentInput, actorId: String, actorName: String): Result<Student>
    suspend fun updateStudent(id: String, input: UpdateStudentInput, actorId: String, actorName: String): Result<Student>
    suspend fun batchRegister(parent: CreateParentInput, students: List<CreateStudentInput>, actorId: String, actorName: String): Result<BatchRegisterResult>
    suspend fun promoteStudents(academicYear: String, decisions: List<PromotionDecision>, actorId: String, actorName: String): Result<Unit>
}

data class CreateStudentInput(
    val firstName: String, val lastName: String, val gender: String,
    val birthDate: String, val level: String, val gradeLevel: String,
    val classId: String? = null, val parentId: String? = null,
    val medicalNotes: String? = null,
)
data class UpdateStudentInput(
    val firstName: String? = null, val lastName: String? = null,
    val classId: String? = null, val status: String? = null,
    val medicalNotes: String? = null,
)
data class BatchRegisterResult(val parent: Parent, val students: List<Student>, val activationCode: String?)
data class PromotionDecision(val studentId: String, val decision: String, val note: String? = null)

interface ClassRepository {
    fun observe(): Flow<List<AcademicClass>>
    fun observeByLevel(level: String): Flow<List<AcademicClass>>
    fun observeById(id: String): Flow<AcademicClass?>
    suspend fun createClass(input: CreateClassInput, actorId: String, actorName: String): Result<AcademicClass>
    suspend fun updateClass(id: String, input: UpdateClassInput, actorId: String, actorName: String): Result<AcademicClass>
    suspend fun deleteClass(id: String, actorId: String, actorName: String): Result<Unit>
}

data class CreateClassInput(val name: String, val level: String, val gradeYear: Int, val room: String? = null, val capacity: Int, val academicYear: String, val homeroomTeacherId: String? = null)
data class UpdateClassInput(val name: String? = null, val room: String? = null, val capacity: Int? = null, val homeroomTeacherId: String? = null)

interface SubjectRepository {
    fun observe(): Flow<List<Subject>>
    fun observeByLevel(level: String): Flow<List<Subject>>
    fun observeByClass(classId: String): Flow<List<Subject>>
    suspend fun createSubject(input: CreateSubjectInput, actorId: String, actorName: String): Result<Subject>
    suspend fun updateSubject(id: String, input: UpdateSubjectInput, actorId: String, actorName: String): Result<Subject>
    suspend fun archiveSubject(id: String, actorId: String, actorName: String): Result<Unit>
    suspend fun assignSubjectToClass(classId: String, subjectId: String, teacherId: String?, weeklyHours: Int, coefficient: Int, actorId: String, actorName: String): Result<Unit>
}

data class CreateSubjectInput(val name: String, val nameAr: String?, val code: String, val level: String, val coefficient: Int, val isExtracurricular: Boolean, val passingGrade: Double = 10.0)
data class UpdateSubjectInput(val name: String? = null, val coefficient: Int? = null, val passingGrade: Double? = null)

interface GradeRepository {
    fun observeForStudent(studentId: String, term: String, academicYear: String): Flow<List<Assessment>>
    fun observeForClass(classId: String, subjectId: String, term: String, academicYear: String): Flow<List<Assessment>>
    suspend fun enterGrade(input: EnterGradeInput, actorId: String, actorName: String): Result<Assessment>
}

data class EnterGradeInput(val studentId: String, val subjectId: String, val classId: String, val term: String, val academicYear: String, val devoir1: Double?, val devoir2: Double?, val examen: Double?, val coefficient: Int)

interface AttendanceRepository {
    fun observeByClass(classId: String, date: String): Flow<List<AttendanceRecord>>
    fun observeByStudent(studentId: String): Flow<List<AttendanceRecord>>
    suspend fun recordRollCall(classId: String, date: String, session: String, records: List<RollCallEntry>, actorId: String, actorName: String): Result<Unit>
    suspend fun alertAbsences(studentIds: List<String>, actorId: String, actorName: String): Result<Unit>
}

data class RollCallEntry(val studentId: String, val status: String, val note: String? = null)

interface HomeworkRepository {
    fun observeForClass(classId: String): Flow<List<Homework>>
    fun observeForTeacher(teacherId: String): Flow<List<Homework>>
    suspend fun push(input: PushHomeworkInput, actorId: String, actorName: String): Result<Homework>
}

data class PushHomeworkInput(val classId: String, val subjectId: String, val title: String, val description: String, val dueDate: String, val attachments: List<String> = emptyList(), val academicYear: String)

interface PaymentRepository {
    fun observe(): Flow<List<Payment>>
    fun observeByParent(parentId: String): Flow<List<Payment>>
    fun observeByStudent(studentId: String): Flow<List<Payment>>
    fun observeById(id: String): Flow<Payment?>
    suspend fun collect(input: CollectPaymentInput, actorId: String, actorName: String): Result<Payment>
    suspend fun refund(paymentId: String, reason: String, actorId: String, actorName: String): Result<Payment>
    suspend fun adjust(input: AdjustAccountInput, actorId: String, actorName: String): Result<Unit>
}

data class CollectPaymentInput(
    val parentId: String, val studentId: String?, val amount: Long,
    val method: PaymentMethod, val category: PaymentCategory,
    val installmentId: String? = null, val notes: String? = null,
    val checkNumber: String? = null, val checkBankName: String? = null,
    val checkIssueDate: String? = null, val checkClearanceDate: String? = null,
    val transferReference: String? = null, val transferSourceBank: String? = null,
    val proofPath: String? = null,
)
data class AdjustAccountInput(val parentId: String, val studentId: String?, val category: PaymentCategory, val amount: Long, val reason: String, val receiptRef: String? = null)

interface InstallmentRepository {
    fun observeByParent(parentId: String): Flow<List<Installment>>
    fun observeByStudent(studentId: String): Flow<List<Installment>>
    fun observeById(id: String): Flow<Installment?>
    suspend fun markPaid(id: String, actorId: String, actorName: String): Result<Installment>
    suspend fun updateDueDate(id: String, dueDate: String, note: String?, actorId: String, actorName: String): Result<Installment>
    suspend fun regenerateForCycle(parentId: String, cycle: String, actorId: String, actorName: String): Result<List<Installment>>
    suspend fun findOverdue(): Result<List<Installment>>
}

interface DebtRepository {
    fun observeSummary(): Flow<List<DebtSummary>>
    fun observeParentProfile(parentId: String): Flow<ParentFinancialProfile?>
    suspend fun sendReminder(parentId: String, actorId: String, actorName: String): Result<Unit>
}

data class ParentFinancialProfile(
    val parentId: String, val parentName: String,
    val totalDue: Long, val totalPaid: Long, val totalOutstanding: Long, val overdueAmount: Long,
    val installments: List<Installment>, val recentPayments: List<Payment>,
)

interface ExpenseRepository {
    fun observe(): Flow<List<Expense>>
    fun observeByStatus(status: String): Flow<List<Expense>>
    fun observeById(id: String): Flow<Expense?>
    suspend fun submit(input: SubmitExpenseInput, actorId: String, actorName: String): Result<Expense>
    suspend fun approve(id: String, note: String, actorId: String, actorName: String): Result<Expense>
    suspend fun reject(id: String, reason: String, actorId: String, actorName: String): Result<Expense>
    suspend fun disburse(id: String, actorId: String, actorName: String): Result<Expense>
    suspend fun settleProof(id: String, proofPath: String, finalAmount: Long, actorId: String, actorName: String): Result<Expense>
}

data class SubmitExpenseInput(val title: String, val description: String, val amount: Long, val category: String, val payee: String, val urgency: String = "normal")

interface PersonnelRepository {
    fun observe(): Flow<List<Personnel>>
    fun observeByCategory(category: String): Flow<List<Personnel>>
    fun observeById(id: String): Flow<Personnel?>
    fun observeByUserId(userId: String): Flow<Personnel?>
    suspend fun createPersonnel(input: CreatePersonnelInput, actorId: String, actorName: String): Result<Personnel>
    suspend fun updatePersonnel(id: String, input: UpdatePersonnelInput, actorId: String, actorName: String): Result<Personnel>
    suspend fun deletePersonnel(id: String, actorId: String, actorName: String): Result<Unit>
}

data class CreatePersonnelInput(val firstName: String, val lastName: String, val staffCategory: String, val roleId: String, val departmentId: String?, val position: String, val phone: String, val email: String?, val hireDate: String, val salary: Long? = null, val weeklyHoursTarget: Int = 0)
data class UpdatePersonnelInput(val position: String? = null, val phone: String? = null, val email: String? = null, val salary: Long? = null, val status: String? = null, val departmentId: String? = null)

interface DepartmentRepository {
    fun observe(): Flow<List<Department>>
    fun observeById(id: String): Flow<Department?>
    suspend fun createDepartment(input: CreateDepartmentInput, actorId: String, actorName: String): Result<Department>
    suspend fun archiveDepartment(id: String, actorId: String, actorName: String): Result<Unit>
    suspend fun unarchiveDepartment(id: String, actorId: String, actorName: String): Result<Unit>
}

data class CreateDepartmentInput(val name: String, val description: String?, val headPersonnelId: String?, val parentDepartmentId: String?, val colorHex: String?)

interface AuditRepository {
    fun observe(limit: Int = 100): Flow<List<AuditLog>>
    fun observeByEntity(entityType: String, entityId: String): Flow<List<AuditLog>>
    suspend fun query(filter: AuditFilter): Result<List<AuditLog>>
    suspend fun log(input: AuditLogInput): Result<AuditLog>
}

data class AuditFilter(val action: String? = null, val entityType: String? = null, val entityId: String? = null, val actorId: String? = null, val from: String? = null, val to: String? = null, val limit: Int = 100, val offset: Int = 0)
data class AuditLogInput(val action: String, val entityType: String, val entityId: String, val beforeJson: String? = null, val afterJson: String? = null, val note: String? = null)

interface NotificationRepository {
    fun observe(): Flow<List<AppNotification>>
    fun observeForSession(session: Session): Flow<List<AppNotification>>
    suspend fun markRead(id: String): Result<Unit>
    suspend fun markAllRead(): Result<Unit>
    suspend fun dismiss(id: String): Result<Unit>
}

interface DashboardRepository {
    fun observeKpis(): Flow<DashboardKpi?>
    fun observeRevenueLast12Months(): Flow<List<RevenuePoint>>
    fun observeDebtByAging(): Flow<List<DebtSummary>>
    suspend fun refreshKpis(): Result<Unit>
}

data class RevenuePoint(val label: String, val amount: Long)

interface PricingRepository {
    fun observe(): Flow<PricingConfig?>
    fun observeGradeLevelTuition(): Flow<List<GradeLevelTuition>>
    suspend fun updateRegistrationFee(amount: Long, actorId: String, actorName: String): Result<Unit>
    suspend fun updateLatePenalty(amount: Long, actorId: String, actorName: String): Result<Unit>
    suspend fun updateTuitionForGradeLevel(gradeLevel: String, annualAmount: Long, tranches: Triple<Long, Long, Long>, actorId: String, actorName: String): Result<Unit>
}

interface LedgerRepository {
    fun observe(): Flow<List<LedgerEntry>>
    fun observeByParent(parentId: String): Flow<List<LedgerEntry>>
    fun observeByAccount(accountId: String): Flow<List<LedgerEntry>>
    suspend fun append(entry: LedgerEntry): Result<LedgerEntry>
    suspend fun appendMany(entries: List<LedgerEntry>): Result<List<LedgerEntry>>
    suspend fun reverse(originalId: String, reason: String, actorId: String, actorName: String): Result<LedgerEntry>
    suspend fun summary(parentId: String): Result<ParentLedgerSummary>
    suspend fun reconcile(): Result<Reconcile.Report>
}

interface StorageRepository {
    suspend fun uploadProof(bucket: String, entityId: String, fileName: String, bytes: ByteArray, mimeType: String): Result<String>
    suspend fun createSignedUrl(bucket: String, path: String, expiresInSeconds: Long = 300): Result<String>
}

object StorageBuckets {
    const val PAYMENT_PROOFS = "payment-proofs"
    const val EXPENSE_RECEIPTS = "expense-receipts"
    const val RECEIPTS = "receipts"
    const val STUDENT_DOCUMENTS = "student-documents"
    const val HOMEWORK_ATTACHMENTS = "homework-attachments"
    const val TASK_ATTACHMENTS = "task-attachments"
    const val CHAT_ATTACHMENTS = "chat-attachments"
    const val TENANT_ASSETS = "tenant-assets"
    const val AI_REPORTS = "ai-reports"
    const val IMPORT_REPORTS = "import-reports"
}
