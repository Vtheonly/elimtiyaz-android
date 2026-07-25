package com.elimtiyaz.data.mock

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.model.AcademicClass
import com.elimtiyaz.domain.model.Assessment
import com.elimtiyaz.domain.model.AttendanceRecord
import com.elimtiyaz.domain.model.AttendanceSession
import com.elimtiyaz.domain.model.ClassSubject
import com.elimtiyaz.domain.model.Homework
import com.elimtiyaz.domain.model.Subject
import com.elimtiyaz.domain.repository.AttendanceRepository
import com.elimtiyaz.domain.repository.ClassRepository
import com.elimtiyaz.domain.repository.GradeRepository
import com.elimtiyaz.domain.repository.HomeworkRepository
import com.elimtiyaz.domain.repository.SubjectRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

private fun mockDelay() = delay((200L..500L).random())

/** Mock [ClassRepository]. */
@Singleton
class MockClassRepository @Inject constructor() : ClassRepository {

    private val log = Logger.withTag("Mock.Class")
    private val state = MutableStateFlow(MockData.classes)

    /** Stream all classes. */
    override fun classes(): Flow<Result<List<AcademicClass>>> = state.map { Result.success(it) }

    /** Stream classes filtered by academic level. */
    override fun classesByLevel(level: String): Flow<Result<List<AcademicClass>>> =
        state.map { Result.success(it.filter { c -> c.level == level }) }

    /** Stream a single class. */
    override fun classById(id: String): Flow<Result<AcademicClass>> = state.map { classes ->
        val c = classes.firstOrNull { it.id == id }
            ?: return@map Result.failure("Classe $id introuvable.")
        Result.success(c)
    }

    /** Create a new class. */
    override suspend fun createClass(
        name: String, level: String, gradeYear: Int, room: String?,
        capacity: Int, academicYear: String,
    ): Result<AcademicClass> {
        mockDelay()
        val c = AcademicClass(
            id = "c-new-${UUID.randomUUID().toString().take(6)}",
            tenantId = MockData.TENANT_ID, name = name, level = level, gradeYear = gradeYear,
            room = room, capacity = capacity, enrolledCount = 0, academicYear = academicYear,
        )
        state.value = state.value + c
        log.i { "Created class $name" }
        return Result.success(c)
    }

    /** Update an existing class. */
    override suspend fun updateClass(
        id: String, name: String?, room: String?, capacity: Int?, homeroomTeacherId: String?,
    ): Result<AcademicClass> {
        mockDelay()
        val updated = state.value.map { c ->
            if (c.id != id) c else c.copy(
                name = name ?: c.name, room = room ?: c.room,
                capacity = capacity ?: c.capacity,
                homeroomTeacherId = homeroomTeacherId ?: c.homeroomTeacherId,
            )
        }
        state.value = updated
        val result = updated.firstOrNull { it.id == id }
            ?: return Result.failure("Classe $id introuvable.")
        log.i { "Updated class $id" }
        return Result.success(result)
    }

    /** Delete a class. */
    override suspend fun deleteClass(id: String): Result<Unit> {
        mockDelay()
        state.value = state.value.filterNot { it.id == id }
        log.i { "Deleted class $id" }
        return Result.success(Unit)
    }
}

/** Mock [SubjectRepository]. */
@Singleton
class MockSubjectRepository @Inject constructor() : SubjectRepository {

    private val log = Logger.withTag("Mock.Subject")
    private val subjectState = MutableStateFlow(MockData.subjects)
    private val classSubjectState = MutableStateFlow(MockData.classSubjects)

    /** Stream all subjects. */
    override fun subjects(): Flow<Result<List<Subject>>> = subjectState.map { Result.success(it) }

    /** Stream subjects filtered by academic level. */
    override fun subjectsByLevel(level: String): Flow<Result<List<Subject>>> =
        subjectState.map { Result.success(it.filter { s -> s.level == level }) }

    /** Stream class-subject mappings for a class. */
    override fun subjectsByClass(classId: String): Flow<Result<List<ClassSubject>>> =
        classSubjectState.map { Result.success(it.filter { cs -> cs.classId == classId }) }

    /** Assign a subject to a class. */
    override suspend fun assignSubjectToClass(
        classId: String, subjectId: String, teacherId: String?, weeklyHours: Int, coefficient: Double,
    ): Result<ClassSubject> {
        mockDelay()
        val cs = ClassSubject(
            id = "cs-new-${UUID.randomUUID().toString().take(6)}", classId = classId, subjectId = subjectId,
            teacherId = teacherId, teacherName = null, weeklyHours = weeklyHours, coefficient = coefficient,
        )
        classSubjectState.value = classSubjectState.value + cs
        log.i { "Assigned subject $subjectId to class $classId" }
        return Result.success(cs)
    }

    /** Remove a class-subject mapping. */
    override suspend fun removeSubjectFromClass(id: String): Result<Unit> {
        mockDelay()
        classSubjectState.value = classSubjectState.value.filterNot { it.id == id }
        log.i { "Removed class-subject $id" }
        return Result.success(Unit)
    }
}

/** Mock [GradeRepository]. */
@Singleton
class MockGradeRepository @Inject constructor() : GradeRepository {

    private val log = Logger.withTag("Mock.Grade")
    private val state = MutableStateFlow(MockData.assessments)

    /** Stream grades for a student (optionally filtered by term + academic year). */
    override fun gradesForStudent(
        studentId: String, term: String?, academicYear: String,
    ): Flow<Result<List<Assessment>>> = state.map { asss ->
        Result.success(asss.filter { a ->
            a.studentId == studentId &&
                (term == null || a.term == term) &&
                a.academicYear == academicYear
        })
    }

    /** Stream grades for a class (optionally filtered by subject + term). */
    override fun gradesForClass(
        classId: String, subjectId: String?, term: String, academicYear: String,
    ): Flow<Result<List<Assessment>>> = state.map { asss ->
        Result.success(asss.filter { a ->
            a.classId == classId &&
                (subjectId == null || a.subjectId == subjectId) &&
                a.term == term && a.academicYear == academicYear
        })
    }

    /** Enter or update a grade, computing the subject average. */
    override suspend fun enterGrade(
        studentId: String, subjectId: String, classId: String,
        term: String, academicYear: String,
        devoir1: Double?, devoir2: Double?, examen: Double?,
        coefficient: Double, enteredBy: String,
    ): Result<Assessment> {
        mockDelay()
        val id = "a-new-${UUID.randomUUID().toString().take(6)}"
        val avg = subjectAverage(devoir1, devoir2, examen)
        val assessment = Assessment(
            id = id, studentId = studentId, subjectId = subjectId, classId = classId,
            term = term, academicYear = academicYear, devoir1 = devoir1, devoir2 = devoir2,
            examen = examen, subjectAverage = avg, coefficient = coefficient,
            enteredBy = enteredBy, enteredAt = Clock.System.now().toString(),
        )
        state.value = state.value + assessment
        log.i { "Entered grade avg=$avg for student=$studentId" }
        return Result.success(assessment)
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

/** Mock [AttendanceRepository]. */
@Singleton
class MockAttendanceRepository @Inject constructor() : AttendanceRepository {

    private val log = Logger.withTag("Mock.Attendance")
    private val state = MutableStateFlow(MockData.attendanceRecords)

    /** Stream attendance for a class on a date. */
    override fun recordsByClass(classId: String, date: String): Flow<Result<List<AttendanceRecord>>> =
        state.map { Result.success(it.filter { r -> r.classId == classId && r.date == date }) }

    /** Stream attendance for a student between two dates (inclusive). */
    override fun recordsByStudent(
        studentId: String, from: String, to: String,
    ): Flow<Result<List<AttendanceRecord>>> = state.map { Result.success(it.filter { r ->
        r.studentId == studentId && r.date >= from && r.date <= to
    }) }

    /** Persist a roll-call batch. */
    override suspend fun recordRollCall(
        classId: String, date: String, session: AttendanceSession,
        statuses: Map<String, String>, recordedBy: String,
    ): Result<List<AttendanceRecord>> {
        mockDelay()
        val nowIso = Clock.System.now().toString()
        val records = statuses.map { (studentId, status) ->
            AttendanceRecord(
                id = "att-new-${UUID.randomUUID().toString().take(6)}",
                studentId = studentId, classId = classId, date = date, session = session,
                status = status, note = null, recordedBy = recordedBy,
                recordedAt = nowIso, syncedAt = nowIso,
            )
        }
        // Replace any prior records for the same class+date+session.
        state.value = state.value.filterNot { it.classId == classId && it.date == date && it.session == session } + records
        log.i { "Recorded roll call class=$classId session=$session (${statuses.size})" }
        return Result.success(records)
    }

    /** Mock absence alert — always succeeds. */
    override suspend fun alertAbsences(recordIds: List<String>): Result<Unit> {
        mockDelay()
        log.i { "Alerted ${recordIds.size} absence(s)" }
        return Result.success(Unit)
    }
}

/** Mock [HomeworkRepository]. */
@Singleton
class MockHomeworkRepository @Inject constructor() : HomeworkRepository {

    private val log = Logger.withTag("Mock.Homework")
    private val state = MutableStateFlow(MockData.homework)

    /** Stream homework for a class. */
    override fun homeworkForClass(classId: String): Flow<Result<List<Homework>>> =
        state.map { Result.success(it.filter { h -> h.classId == classId }) }

    /** Stream homework pushed by a teacher. */
    override fun homeworkByTeacher(teacherId: String): Flow<Result<List<Homework>>> =
        state.map { Result.success(it.filter { h -> h.teacherId == teacherId }) }

    /** Push homework to a class. */
    override suspend fun push(
        classId: String, subjectId: String, teacherId: String, teacherName: String,
        title: String, description: String, dueDate: String, attachments: List<String>,
    ): Result<Homework> {
        mockDelay()
        val id = "h-new-${UUID.randomUUID().toString().take(6)}"
        val nowIso = Clock.System.now().toString()
        val subject = MockData.subjects.firstOrNull { it.id == subjectId }
        val homework = Homework(
            id = id, classId = classId, subjectId = subjectId,
            subjectName = subject?.name ?: "—", teacherId = teacherId, teacherName = teacherName,
            title = title, description = description, dueDate = dueDate,
            attachments = attachments, academicYear = MockData.ACADEMIC_YEAR,
            createdAt = nowIso, pushedAt = nowIso, acknowledgedCount = 0,
        )
        state.value = state.value + homework
        log.i { "Pushed homework '$title' to class=$classId" }
        return Result.success(homework)
    }
}
