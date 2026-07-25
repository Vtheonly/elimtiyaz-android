package com.elimtiyaz.domain.repository

import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.model.AcademicClass
import com.elimtiyaz.domain.model.Assessment
import com.elimtiyaz.domain.model.AttendanceRecord
import com.elimtiyaz.domain.model.AttendanceSession
import com.elimtiyaz.domain.model.ClassSubject
import com.elimtiyaz.domain.model.Homework
import com.elimtiyaz.domain.model.Subject
import kotlinx.coroutines.flow.Flow

interface ClassRepository {
    fun classes(): Flow<Result<List<AcademicClass>>>
    fun classesByLevel(level: String): Flow<Result<List<AcademicClass>>>
    fun classById(id: String): Flow<Result<AcademicClass>>
    suspend fun createClass(name: String, level: String, gradeYear: Int, room: String?, capacity: Int, academicYear: String): Result<AcademicClass>
    suspend fun updateClass(id: String, name: String?, room: String?, capacity: Int?, homeroomTeacherId: String?): Result<AcademicClass>
    suspend fun deleteClass(id: String): Result<Unit>
}

interface SubjectRepository {
    fun subjects(): Flow<Result<List<Subject>>>
    fun subjectsByLevel(level: String): Flow<Result<List<Subject>>>
    fun subjectsByClass(classId: String): Flow<Result<List<ClassSubject>>>
    suspend fun assignSubjectToClass(classId: String, subjectId: String, teacherId: String?, weeklyHours: Int, coefficient: Double): Result<ClassSubject>
    suspend fun removeSubjectFromClass(id: String): Result<Unit>
}

interface GradeRepository {
    fun gradesForStudent(studentId: String, term: String?, academicYear: String): Flow<Result<List<Assessment>>>
    fun gradesForClass(classId: String, subjectId: String?, term: String, academicYear: String): Flow<Result<List<Assessment>>>
    suspend fun enterGrade(
        studentId: String, subjectId: String, classId: String,
        term: String, academicYear: String,
        devoir1: Double?, devoir2: Double?, examen: Double?,
        coefficient: Double, enteredBy: String,
    ): Result<Assessment>

    /** Compute subject average = (D1 + D2 + 2*Examen) / 4 — §06.02. */
    fun subjectAverage(devoir1: Double?, devoir2: Double?, examen: Double?): Double?
    /** Compute GPA = Σ(subj_avg × coef) / Σ(coef) — §06.03. */
    fun overallGpa(assessments: List<Assessment>): Double
}

interface AttendanceRepository {
    fun recordsByClass(classId: String, date: String): Flow<Result<List<AttendanceRecord>>>
    fun recordsByStudent(studentId: String, from: String, to: String): Flow<Result<List<AttendanceRecord>>>
    suspend fun recordRollCall(
        classId: String, date: String, session: AttendanceSession,
        statuses: Map<String, String>, recordedBy: String,
    ): Result<List<AttendanceRecord>>
    suspend fun alertAbsences(recordIds: List<String>): Result<Unit>
}

interface HomeworkRepository {
    fun homeworkForClass(classId: String): Flow<Result<List<Homework>>>
    fun homeworkByTeacher(teacherId: String): Flow<Result<List<Homework>>>
    suspend fun push(
        classId: String, subjectId: String, teacherId: String, teacherName: String,
        title: String, description: String, dueDate: String, attachments: List<String>,
    ): Result<Homework>
}
