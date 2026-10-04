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
    @Query("SELECT * FROM parents ORDER BY CASE WHEN displayName IS NOT NULL AND displayName != '' THEN displayName ELSE lastName END ASC, firstName ASC")
    fun observeAll(): Flow<List<ParentEntity>>

    @Query("SELECT * FROM parents ORDER BY CASE WHEN displayName IS NOT NULL AND displayName != '' THEN displayName ELSE lastName END ASC, firstName ASC")
    suspend fun listAll(): List<ParentEntity>

    @Query("SELECT * FROM parents WHERE id = :id")
    fun observeById(id: String): Flow<ParentEntity?>

    @Query("SELECT * FROM parents WHERE id = :id")
    suspend fun getById(id: String): ParentEntity?

    @Query("""
        SELECT * FROM parents 
        WHERE (:q = '' 
           OR firstName LIKE '%' || :q || '%' 
           OR lastName LIKE '%' || :q || '%' 
           OR displayName LIKE '%' || :q || '%' 
           OR phone LIKE '%' || :q || '%' 
           OR whatsapp LIKE '%' || :q || '%' 
           OR code LIKE '%' || :q || '%')
        ORDER BY CASE WHEN displayName IS NOT NULL AND displayName != '' THEN displayName ELSE lastName END ASC, firstName ASC
    """)
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

    /** T-494 (DATA-059): evict the demo-seeded families after a successful pull (exact ids). */
    @Query("DELETE FROM parents WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

// ─── Student DAO ─────────────────────────────────────────────────────────────

@Dao
interface StudentDao {
    @Query("SELECT * FROM students ORDER BY CASE WHEN displayName IS NOT NULL AND displayName != '' THEN displayName ELSE lastName END ASC, firstName ASC")
    fun observeAll(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students ORDER BY CASE WHEN displayName IS NOT NULL AND displayName != '' THEN displayName ELSE lastName END ASC, firstName ASC")
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

    @Query("""
        SELECT * FROM students 
        WHERE (:q = '' 
           OR firstName LIKE '%' || :q || '%' 
           OR lastName LIKE '%' || :q || '%' 
           OR displayName LIKE '%' || :q || '%' 
           OR code LIKE '%' || :q || '%' 
           OR gradeLevel LIKE '%' || :q || '%')
        ORDER BY CASE WHEN displayName IS NOT NULL AND displayName != '' THEN displayName ELSE lastName END ASC, firstName ASC
    """)
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

    /** T-494 (DATA-059): evict the demo-seeded students after a successful pull (exact ids). */
    @Query("DELETE FROM students WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
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

    @Query("SELECT COUNT(*) FROM classes WHERE isActive = 1")
    fun observeActiveCount(): Flow<Int>

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
    suspend fun upsert(row: SubjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<SubjectEntity>)
}

// ─── Attendance DAO ──────────────────────────────────────────────────────────

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance ORDER BY date DESC LIMIT 1000")
    fun observeAll(): Flow<List<AttendanceEntity>>

    @Query("SELECT * FROM attendance WHERE date = :date ORDER BY classId ASC, studentId ASC")
    fun observeByDate(date: String): Flow<List<AttendanceEntity>>

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

    @Query("SELECT COUNT(*) FROM attendance")
    suspend fun countAll(): Int

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

    /** T-494 (DATA-059): evict the demo-seeded attendance rows — the seeder's unique `att-seed-` infix (local roll calls are `att-<UUID>`, pulled rows are UUIDs). */
    @Query("DELETE FROM attendance WHERE id LIKE 'att-seed-%'")
    suspend fun deleteSeeded()

    /** T-494 test support: direct row access. */
    @Query("SELECT * FROM attendance WHERE id = :id")
    suspend fun getById(id: String): AttendanceEntity?
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

    @Query("SELECT * FROM assessments WHERE studentId = :studentId ORDER BY academicYear ASC, term ASC, subjectId ASC")
    fun observeByStudent(studentId: String): Flow<List<AssessmentEntity>>

    /**
     * T-340 (STATS-400): the full assessments stream feeds the triple-risk
     * radar's GPA derivation (the desktop operational-query-engine parity).
     */
    @Query("SELECT * FROM assessments ORDER BY academicYear ASC, term ASC, studentId ASC, subjectId ASC")
    fun observeAll(): Flow<List<AssessmentEntity>>

    @Query("UPDATE assessments SET coefficient = :coefficient WHERE subjectId = :subjectId AND academicYear = :academicYear")
    suspend fun updateCoefficientForSubjectYear(subjectId: String, coefficient: Double, academicYear: String)

    @Query("SELECT * FROM assessments WHERE subjectId = :subjectId AND academicYear = :academicYear")
    suspend fun listBySubjectAndYear(subjectId: String, academicYear: String): List<AssessmentEntity>

    @Query("SELECT COUNT(*) FROM assessments")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<AssessmentEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AssessmentEntity)

    /** T-494 (DATA-059): evict the demo-seeded grades — the seeder's id shape is asm-<studentId>-sub-<name>-t<term>; the `-sub-` infix can NEVER appear in a UUID (s/u are not hex) so this pattern cannot touch a local `asm-<UUID>` grade or a pulled UUID row. */
    @Query("DELETE FROM assessments WHERE id LIKE 'asm-%-sub-%'")
    suspend fun deleteSeeded()

    /** T-494 test support: direct row access. */
    @Query("SELECT * FROM assessments WHERE id = :id")
    suspend fun getById(id: String): AssessmentEntity?
}

// ─── Homework DAO ────────────────────────────────────────────────────────────

@Dao
interface HomeworkDao {
    @Query("SELECT * FROM homework WHERE classId = :classId ORDER BY dueDate DESC")
    fun observeByClass(classId: String): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM homework ORDER BY createdAt DESC LIMIT 100")
    fun observeAll(): Flow<List<HomeworkEntity>>

    @Query("SELECT * FROM homework WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HomeworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: HomeworkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<HomeworkEntity>)

    @Query("DELETE FROM homework WHERE id = 'hwk-' || :serverId")
    suspend fun deleteLegacyPrefixedCopy(serverId: String)
}

// ─── Payment DAO ─────────────────────────────────────────────────────────────

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY collectedAt DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY collectedAt DESC")
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<PaymentEntity>)

    @Update
    suspend fun update(row: PaymentEntity)

    /** T-494 (DATA-059): evict the demo-seeded payment + its family rows after a successful pull (real local ids are pay-<UUID>, real pulled ids are UUIDs). */
    @Query("DELETE FROM payments WHERE parentId IN (:parentIds) OR id IN (:extraIds)")
    suspend fun deleteDemoRows(parentIds: List<String>, extraIds: List<String>)
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

    @Query("SELECT * FROM installments ORDER BY dueDate ASC")
    suspend fun listAll(): List<InstallmentEntity>

    @Query("SELECT * FROM installments WHERE id = :id")
    fun observeById(id: String): Flow<InstallmentEntity?>

    @Query("SELECT * FROM installments WHERE id = :id")
    suspend fun getById(id: String): InstallmentEntity?

    @Query("SELECT * FROM installments WHERE status != 'paid' AND dueDate < :now ORDER BY dueDate ASC")
    fun observeOverdue(now: String): Flow<List<InstallmentEntity>>

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

    /** T-494 (DATA-059): evict the demo-seeded installments after a successful pull — scoped by the demo PARENT ids (the seeder's ins-stu-* pattern is ALSO used by batchRegister for REAL students, so the parent scope is the only safe discriminator). */
    @Query("DELETE FROM installments WHERE parentId IN (:parentIds)")
    suspend fun deleteByParentIds(parentIds: List<String>)
}

// ─── Ledger DAO ──────────────────────────────────────────────────────────────

@Dao
interface LedgerEntryDao {
    @Query("SELECT * FROM ledger_entries ORDER BY at DESC")
    fun observeAll(): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries ORDER BY at DESC")
    suspend fun listAll(): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE parentId = :parentId ORDER BY at DESC")
    fun observeByParent(parentId: String): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE parentId = :parentId ORDER BY at DESC")
    suspend fun listByParent(parentId: String): List<LedgerEntryEntity>

    @Query("SELECT * FROM ledger_entries WHERE accountId = :accountId ORDER BY at DESC")
    fun observeByAccount(accountId: String): Flow<List<LedgerEntryEntity>>

    @Query("SELECT * FROM ledger_entries WHERE id = :id")
    suspend fun getById(id: String): LedgerEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: LedgerEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<LedgerEntryEntity>)

    /** T-494 (DATA-059): evict the demo-seeded ledger rows after a successful pull — scoped by the demo PARENT ids (covers the led-par-…, led-pay-001 and led-credit-… shapes without touching a real row; real local ids come from generateEntryId, real pulled ids are UUIDs). */
    @Query("DELETE FROM ledger_entries WHERE parentId IN (:parentIds) OR id IN (:extraIds)")
    suspend fun deleteDemoRows(parentIds: List<String>, extraIds: List<String>)
}

// ─── Expense DAO ─────────────────────────────────────────────────────────────

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses ORDER BY submittedAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE status = :status ORDER BY submittedAt DESC")
    fun observeByStatus(status: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun getById(id: String): ExpenseEntity?

    @Query("SELECT COUNT(*) FROM expenses WHERE status = 'submitted'")
    suspend fun countPending(): Int

    @Query("SELECT COUNT(*) FROM expenses WHERE status = 'submitted'")
    fun observePendingCount(): Flow<Int>

    /** T-492 (SYNC-302): the ticket-number collision check (the desktop's generateTicketNumber convention). */
    @Query("SELECT COUNT(*) FROM expenses WHERE requestCode = :requestCode")
    suspend fun countByRequestCode(requestCode: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ExpenseEntity>)

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

    @Query("SELECT COUNT(*) FROM personnel WHERE status = 'active'")
    fun observeActiveCount(): Flow<Int>

    /** T-494 (DATA-059): evict the demo-seeded workers after a successful pull (exact ids — never a pattern that could match a server UUID row). */
    @Query("DELETE FROM personnel WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

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

    @Query("SELECT * FROM departments WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DepartmentEntity?

    /** T-494 (DATA-059): evict the demo-seeded departments after a successful pull (exact ids). */
    @Query("DELETE FROM departments WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

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

    @Query("SELECT * FROM notifications ORDER BY createdAt DESC LIMIT 200")
    suspend fun listAll(): List<NotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<NotificationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: NotificationEntity)

    @Query(
        "DELETE FROM notifications WHERE NOT (" +
            "(targetUserId IS NOT NULL AND targetUserId = :userId)" +
            " OR (targetRole IS NOT NULL AND targetRole IN (:roles))" +
            " OR (:canSeeTenantBroadcasts = 1 AND targetUserId IS NULL AND targetRole IS NULL)" +
            ")",
    )
    suspend fun evictNotVisibleTo(userId: String, roles: List<String>, canSeeTenantBroadcasts: Int)

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun evictServerDismissed(ids: List<String>)

    @Query("UPDATE notifications SET isRead = 1 WHERE id = :id")
    suspend fun markRead(id: String)

    @Query("UPDATE notifications SET isRead = 1 WHERE isRead = 0")
    suspend fun markAllRead()

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun dismiss(id: String)
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

    @Query("SELECT * FROM trip_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TripLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: TripLogEntity)
}

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles WHERE isActive = 1 ORDER BY plate")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): VehicleEntity?

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<VehicleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: VehicleEntity)

    /** T-494 (DATA-059): evict the demo-seeded vehicles (exact ids). */
    @Query("DELETE FROM vehicles WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface RoutingStopDao {
    @Query("SELECT * FROM routing_stops WHERE isActive = 1")
    fun observeAll(): Flow<List<RoutingStopEntity>>

    @Query("SELECT * FROM routing_stops WHERE isActive = 1")
    suspend fun getAll(): List<RoutingStopEntity>

    @Query("SELECT COUNT(*) FROM routing_stops")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<RoutingStopEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: RoutingStopEntity)

    /** T-494 (DATA-059): evict the demo-seeded routing stops (exact ids). */
    @Query("DELETE FROM routing_stops WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

@Dao
interface ClassSubjectDao {
    @Query("SELECT * FROM class_subjects WHERE classId = :classId")
    suspend fun listByClass(classId: String): List<ClassSubjectEntity>

    @Query("SELECT * FROM class_subjects WHERE classId = :classId")
    fun observeByClass(classId: String): Flow<List<ClassSubjectEntity>>

    @Query("SELECT COUNT(*) FROM class_subjects WHERE classId = :classId AND subjectId = :subjectId")
    suspend fun countAssignment(classId: String, subjectId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ClassSubjectEntity)
}

@Dao
interface ReleveEntryDao {
    @Query("SELECT * FROM releve_entries WHERE personnelId = :personnelId ORDER BY date DESC LIMIT 100")
    fun observeByPersonnel(personnelId: String): Flow<List<ReleveEntryEntity>>

    @Query("SELECT * FROM releve_entries ORDER BY date DESC")
    fun observeAll(): Flow<List<ReleveEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ReleveEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ReleveEntryEntity>)

    /** T-494 (DATA-059): evict the demo-seeded timesheet rows (scoped by the demo PERSONNEL ids — the rel-t* rows belong to the seeded teachers). */
    @Query("DELETE FROM releve_entries WHERE personnelId IN (:personnelIds)")
    suspend fun deleteByPersonnelIds(personnelIds: List<String>)

    /** T-492/T-494: backfill the denormalized name from the personnel table (pulled rows carry only the id). */
    @Query("UPDATE releve_entries SET personnelName = COALESCE((SELECT firstName || ' ' || lastName FROM personnel WHERE personnel.id = releve_entries.personnelId), personnelName) WHERE personnelName = ''")
    suspend fun backfillPersonnelNames()

    /** T-494 test/eject support: direct row access. */
    @Query("SELECT * FROM releve_entries WHERE id = :id")
    suspend fun getById(id: String): ReleveEntryEntity?

    /** T-494 test support. */
    @Query("SELECT COUNT(*) FROM releve_entries")
    suspend fun countAll(): Int
}

@Dao
interface WorkflowRunDao {
    @Query("SELECT * FROM workflow_runs ORDER BY startedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<WorkflowRunEntity>>

    @Query("SELECT * FROM workflow_runs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkflowRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: WorkflowRunEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<WorkflowRunEntity>)
}
/**
 * T-102 chat v2 (133rd session) — the chat READ cache. Chat is
 * online-authoritative; these queries serve the offline/cold-start path.
 * Replace-style writes keep the cache honest (the fresh server pull IS the
 * caller's full channel set; RLS scopes it to the signed-in user).
 */
@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_channels ORDER BY lastMessageAt IS NULL, lastMessageAt DESC")
    suspend fun channels(): List<ChatChannelEntity>

    @Query("SELECT * FROM chat_messages WHERE channelId = :channelId ORDER BY sentAt ASC LIMIT :limit")
    suspend fun messages(channelId: String, limit: Int = 200): List<ChatMessageEntity>

    @Query("SELECT * FROM chat_messages")
    suspend fun allMessages(): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChannels(rows: List<ChatChannelEntity>)

    @Query("DELETE FROM chat_channels")
    suspend fun clearChannels()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(rows: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE channelId = :channelId")
    suspend fun clearMessages(channelId: String)

    @Query("DELETE FROM chat_messages")
    suspend fun clearAllMessages()
}
