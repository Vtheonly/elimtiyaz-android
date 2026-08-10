package com.example.infrastructure.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// ─── Parent DAO ──────────────────────────────────────────────────────────────

@Dao
interface ParentDao {
    @Query("SELECT * FROM parents ORDER BY lastName ASC, firstName ASC")
    fun observeAll(): Flow<List<ParentEntity>>

    @Query("SELECT * FROM parents ORDER BY lastName ASC, firstName ASC")
    suspend fun listAll(): List<ParentEntity>

    @Query("SELECT * FROM parents WHERE id = :id")
    fun observeById(id: String): Flow<ParentEntity?>

    @Query("SELECT * FROM parents WHERE id = :id")
    suspend fun getById(id: String): ParentEntity?

    @Query("SELECT * FROM parents WHERE firstName LIKE '%' || :q || '%' OR lastName LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%' OR code LIKE '%' || :q || '%' ORDER BY lastName ASC")
    fun search(q: String): Flow<List<ParentEntity>>

    @Query("SELECT * FROM parents WHERE activationCode = :code LIMIT 1")
    suspend fun findByActivationCode(code: String): ParentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ParentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ParentEntity>)

    @Update
    suspend fun update(row: ParentEntity)

    @Query("DELETE FROM parents WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM parents")
    suspend fun count(): Int
}

// ─── Student DAO ─────────────────────────────────────────────────────────────

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY lastName ASC, firstName ASC LIMIT 500")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY lastName ASC, firstName ASC")
    suspend fun listAll(): List<StudentEntity>

    @Query("SELECT * FROM students WHERE parentId = :parentId ORDER BY birthDate ASC")
    fun observeByParent(parentId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE parentId = :parentId ORDER BY birthDate ASC")
    suspend fun listByParent(parentId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE classId = :classId ORDER BY lastName ASC, firstName ASC")
    fun observeByClass(classId: String): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students WHERE classId = :classId ORDER BY lastName ASC, firstName ASC")
    suspend fun listByClass(classId: String): List<StudentEntity>

    @Query("SELECT * FROM students WHERE id = :id")
    fun observeById(id: String): Flow<StudentEntity?>

    @Query("SELECT * FROM students WHERE id = :id")
    suspend fun getById(id: String): StudentEntity?

    @Query("SELECT * FROM students WHERE firstName LIKE '%' || :q || '%' OR lastName LIKE '%' || :q || '%' OR code LIKE '%' || :q || '%' ORDER BY lastName ASC LIMIT 50")
    fun search(q: String): Flow<List<StudentEntity>>

    @Query("SELECT COUNT(*) FROM students WHERE status = 'active'")
    suspend fun countActive(): Int

    @Query("SELECT COUNT(*) FROM students WHERE status = 'active'")
    fun observeActiveCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: StudentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<StudentEntity>)

    @Update
    suspend fun update(row: StudentEntity)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ─── Class DAO ───────────────────────────────────────────────────────────────

@Dao
interface AcademicClassDao {
    @Query("SELECT * FROM classes ORDER BY level ASC, gradeYear ASC, name ASC")
    fun observeAll(): Flow<List<AcademicClassEntity>>

    @Query("SELECT * FROM classes ORDER BY level ASC, gradeYear ASC, name ASC")
    suspend fun listAll(): List<AcademicClassEntity>

    @Query("SELECT * FROM classes WHERE id = :id")
    fun observeById(id: String): Flow<AcademicClassEntity?>

    @Query("SELECT * FROM classes WHERE id = :id")
    suspend fun getById(id: String): AcademicClassEntity?

    @Query("SELECT * FROM classes WHERE gradeLevel = :gradeLevel AND isActive = 1 ORDER BY name ASC")
    suspend fun listByGradeLevel(gradeLevel: String): List<AcademicClassEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AcademicClassEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AcademicClassEntity>)

    @Update
    suspend fun update(row: AcademicClassEntity)

    @Query("DELETE FROM classes WHERE id = :id")
    suspend fun deleteById(id: String)
}

// ─── Subject DAO ─────────────────────────────────────────────────────────────

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects WHERE isActive = 1 ORDER BY name ASC")
    fun observeAll(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects WHERE isActive = 1 ORDER BY name ASC")
    suspend fun listAll(): List<SubjectEntity>

    @Query("SELECT * FROM subjects WHERE id = :id")
    suspend fun getById(id: String): SubjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<SubjectEntity>)
}

// ─── Attendance DAO ──────────────────────────────────────────────────────────

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE classId = :classId AND date = :date ORDER BY studentId ASC")
    fun observeByClassAndDate(classId: String, date: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE classId = :classId AND date = :date")
    suspend fun listByClassAndDate(classId: String, date: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND date >= :sinceDate ORDER BY date DESC")
    fun observeByStudent(studentId: String, sinceDate: String): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND date >= :sinceDate ORDER BY date DESC")
    suspend fun listByStudent(studentId: String, sinceDate: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE date = :date")
    suspend fun listByDate(date: String): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE studentId = :studentId AND date = :date AND session = :session LIMIT 1")
    suspend fun getByStudentDateSession(studentId: String, date: String, session: String): AttendanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AttendanceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AttendanceEntity)

    @Query("SELECT COUNT(*) FROM attendance WHERE date = :date AND status = 'present'")
    suspend fun countPresentByDate(date: String): Int

    @Query("SELECT COUNT(*) FROM attendance WHERE date = :date")
    suspend fun countTotalByDate(date: String): Int

    @Query("SELECT COUNT(*) FROM attendance WHERE studentId = :studentId AND status = 'absent_unexcused' AND date >= :sinceDate")
    suspend fun countUnexcusedAbsences(studentId: String, sinceDate: String): Int
}

// ─── Assessment DAO ──────────────────────────────────────────────────────────

@Dao
interface AssessmentDao {
    @Query("SELECT * FROM assessments WHERE studentId = :studentId AND term = :term AND academicYear = :year ORDER BY subjectId ASC")
    fun observeByStudentTerm(studentId: String, term: String, year: String): Flow<List<AssessmentEntity>>

    @Query("SELECT * FROM assessments WHERE classId = :classId AND term = :term AND academicYear = :year ORDER BY studentId ASC, subjectId ASC")
    fun observeByClassTerm(classId: String, term: String, year: String): Flow<List<AssessmentEntity>>

    @Query("SELECT * FROM assessments WHERE studentId = :studentId AND subjectId = :subjectId AND term = :term AND academicYear = :year LIMIT 1")
    suspend fun getByStudentSubjectTerm(studentId: String, subjectId: String, term: String, year: String): AssessmentEntity?

    @Query("SELECT * FROM assessments WHERE classId = :classId AND academicYear = :year")
    suspend fun listByClass(classId: String, year: String): List<AssessmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AssessmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AssessmentEntity)
}

// ─── Homework DAO ────────────────────────────────────────────────────────────

@Dao
interface HomeworkDao {
    @Query("SELECT * FROM homework WHERE classId = :classId ORDER BY dueDate DESC")
    fun observeByClass(classId: String): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM homework ORDER BY createdAt DESC LIMIT 100")
    fun observeAll(): Flow<List<HomeworkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: HomeworkEntity)
}

// ─── Payment DAO ─────────────────────────────────────────────────────────────

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY collectedAt DESC LIMIT 200")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY collectedAt DESC LIMIT 200")
    suspend fun listAll(): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE parentId = :parentId ORDER BY collectedAt DESC")
    fun observeByParent(parentId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE parentId = :parentId ORDER BY collectedAt DESC")
    suspend fun listByParent(parentId: String): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE studentId = :studentId ORDER BY collectedAt DESC")
    fun observeByStudent(studentId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE id = :id")
    fun observeById(id: String): Flow<PaymentEntity?>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getById(id: String): PaymentEntity?

    @Query("SELECT * FROM payments WHERE receiptNumber = :receipt LIMIT 1")
    suspend fun getByReceipt(receipt: String): PaymentEntity?

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'paid' AND collectedAt >= :since AND collectedAt < :until")
    suspend fun sumPaidBetween(since: String, until: String): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM payments WHERE status = 'paid'")
    suspend fun sumAllPaid(): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: PaymentEntity)

    @Update
    suspend fun update(row: PaymentEntity)
}

// ─── Installment DAO ─────────────────────────────────────────────────────────

@Dao
interface InstallmentDao {
    @Query("SELECT * FROM installments ORDER BY dueDate ASC")
    fun observeAll(): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments WHERE parentId = :parentId ORDER BY dueDate ASC")
    fun observeByParent(parentId: String): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments WHERE parentId = :parentId ORDER BY dueDate ASC")
    suspend fun listByParent(parentId: String): List<InstallmentEntity>

    @Query("SELECT * FROM installments WHERE studentId = :studentId ORDER BY dueDate ASC")
    fun observeByStudent(studentId: String): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments WHERE studentId = :studentId ORDER BY dueDate ASC")
    suspend fun listByStudent(studentId: String): List<InstallmentEntity>

    @Query("SELECT * FROM installments WHERE id = :id")
    fun observeById(id: String): Flow<InstallmentEntity?>

    @Query("SELECT * FROM installments WHERE id = :id")
    suspend fun getById(id: String): InstallmentEntity?

    @Query("SELECT * FROM installments WHERE status != 'paid' AND dueDate < :now ORDER BY dueDate ASC")
    suspend fun listOverdue(now: String): List<InstallmentEntity>

    @Query("SELECT * FROM installments WHERE category = :category ORDER BY dueDate ASC")
    suspend fun listByCategory(category: String): List<InstallmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: InstallmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<InstallmentEntity>)

    @Update
    suspend fun update(row: InstallmentEntity)
}

// ─── Ledger DAO ──────────────────────────────────────────────────────────────

@Dao
interface LedgerEntryDao {
    @Query("SELECT * FROM ledger_entries ORDER BY at ASC LIMIT 2000")
    fun observeAll(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries ORDER BY at ASC")
    suspend fun listAll(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE parentId = :parentId ORDER BY at ASC")
    fun observeByParent(parentId: String): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE parentId = :parentId ORDER BY at ASC")
    suspend fun listByParent(parentId: String): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE accountId = :accountId ORDER BY at ASC")
    fun observeByAccount(accountId: String): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE id = :id")
    suspend fun getById(id: String): LedgerEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: LedgerEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<LedgerEntryEntity>)
}

// ─── Expense DAO ─────────────────────────────────────────────────────────────

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY submittedAt DESC LIMIT 200")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE status = :status ORDER BY submittedAt DESC")
    fun observeByStatus(status: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: String): ExpenseEntity?

    @Query("SELECT COUNT(*) FROM expenses WHERE status = 'submitted'")
    suspend fun countPending(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ExpenseEntity)

    @Update
    suspend fun update(row: ExpenseEntity)
}

// ─── Personnel DAO ───────────────────────────────────────────────────────────

@Dao
interface PersonnelDao {
    @Query("SELECT * FROM personnel WHERE status = 'active' ORDER BY lastName ASC, firstName ASC")
    fun observeAll(): Flow<List<PersonnelEntity>>

    @Query("SELECT * FROM personnel ORDER BY lastName ASC")
    suspend fun listAll(): List<PersonnelEntity>

    @Query("SELECT * FROM personnel WHERE id = :id")
    suspend fun getById(id: String): PersonnelEntity?

    @Query("SELECT * FROM personnel WHERE id = :id")
    fun observeById(id: String): Flow<PersonnelEntity?>

    @Query("SELECT COUNT(*) FROM personnel WHERE status = 'active'")
    suspend fun countActive(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: PersonnelEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<PersonnelEntity>)
}

// ─── Department DAO ──────────────────────────────────────────────────────────

@Dao
interface DepartmentDao {
    @Query("SELECT * FROM departments WHERE archivedAt IS NULL ORDER BY name ASC")
    fun observeAll(): Flow<List<DepartmentEntity>>

    @Query("SELECT * FROM departments WHERE archivedAt IS NULL ORDER BY name ASC")
    suspend fun listAll(): List<DepartmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<DepartmentEntity>)
}

// ─── Pricing DAO ─────────────────────────────────────────────────────────────

@Dao
interface PricingConfigDao {
    @Query("SELECT * FROM pricing_config WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<PricingConfigEntity?>

    @Query("SELECT * FROM pricing_config WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): PricingConfigEntity?

    @Query("SELECT * FROM pricing_discounts WHERE isActive = 1")
    suspend fun listActiveDiscounts(): List<PricingDiscountEntity>

    @Query("SELECT * FROM grade_level_tuition ORDER BY annualAmount ASC")
    suspend fun listGradeLevelTuition(): List<GradeLevelTuitionEntity>

    @Query("SELECT * FROM grade_level_tuition WHERE gradeLevel = :gradeLevel LIMIT 1")
    suspend fun getTuitionByGrade(gradeLevel: String): GradeLevelTuitionEntity?

    @Query("SELECT * FROM transport_pricing")
    suspend fun listTransportPricing(): List<TransportPricingEntity>

    @Query("SELECT * FROM transport_pricing WHERE destination = :destination LIMIT 1")
    suspend fun getTransportByDestination(destination: String): TransportPricingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConfig(row: PricingConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDiscounts(rows: List<PricingDiscountEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGradeLevelTuition(rows: List<GradeLevelTuitionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTransportPricing(rows: List<TransportPricingEntity>)
}

// ─── Notification DAO ────────────────────────────────────────────────────────

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC LIMIT 100")
    fun observeAll(): Flow<List<NotificationEntity>>

    @Query("SELECT * FROM notifications WHERE targetUserId IS NULL OR targetUserId = :userId ORDER BY createdAt DESC LIMIT 100")
    fun observeForUser(userId: String): Flow<List<NotificationEntity>>

    @Query("SELECT COUNT(*) FROM notifications WHERE isRead = 0")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<NotificationEntity>)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)
}

// ─── Audit Log DAO ───────────────────────────────────────────────────────────

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs WHERE entityId = :entityId ORDER BY createdAt DESC")
    suspend fun listByEntity(entityId: String): List<AuditLogEntity>

    @Query("SELECT * FROM audit_logs WHERE entityType = :entityType ORDER BY createdAt DESC LIMIT 50")
    suspend fun listByType(entityType: String): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AuditLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AuditLogEntity>)
}

// ─── Routing / Releve / Workflow DAOs ────────────────────────────────────────

@Dao
interface TripLogDao {
    @Query("SELECT * FROM trip_logs ORDER BY date DESC LIMIT 100")
    fun observeAll(): Flow<List<TripLogEntity>>

    @Query("SELECT * FROM trip_logs WHERE driverId = :driverId ORDER BY date DESC LIMIT 50")
    fun observeByDriver(driverId: String): Flow<List<TripLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TripLogEntity)
}

@Dao
interface ReleveEntryDao {
    @Query("SELECT * FROM releve_entries WHERE personnelId = :personnelId ORDER BY date DESC LIMIT 100")
    fun observeByPersonnel(personnelId: String): Flow<List<ReleveEntryEntity>>

    @Query("SELECT * FROM releve_entries ORDER BY date DESC LIMIT 200")
    fun observeAll(): Flow<List<ReleveEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ReleveEntryEntity)
}

@Dao
interface WorkflowRunDao {
    @Query("SELECT * FROM workflow_runs ORDER BY startedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<WorkflowRunEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: WorkflowRunEntity)
}
