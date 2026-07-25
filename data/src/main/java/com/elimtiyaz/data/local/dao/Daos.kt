package com.elimtiyaz.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.elimtiyaz.data.local.entity.AcademicClassEntity
import com.elimtiyaz.data.local.entity.AppNotificationEntity
import com.elimtiyaz.data.local.entity.AssessmentEntity
import com.elimtiyaz.data.local.entity.AttendanceRecordEntity
import com.elimtiyaz.data.local.entity.AuditEntryEntity
import com.elimtiyaz.data.local.entity.ClassSubjectEntity
import com.elimtiyaz.data.local.entity.ExpenseEntity
import com.elimtiyaz.data.local.entity.HomeworkEntity
import com.elimtiyaz.data.local.entity.InstallmentEntity
import com.elimtiyaz.data.local.entity.ParentEntity
import com.elimtiyaz.data.local.entity.PaymentEntity
import com.elimtiyaz.data.local.entity.PersonnelEntity
import com.elimtiyaz.data.local.entity.ReleveEntryEntity
import com.elimtiyaz.data.local.entity.RoutingStopEntity
import com.elimtiyaz.data.local.entity.StudentEntity
import com.elimtiyaz.data.local.entity.SubjectEntity
import com.elimtiyaz.data.local.entity.SyncQueueEntity
import com.elimtiyaz.data.local.entity.TripLogEntity
import com.elimtiyaz.data.local.entity.VehicleEntity
import kotlinx.coroutines.flow.Flow

/**
 * Common contract for all DAOs. Concrete DAOs extend this so they all share
 * the same insert/delete semantics: insert with `REPLACE` (acts as upsert) and
 * a typed `@Delete`. Per master plan §13.05 there is **no** export method on
 * any DAO — local backups are prohibited.
 */
interface BaseDao<T> {
    /** Upsert a row by primary key (REPLACE semantics). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: T)

    /** Upsert many rows in a single transaction. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<T>)

    /** Delete a single row. */
    @Delete
    suspend fun delete(item: T)
}

/** DAO for [ParentEntity]. */
@Dao
interface ParentDao : BaseDao<ParentEntity> {
    /** Stream all parents ordered by createdAt desc. */
    @Query("SELECT * FROM parents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ParentEntity>>

    /** Stream a single parent by id. */
    @Query("SELECT * FROM parents WHERE id = :id")
    fun observeById(id: String): Flow<ParentEntity?>

    /** Search parents whose code or name contains the query (case-insensitive). */
    @Query("SELECT * FROM parents WHERE firstName LIKE '%' || :q || '%' OR lastName LIKE '%' || :q || '%' OR code LIKE '%' || :q || '%' OR phone LIKE '%' || :q || '%'")
    fun search(q: String): Flow<List<ParentEntity>>

    /** Delete a parent by id. */
    @Query("DELETE FROM parents WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Return all parents as a one-shot snapshot. */
    @Query("SELECT * FROM parents")
    suspend fun all(): List<ParentEntity>
}

/** DAO for [StudentEntity]. */
@Dao
interface StudentDao : BaseDao<StudentEntity> {
    /** Stream all students. */
    @Query("SELECT * FROM students ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<StudentEntity>>

    /** Stream students for a parent. */
    @Query("SELECT * FROM students WHERE parentId = :parentId")
    fun observeByParent(parentId: String): Flow<List<StudentEntity>>

    /** Stream students in a class. */
    @Query("SELECT * FROM students WHERE classId = :classId")
    fun observeByClass(classId: String): Flow<List<StudentEntity>>

    /** Stream a single student. */
    @Query("SELECT * FROM students WHERE id = :id")
    fun observeById(id: String): Flow<StudentEntity?>

    /** Search students by code/name. */
    @Query("SELECT * FROM students WHERE firstName LIKE '%' || :q || '%' OR lastName LIKE '%' || :q || '%' OR code LIKE '%' || :q || '%'")
    fun search(q: String): Flow<List<StudentEntity>>

    /** Delete a student by id. */
    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Return all students as a one-shot snapshot. */
    @Query("SELECT * FROM students")
    suspend fun all(): List<StudentEntity>
}

/** DAO for [AcademicClassEntity]. */
@Dao
interface AcademicClassDao : BaseDao<AcademicClassEntity> {
    /** Stream all classes. */
    @Query("SELECT * FROM academic_classes ORDER BY name")
    fun observeAll(): Flow<List<AcademicClassEntity>>

    /** Stream classes for a level. */
    @Query("SELECT * FROM academic_classes WHERE level = :level ORDER BY name")
    fun observeByLevel(level: String): Flow<List<AcademicClassEntity>>

    /** Stream a single class. */
    @Query("SELECT * FROM academic_classes WHERE id = :id")
    fun observeById(id: String): Flow<AcademicClassEntity?>

    /** Delete a class. */
    @Query("DELETE FROM academic_classes WHERE id = :id")
    suspend fun deleteById(id: String)
}

/** DAO for [SubjectEntity]. */
@Dao
interface SubjectDao : BaseDao<SubjectEntity> {
    /** Stream all subjects. */
    @Query("SELECT * FROM subjects ORDER BY code")
    fun observeAll(): Flow<List<SubjectEntity>>

    /** Stream subjects for a level. */
    @Query("SELECT * FROM subjects WHERE level = :level")
    fun observeByLevel(level: String): Flow<List<SubjectEntity>>
}

/** DAO for [ClassSubjectEntity]. */
@Dao
interface ClassSubjectDao : BaseDao<ClassSubjectEntity> {
    /** Stream class-subject mappings for a class. */
    @Query("SELECT * FROM class_subjects WHERE classId = :classId")
    fun observeByClass(classId: String): Flow<List<ClassSubjectEntity>>

    /** Delete a class-subject row by id. */
    @Query("DELETE FROM class_subjects WHERE id = :id")
    suspend fun deleteById(id: String)
}

/** DAO for [AssessmentEntity]. */
@Dao
interface AssessmentDao : BaseDao<AssessmentEntity> {
    /** Stream grades for a student (optionally filtered by term and academic year). */
    @Query("SELECT * FROM assessments WHERE studentId = :studentId AND (:term IS NULL OR term = :term) AND academicYear = :academicYear")
    fun observeForStudent(studentId: String, term: String?, academicYear: String): Flow<List<AssessmentEntity>>

    /** Stream grades for a class (optionally filtered by subject and term). */
    @Query("SELECT * FROM assessments WHERE classId = :classId AND (:subjectId IS NULL OR subjectId = :subjectId) AND term = :term AND academicYear = :academicYear")
    fun observeForClass(classId: String, subjectId: String?, term: String, academicYear: String): Flow<List<AssessmentEntity>>
}

/** DAO for [AttendanceRecordEntity]. */
@Dao
interface AttendanceRecordDao : BaseDao<AttendanceRecordEntity> {
    /** Stream attendance for a class on a date. */
    @Query("SELECT * FROM attendance_records WHERE classId = :classId AND date = :date")
    fun observeByClass(classId: String, date: String): Flow<List<AttendanceRecordEntity>>

    /** Stream attendance for a student between two dates (inclusive). */
    @Query("SELECT * FROM attendance_records WHERE studentId = :studentId AND date >= :from AND date <= :to")
    fun observeByStudent(studentId: String, from: String, to: String): Flow<List<AttendanceRecordEntity>>
}

/** DAO for [HomeworkEntity]. */
@Dao
interface HomeworkDao : BaseDao<HomeworkEntity> {
    /** Stream homework for a class. */
    @Query("SELECT * FROM homework WHERE classId = :classId ORDER BY createdAt DESC")
    fun observeByClass(classId: String): Flow<List<HomeworkEntity>>

    /** Stream homework pushed by a teacher. */
    @Query("SELECT * FROM homework WHERE teacherId = :teacherId ORDER BY createdAt DESC")
    fun observeByTeacher(teacherId: String): Flow<List<HomeworkEntity>>
}

/** DAO for [PaymentEntity]. */
@Dao
interface PaymentDao : BaseDao<PaymentEntity> {
    /** Stream all payments. */
    @Query("SELECT * FROM payments ORDER BY collectedAt DESC")
    fun observeAll(): Flow<List<PaymentEntity>>

    /** Stream payments for a parent. */
    @Query("SELECT * FROM payments WHERE parentId = :parentId ORDER BY collectedAt DESC")
    fun observeByParent(parentId: String): Flow<List<PaymentEntity>>

    /** Stream payments for a student. */
    @Query("SELECT * FROM payments WHERE studentId = :studentId ORDER BY collectedAt DESC")
    fun observeByStudent(studentId: String): Flow<List<PaymentEntity>>

    /** Stream a single payment. */
    @Query("SELECT * FROM payments WHERE id = :id")
    fun observeById(id: String): Flow<PaymentEntity?>

    /** Snapshot all payments. */
    @Query("SELECT * FROM payments")
    suspend fun all(): List<PaymentEntity>
}

/** DAO for [InstallmentEntity]. */
@Dao
interface InstallmentDao : BaseDao<InstallmentEntity> {
    /** Stream installments for a parent. */
    @Query("SELECT * FROM installments WHERE parentId = :parentId ORDER BY dueDate")
    fun observeByParent(parentId: String): Flow<List<InstallmentEntity>>

    /** Stream installments for a student. */
    @Query("SELECT * FROM installments WHERE studentId = :studentId ORDER BY dueDate")
    fun observeByStudent(studentId: String): Flow<List<InstallmentEntity>>
}

/** DAO for [ExpenseEntity]. */
@Dao
interface ExpenseDao : BaseDao<ExpenseEntity> {
    /** Stream all expenses. */
    @Query("SELECT * FROM expenses ORDER BY submittedAt DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    /** Stream expenses by status. */
    @Query("SELECT * FROM expenses WHERE status = :status ORDER BY submittedAt DESC")
    fun observeByStatus(status: String): Flow<List<ExpenseEntity>>

    /** Stream a single expense. */
    @Query("SELECT * FROM expenses WHERE id = :id")
    fun observeById(id: String): Flow<ExpenseEntity?>
}

/** DAO for [PersonnelEntity]. */
@Dao
interface PersonnelDao : BaseDao<PersonnelEntity> {
    /** Stream all personnel. */
    @Query("SELECT * FROM personnel ORDER BY lastName")
    fun observeAll(): Flow<List<PersonnelEntity>>

    /** Stream personnel by staff category. */
    @Query("SELECT * FROM personnel WHERE staffCategory = :category ORDER BY lastName")
    fun observeByCategory(category: String): Flow<List<PersonnelEntity>>

    /** Stream a single person. */
    @Query("SELECT * FROM personnel WHERE id = :id")
    fun observeById(id: String): Flow<PersonnelEntity?>

    /** Delete a person by id. */
    @Query("DELETE FROM personnel WHERE id = :id")
    suspend fun deleteById(id: String)
}

/** DAO for [ReleveEntryEntity]. */
@Dao
interface ReleveDao : BaseDao<ReleveEntryEntity> {
    /** Stream releve entries for a person between two dates (inclusive). */
    @Query("SELECT * FROM releve_entries WHERE personnelId = :personnelId AND date >= :from AND date <= :to ORDER BY date DESC")
    fun observeByPersonnel(personnelId: String, from: String, to: String): Flow<List<ReleveEntryEntity>>
}

/** DAO for [AuditEntryEntity]. */
@Dao
interface AuditDao : BaseDao<AuditEntryEntity> {
    /** Stream recent audit entries (most recent first). */
    @Query("SELECT * FROM audit_log ORDER BY at DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditEntryEntity>>

    /** Stream audit entries for an entity. */
    @Query("SELECT * FROM audit_log WHERE entityType = :entityType AND entityId = :entityId ORDER BY at DESC")
    fun observeByEntity(entityType: String, entityId: String): Flow<List<AuditEntryEntity>>
}

/** DAO for [AppNotificationEntity]. */
@Dao
interface NotificationDao : BaseDao<AppNotificationEntity> {
    /** Stream all notifications (most recent first). */
    @Query("SELECT * FROM notifications ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AppNotificationEntity>>
}

/** DAO for [RoutingStopEntity]. */
@Dao
interface RoutingStopDao : BaseDao<RoutingStopEntity> {
    /** Stream all stops ordered by their position in route. */
    @Query("SELECT * FROM routing_stops ORDER BY shift, orderInRoute")
    fun observeAll(): Flow<List<RoutingStopEntity>>
}

/** DAO for [VehicleEntity]. */
@Dao
interface VehicleDao : BaseDao<VehicleEntity> {
    /** Stream all vehicles. */
    @Query("SELECT * FROM vehicles ORDER BY plate")
    fun observeAll(): Flow<List<VehicleEntity>>
}

/** DAO for [TripLogEntity]. */
@Dao
interface TripLogDao : BaseDao<TripLogEntity> {
    /** Stream all trip logs (most recent first). */
    @Query("SELECT * FROM trip_logs ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<TripLogEntity>>
}

/**
 * DAO for [SyncQueueEntity] — the offline write queue. Pending rows are
 * replayed by [com.elimtiyaz.data.sync.SyncQueueWorker] when network returns.
 */
@Dao
interface SyncQueueDao : BaseDao<SyncQueueEntity> {
    /** Stream all pending queue rows ordered by creation time. */
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<SyncQueueEntity>>

    /** Snapshot of pending queue rows (used by the worker). */
    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun pending(): List<SyncQueueEntity>

    /** Delete a queue row by id. */
    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: String)

    /** Count of pending rows (drives the offline banner). */
    @Query("SELECT COUNT(*) FROM sync_queue")
    fun pendingCount(): Flow<Int>
}
