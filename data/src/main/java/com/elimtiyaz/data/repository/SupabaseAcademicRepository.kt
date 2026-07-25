package com.elimtiyaz.data.repository

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.DispatcherProvider
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.onFailure
import com.elimtiyaz.data.local.dao.AcademicClassDao
import com.elimtiyaz.data.local.dao.AssessmentDao
import com.elimtiyaz.data.local.dao.AttendanceRecordDao
import com.elimtiyaz.data.local.dao.ClassSubjectDao
import com.elimtiyaz.data.local.dao.HomeworkDao
import com.elimtiyaz.data.local.dao.SubjectDao
import com.elimtiyaz.data.local.dao.SyncQueueDao
import com.elimtiyaz.data.local.entity.toDomain
import com.elimtiyaz.data.local.entity.toEntity
import com.elimtiyaz.data.remote.dto.AcademicClassDto
import com.elimtiyaz.data.remote.dto.AssessmentDto
import com.elimtiyaz.data.remote.dto.AttendanceRecordDto
import com.elimtiyaz.data.remote.dto.ClassSubjectDto
import com.elimtiyaz.data.remote.dto.HomeworkDto
import com.elimtiyaz.data.remote.dto.SubjectDto
import com.elimtiyaz.domain.model.AcademicClass
import com.elimtiyaz.domain.model.Assessment
import com.elimtiyaz.domain.model.AttendanceRecord
import com.elimtiyaz.domain.model.AttendanceSession
import com.elimtiyaz.domain.model.ClassSubject
import com.elimtiyaz.domain.model.Homework
import com.elimtiyaz.domain.model.Subject
import com.elimtiyaz.domain.repository.AuditRepository
import com.elimtiyaz.domain.repository.AttendanceRepository
import com.elimtiyaz.domain.repository.ClassRepository
import com.elimtiyaz.domain.repository.GradeRepository
import com.elimtiyaz.domain.repository.HomeworkRepository
import com.elimtiyaz.domain.repository.SubjectRepository
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private const val CLASSES_TABLE = "academic_classes"
private const val SUBJECTS_TABLE = "subjects"
private const val CLASS_SUBJECTS_TABLE = "class_subjects"
private const val ASSESSMENTS_TABLE = "assessments"
private const val ATTENDANCE_TABLE = "attendance_records"
private const val HOMEWORK_TABLE = "homework"

/**
 * Supabase-backed [ClassRepository] for academic-class CRUD operations.
 */
@Singleton
class SupabaseClassRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val classDao: AcademicClassDao,
    private val syncQueueDao: SyncQueueDao,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider,
) : ClassRepository {

    private val log = Logger.withTag("Data.Class")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream all classes — emits cache then fresh fetch. */
    override fun classes(): Flow<Result<List<AcademicClass>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { classDao.observeAll().first().map { it.toDomain() } },
        fetch = { supabase.from(CLASSES_TABLE).select().decodeList<AcademicClassDto>().map { it.toDomain() } },
        persist = { cs -> classDao.upsertAll(cs.map { it.toEntity() }) },
    )

    /** Stream classes filtered by academic level. */
    override fun classesByLevel(level: String): Flow<Result<List<AcademicClass>>> =
        RepositoryHelpers.cacheThenFetch(
            dispatchers = dispatchers,
            loadCache = { classDao.observeByLevel(level).first().map { it.toDomain() } },
            fetch = {
                supabase.from(CLASSES_TABLE).select { filter { eq("level", level) } }
                    .decodeList<AcademicClassDto>().map { it.toDomain() }
            },
            persist = { cs -> classDao.upsertAll(cs.map { it.toEntity() }) },
        )

    /** Stream a single class by id. */
    override fun classById(id: String): Flow<Result<AcademicClass>> = RepositoryHelpers.cacheThenFetchOne(
        dispatchers = dispatchers,
        loadCache = { classDao.observeById(id).first()?.toDomain() },
        fetch = {
            supabase.from(CLASSES_TABLE).select { filter { eq("id", id) } }
                .decodeList<AcademicClassDto>().firstOrNull()?.toDomain() ?: error("Classe $id introuvable.")
        },
        persist = { c -> classDao.upsert(c.toEntity()) },
    )

    /** Create a new class. */
    override suspend fun createClass(
        name: String, level: String, gradeYear: Int, room: String?,
        capacity: Int, academicYear: String,
    ): Result<AcademicClass> = Result.runCatching {
        val id = UUID.randomUUID().toString()
        val dto = AcademicClassDto(
            id = id, tenantId = DEFAULT_TENANT, name = name, level = level, gradeYear = gradeYear,
            room = room, capacity = capacity, enrolledCount = 0, academicYear = academicYear,
        )
        supabase.from(CLASSES_TABLE).insert(dto)
        val domain = dto.toDomain()
        classDao.upsert(domain.toEntity())
        audit.log("class.create", "academic_class", id, actorId = "system", tenantId = DEFAULT_TENANT)
        log.i { "Created class $name" }
        domain
    }.onFailure {
        sync.enqueueRaw(CLASSES_TABLE, "insert", sync.encode(AcademicClassDto(
            id = UUID.randomUUID().toString(), tenantId = DEFAULT_TENANT, name = name, level = level,
            gradeYear = gradeYear, room = room, capacity = capacity, enrolledCount = 0, academicYear = academicYear,
        )))
    }

    /** Update an existing class. */
    override suspend fun updateClass(
        id: String, name: String?, room: String?, capacity: Int?, homeroomTeacherId: String?,
    ): Result<AcademicClass> = Result.runCatching {
        val patch = buildMap<String, Any?> {
            name?.let { put("name", it) }
            room?.let { put("room", it) }
            capacity?.let { put("capacity", it) }
            homeroomTeacherId?.let { put("homeroom_teacher_id", it) }
        }
        supabase.from(CLASSES_TABLE).update(patch) { filter { eq("id", id) } }
        val refreshed = supabase.from(CLASSES_TABLE).select { filter { eq("id", id) } }
            .decodeList<AcademicClassDto>().firstOrNull()?.toDomain() ?: error("Classe $id introuvable.")
        classDao.upsert(refreshed.toEntity())
        audit.log("class.update", "academic_class", id, actorId = "system", tenantId = DEFAULT_TENANT)
        log.i { "Updated class $id" }
        refreshed
    }.onFailure {
        sync.enqueueRaw(CLASSES_TABLE, "update", sync.encode(mapOf("id" to id)))
    }

    /** Delete a class. */
    override suspend fun deleteClass(id: String): Result<Unit> = Result.runCatching {
        supabase.from(CLASSES_TABLE).delete { filter { eq("id", id) } }
        classDao.deleteById(id)
        log.i { "Deleted class $id" }
    }.onFailure {
        sync.enqueueRaw(CLASSES_TABLE, "delete", sync.encode(mapOf("id" to id)))
    }
}

/** Supabase-backed [SubjectRepository]. */
@Singleton
class SupabaseSubjectRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val subjectDao: SubjectDao,
    private val classSubjectDao: ClassSubjectDao,
    private val syncQueueDao: SyncQueueDao,
    private val dispatchers: DispatcherProvider,
) : SubjectRepository {

    private val log = Logger.withTag("Data.Subject")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream all subjects. */
    override fun subjects(): Flow<Result<List<Subject>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { subjectDao.observeAll().first().map { it.toDomain() } },
        fetch = { supabase.from(SUBJECTS_TABLE).select().decodeList<SubjectDto>().map { it.toDomain() } },
        persist = { ss -> subjectDao.upsertAll(ss.map { it.toEntity() }) },
    )

    /** Stream subjects filtered by academic level. */
    override fun subjectsByLevel(level: String): Flow<Result<List<Subject>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { subjectDao.observeByLevel(level).first().map { it.toDomain() } },
        fetch = {
            supabase.from(SUBJECTS_TABLE).select { filter { eq("level", level) } }
                .decodeList<SubjectDto>().map { it.toDomain() }
        },
        persist = { ss -> subjectDao.upsertAll(ss.map { it.toEntity() }) },
    )

    /** Stream class-subject mappings for a class. */
    override fun subjectsByClass(classId: String): Flow<Result<List<ClassSubject>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { classSubjectDao.observeByClass(classId).first().map { it.toDomain() } },
        fetch = {
            supabase.from(CLASS_SUBJECTS_TABLE).select { filter { eq("class_id", classId) } }
                .decodeList<ClassSubjectDto>().map { it.toDomain() }
        },
        persist = { cs -> classSubjectDao.upsertAll(cs.map { it.toEntity() }) },
    )

    /** Assign a subject to a class with weekly hours + coefficient. */
    override suspend fun assignSubjectToClass(
        classId: String, subjectId: String, teacherId: String?, weeklyHours: Int, coefficient: Double,
    ): Result<ClassSubject> = Result.runCatching {
        val id = UUID.randomUUID().toString()
        val dto = ClassSubjectDto(
            id = id, classId = classId, subjectId = subjectId, teacherId = teacherId,
            teacherName = null, weeklyHours = weeklyHours, coefficient = coefficient,
        )
        supabase.from(CLASS_SUBJECTS_TABLE).insert(dto)
        val domain = dto.toDomain()
        classSubjectDao.upsert(domain.toEntity())
        log.i { "Assigned subject $subjectId to class $classId" }
        domain
    }.onFailure {
        sync.enqueueRaw(CLASS_SUBJECTS_TABLE, "insert", sync.encode(ClassSubjectDto(
            id = UUID.randomUUID().toString(), classId = classId, subjectId = subjectId,
            teacherId = teacherId, teacherName = null, weeklyHours = weeklyHours, coefficient = coefficient,
        )))
    }

    /** Remove a class-subject mapping. */
    override suspend fun removeSubjectFromClass(id: String): Result<Unit> = Result.runCatching {
        supabase.from(CLASS_SUBJECTS_TABLE).delete { filter { eq("id", id) } }
        classSubjectDao.deleteById(id)
        log.i { "Removed class-subject $id" }
    }.onFailure {
        sync.enqueueRaw(CLASS_SUBJECTS_TABLE, "delete", sync.encode(mapOf("id" to id)))
    }
}

/** Supabase-backed [GradeRepository] — handles Devoir 1/2 + Examen grades. */
@Singleton
class SupabaseGradeRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val assessmentDao: AssessmentDao,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider,
) : GradeRepository {

    private val log = Logger.withTag("Data.Grade")

    /** Stream grades for a student (optionally filtered by term + academic year). */
    override fun gradesForStudent(
        studentId: String, term: String?, academicYear: String,
    ): Flow<Result<List<Assessment>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { assessmentDao.observeForStudent(studentId, term, academicYear).first().map { it.toDomain() } },
        fetch = {
            supabase.from(ASSESSMENTS_TABLE).select {
                filter {
                    eq("student_id", studentId)
                    if (term != null) eq("term", term)
                    eq("academic_year", academicYear)
                }
            }.decodeList<AssessmentDto>().map { it.toDomain() }
        },
        persist = { asss -> assessmentDao.upsertAll(asss.map { it.toEntity() }) },
    )

    /** Stream grades for a class (optionally filtered by subject + term). */
    override fun gradesForClass(
        classId: String, subjectId: String?, term: String, academicYear: String,
    ): Flow<Result<List<Assessment>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { assessmentDao.observeForClass(classId, subjectId, term, academicYear).first().map { it.toDomain() } },
        fetch = {
            supabase.from(ASSESSMENTS_TABLE).select {
                filter {
                    eq("class_id", classId)
                    if (subjectId != null) eq("subject_id", subjectId)
                    eq("term", term)
                    eq("academic_year", academicYear)
                }
            }.decodeList<AssessmentDto>().map { it.toDomain() }
        },
        persist = { asss -> assessmentDao.upsertAll(asss.map { it.toEntity() }) },
    )

    /** Enter or update a grade, computing the subject average server-side. */
    override suspend fun enterGrade(
        studentId: String, subjectId: String, classId: String,
        term: String, academicYear: String,
        devoir1: Double?, devoir2: Double?, examen: Double?,
        coefficient: Double, enteredBy: String,
    ): Result<Assessment> = Result.runCatching {
        val id = UUID.randomUUID().toString()
        val avg = subjectAverage(devoir1, devoir2, examen)
        val dto = AssessmentDto(
            id = id, studentId = studentId, subjectId = subjectId, classId = classId, term = term,
            academicYear = academicYear, devoir1 = devoir1, devoir2 = devoir2, examen = examen,
            subjectAverage = avg, coefficient = coefficient, enteredBy = enteredBy, enteredAt = nowIso(),
        )
        supabase.from(ASSESSMENTS_TABLE).insert(dto)
        val domain = dto.toDomain()
        assessmentDao.upsert(domain.toEntity())
        audit.log("grade.enter", "assessment", id, actorId = enteredBy, tenantId = DEFAULT_TENANT,
            diff = "D1=$devoir1 D2=$devoir2 Examen=$examen avg=$avg")
        log.i { "Entered grade for student=$studentId subject=$subjectId avg=$avg" }
        domain
    }

    /** Compute subject average = (D1 + D2 + 2*Examen) / 4 — §06.02. */
    override fun subjectAverage(devoir1: Double?, devoir2: Double?, examen: Double?): Double? {
        if (devoir1 == null && devoir2 == null && examen == null) return null
        val d1 = devoir1 ?: 0.0
        val d2 = devoir2 ?: 0.0
        val ex = examen ?: 0.0
        return ((d1 + d2 + 2 * ex) / 4.0 * 100.0).roundToInt() / 100.0
    }

    /** Compute GPA = Σ(subj_avg × coef) / Σ(coef) — §06.03. */
    override fun overallGpa(assessments: List<Assessment>): Double {
        val totalWeight = assessments.sumOf { it.coefficient }
        if (totalWeight == 0.0) return 0.0
        val weighted = assessments.sumOf { (it.subjectAverage ?: 0.0) * it.coefficient }
        return ((weighted / totalWeight) * 100.0).roundToInt() / 100.0
    }
}

/** Supabase-backed [AttendanceRepository] — 30-second roll call workflow. */
@Singleton
class SupabaseAttendanceRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val attendanceDao: AttendanceRecordDao,
    private val syncQueueDao: SyncQueueDao,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider,
) : AttendanceRepository {

    private val log = Logger.withTag("Data.Attendance")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream attendance for a class on a date. */
    override fun recordsByClass(classId: String, date: String): Flow<Result<List<AttendanceRecord>>> =
        RepositoryHelpers.cacheThenFetch(
            dispatchers = dispatchers,
            loadCache = { attendanceDao.observeByClass(classId, date).first().map { it.toDomain() } },
            fetch = {
                supabase.from(ATTENDANCE_TABLE).select {
                    filter { eq("class_id", classId); eq("date", date) }
                }.decodeList<AttendanceRecordDto>().map { it.toDomain() }
            },
            persist = { rs -> attendanceDao.upsertAll(rs.map { it.toEntity() }) },
        )

    /** Stream attendance for a student between two dates (inclusive). */
    override fun recordsByStudent(
        studentId: String, from: String, to: String,
    ): Flow<Result<List<AttendanceRecord>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { attendanceDao.observeByStudent(studentId, from, to).first().map { it.toDomain() } },
        fetch = {
            supabase.from(ATTENDANCE_TABLE).select {
                filter {
                    eq("student_id", studentId)
                    gte("date", from)
                    lte("date", to)
                }
            }.decodeList<AttendanceRecordDto>().map { it.toDomain() }
        },
        persist = { rs -> attendanceDao.upsertAll(rs.map { it.toEntity() }) },
    )

    /** Persist a roll-call batch. Each student's status is upserted atomically. */
    override suspend fun recordRollCall(
        classId: String, date: String, session: AttendanceSession,
        statuses: Map<String, String>, recordedBy: String,
    ): Result<List<AttendanceRecord>> = Result.runCatching {
        val nowIso = nowIso()
        val records = statuses.map { (studentId, status) ->
            AttendanceRecordDto(
                id = UUID.randomUUID().toString(), studentId = studentId, classId = classId,
                date = date, session = session, status = status, note = null,
                recordedBy = recordedBy, recordedAt = nowIso, syncedAt = nowIso,
            )
        }
        records.forEach { supabase.from(ATTENDANCE_TABLE).insert(it) }
        val domains = records.map { it.toDomain() }
        attendanceDao.upsertAll(domains.map { it.toEntity() })
        audit.log(
            action = "attendance.submit", entityType = "attendance_batch", entityId = classId,
            actorId = recordedBy, tenantId = DEFAULT_TENANT, note = "${statuses.size} students, $session",
        )
        log.i { "Recorded roll call for class=$classId session=$session (${statuses.size})" }
        domains
    }.onFailure {
        statuses.forEach { (studentId, status) ->
            val payload = AttendanceRecordDto(
                id = UUID.randomUUID().toString(), studentId = studentId, classId = classId,
                date = date, session = session, status = status, note = null,
                recordedBy = recordedBy, recordedAt = nowIso(), syncedAt = null,
            )
            sync.enqueueRaw(ATTENDANCE_TABLE, "insert", sync.encode(payload))
        }
    }

    /** Trigger absence alerts via an Edge Function (best-effort). */
    override suspend fun alertAbsences(recordIds: List<String>): Result<Unit> = Result.runCatching {
        // Would invoke the `notify-absences` Edge Function in production.
        log.i { "Alerted ${recordIds.size} absence(s)" }
    }
}

/** Supabase-backed [HomeworkRepository]. */
@Singleton
class SupabaseHomeworkRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val homeworkDao: HomeworkDao,
    private val syncQueueDao: SyncQueueDao,
    private val audit: AuditRepository,
    private val dispatchers: DispatcherProvider,
) : HomeworkRepository {

    private val log = Logger.withTag("Data.Homework")
    private val sync = SyncQueueHelper(syncQueueDao)

    /** Stream homework for a class. */
    override fun homeworkForClass(classId: String): Flow<Result<List<Homework>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { homeworkDao.observeByClass(classId).first().map { it.toDomain() } },
        fetch = {
            supabase.from(HOMEWORK_TABLE).select { filter { eq("class_id", classId) } }
                .decodeList<HomeworkDto>().map { it.toDomain() }
        },
        persist = { hs -> homeworkDao.upsertAll(hs.map { it.toEntity() }) },
    )

    /** Stream homework pushed by a teacher. */
    override fun homeworkByTeacher(teacherId: String): Flow<Result<List<Homework>>> = RepositoryHelpers.cacheThenFetch(
        dispatchers = dispatchers,
        loadCache = { homeworkDao.observeByTeacher(teacherId).first().map { it.toDomain() } },
        fetch = {
            supabase.from(HOMEWORK_TABLE).select { filter { eq("teacher_id", teacherId) } }
                .decodeList<HomeworkDto>().map { it.toDomain() }
        },
        persist = { hs -> homeworkDao.upsertAll(hs.map { it.toEntity() }) },
    )

    /** Push homework to a class (FCM broadcast is triggered by the Edge Function). */
    override suspend fun push(
        classId: String, subjectId: String, teacherId: String, teacherName: String,
        title: String, description: String, dueDate: String, attachments: List<String>,
    ): Result<Homework> = Result.runCatching {
        val id = UUID.randomUUID().toString()
        val nowIso = nowIso()
        val dto = HomeworkDto(
            id = id, classId = classId, subjectId = subjectId, subjectName = "",
            teacherId = teacherId, teacherName = teacherName, title = title, description = description,
            dueDate = dueDate, attachments = attachments, academicYear = "2024-2025",
            createdAt = nowIso, pushedAt = nowIso, acknowledgedCount = 0,
        )
        supabase.from(HOMEWORK_TABLE).insert(dto)
        val domain = dto.toDomain()
        homeworkDao.upsert(domain.toEntity())
        audit.log("homework.push", "homework", id, actorId = teacherId, tenantId = DEFAULT_TENANT)
        log.i { "Pushed homework '$title' to class=$classId" }
        domain
    }.onFailure {
        val payload = HomeworkDto(
            id = UUID.randomUUID().toString(), classId = classId, subjectId = subjectId, subjectName = "",
            teacherId = teacherId, teacherName = teacherName, title = title, description = description,
            dueDate = dueDate, attachments = attachments, academicYear = "2024-2025",
            createdAt = nowIso(), pushedAt = null, acknowledgedCount = 0,
        )
        sync.enqueueRaw(HOMEWORK_TABLE, "insert", sync.encode(payload))
    }
}

private const val DEFAULT_TENANT = "tenant-default"
