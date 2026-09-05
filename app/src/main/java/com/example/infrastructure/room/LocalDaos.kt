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

    @Query("SELECT * FROM students WHERE firstName LIKE '%' || :q || '%' OR lastName LIKE '%' || :q || '%' OR code LIKE '%' || :q || '%' ORDER BY lastName ASC LIMIT 500")
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

    /**
     * Vault §04.07 / §06.05 — Student Academic History: EVERY assessment of
     * one student across ALL academic years and terms (the permanent,
     * append-only history embedded in the Student Profile).
     */
    @Query("SELECT * FROM assessments WHERE studentId = :studentId ORDER BY academicYear ASC, term ASC, subjectId ASC")
    fun observeByStudent(studentId: String): Flow<List<AssessmentEntity>>

    /**
     * Vault §05.06 — coefficient edits must trigger an automatic GPA
     * recompute for affected students. Android computes the GPA on read
     * ([com.example.core.computeOverallGpa] over the assessment rows), so
     * refreshing the coefficient snapshot on the CURRENT year's rows IS the
     * recompute. Past years are append-only and are never touched.
     *
     * Vault §06.02 (iteration 2) — the SUBJECT-level coefficient snapshot on
     * each assessment row is updated by this query. The per-COMPONENT
     * coefficient snapshot (coefficientDevoir1/2/Examen) is recomputed
     * separately by the repository because it must also re-derive
     * subjectAverage inline (Room can't express that in a single UPDATE).
     */
    @Query("UPDATE assessments SET coefficient = :coefficient WHERE subjectId = :subjectId AND academicYear = :academicYear")
    suspend fun updateCoefficientForSubjectYear(subjectId: String, coefficient: Double, academicYear: String)

    /**
     * Vault §04.07 / §06.05 + §06.02 (iteration 2) — list every assessment
     * row for one subject in one academic year. Used by the repository to
     * re-snapshot the per-COMPONENT coefficients (D1/D2/Examen) and
     * re-derive subjectAverage when an admin edits those coefficients on
     * the subject. Past years are NOT touched (append-only rule).
     */
    @Query("SELECT * FROM assessments WHERE subjectId = :subjectId AND academicYear = :academicYear")
    suspend fun listBySubjectAndYear(subjectId: String, academicYear: String): List<AssessmentEntity>

    @Query("SELECT COUNT(*) FROM assessments")
    suspend fun count(): Int

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

    @Query("SELECT * FROM homework WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HomeworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: HomeworkEntity)

    // T-039 / HOMEWORK-103: batch upsert for the pull path (single Room
    // round-trip instead of O(N)).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<HomeworkEntity>)

    /**
     * T-039 / HOMEWORK-103 + T-024: legacy local rows created before the
     * bare-UUID fix carry ids like "hwk-{uuid}"; the same logical row pulled
     * from the server has the bare uuid. Delete the legacy copy so the pull
     * does not leave a duplicate.
     */
    @Query("DELETE FROM homework WHERE id = 'hwk-' || :serverId")
    suspend fun deleteLegacyPrefixedCopy(serverId: String)
}

// ─── Payment DAO ─────────────────────────────────────────────────────────────

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments ORDER BY collectedAt DESC LIMIT 500")
    fun observeAll(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY collectedAt DESC LIMIT 500")
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

    // T-039: batch upsert for the pull path (single Room round-trip).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<PaymentEntity>)

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

    @Query("SELECT COUNT(*) FROM expenses WHERE status = 'submitted'")
    fun observePendingCount(): Flow<Int>

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

    @Query("SELECT COUNT(*) FROM personnel WHERE status = 'active'")
    fun observeActiveCount(): Flow<Int>

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

    // T-039: snapshot read for tests + non-flow callers.
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC LIMIT 200")
    suspend fun listAll(): List<NotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<NotificationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: NotificationEntity)

    /**
     * T-039 / NOTIF-105 — evict rows the signed-in user can no longer see.
     * Mirrors the server's `notifications_select` RLS policy (migration
     * 0019) branch-for-branch; a row SURVIVES iff it is visible:
     *   - direct rows targeted to THIS user (targetUserId = :userId), or
     *   - role-broadcasts for ANY role the user holds (:roles — resolved
     *     fresh from current_user_roles()), or
     *   - tenant broadcasts (NULL/NULL) when the user holds one of the
     *     staff trio (0019: super_admin / financial_officer /
     *     support_staff — passed as :canSeeTenantBroadcasts = 1|0).
     */
    @Query(
        "DELETE FROM notifications WHERE NOT (" +
            "(targetUserId IS NOT NULL AND targetUserId = :userId)" +
            " OR (targetRole IS NOT NULL AND targetRole IN (:roles))" +
            " OR (:canSeeTenantBroadcasts = 1 AND targetUserId IS NULL AND targetRole IS NULL)" +
            ")",
    )
    suspend fun evictNotVisibleTo(userId: String, roles: List<String>, canSeeTenantBroadcasts: Int)

    /**
     * T-181 (T-173b / NOTIF-200) — evict rows the SERVER has dismissed since
     * the last pull. The pull layer resolves the stale candidates (local ids
     * absent from the fresh active pull), asks the server which of them now
     * carry `dismissed_at`, and deletes exactly those locally. Pre-T-181
     * such rows lingered forever (the T-172 pull filter only stops NEW
     * dismissed rows from entering the cache). Desktop parity: the desktop
     * repository filters `dismissed_at IS NULL` on EVERY read.
     */
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

    @Query("SELECT * FROM releve_entries ORDER BY date DESC LIMIT 200")
    fun observeAll(): Flow<List<ReleveEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ReleveEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ReleveEntryEntity>)
}

@Dao
interface WorkflowRunDao {
    @Query("SELECT * FROM workflow_runs ORDER BY startedAt DESC LIMIT 50")
    fun observeRecent(): Flow<List<WorkflowRunEntity>>

    @Query("SELECT * FROM workflow_runs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): WorkflowRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: WorkflowRunEntity)

    // T-039: batch upsert for the pull path (single Room round-trip).
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<WorkflowRunEntity>)
}