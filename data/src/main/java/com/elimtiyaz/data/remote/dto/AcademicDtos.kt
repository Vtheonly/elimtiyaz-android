package com.elimtiyaz.data.remote.dto

import com.elimtiyaz.domain.model.AcademicClass
import com.elimtiyaz.domain.model.Assessment
import com.elimtiyaz.domain.model.AttendanceRecord
import com.elimtiyaz.domain.model.AttendanceSession
import com.elimtiyaz.domain.model.ClassSubject
import com.elimtiyaz.domain.model.Homework
import com.elimtiyaz.domain.model.Subject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire DTO for the `academic_classes` table. */
@Serializable
data class AcademicClassDto(
    val id: String,
    @SerialName("tenant_id") val tenantId: String,
    val name: String,
    val level: String,
    @SerialName("grade_year") val gradeYear: Int,
    @SerialName("homeroom_teacher_id") val homeroomTeacherId: String? = null,
    @SerialName("homeroom_teacher_name") val homeroomTeacherName: String? = null,
    val room: String? = null,
    val capacity: Int = 30,
    @SerialName("enrolled_count") val enrolledCount: Int = 0,
    @SerialName("academic_year") val academicYear: String,
) {
    /** Convert to a domain [AcademicClass]. */
    fun toDomain(): AcademicClass = AcademicClass(
        id = id, tenantId = tenantId, name = name, level = level, gradeYear = gradeYear,
        homeroomTeacherId = homeroomTeacherId, homeroomTeacherName = homeroomTeacherName,
        room = room, capacity = capacity, enrolledCount = enrolledCount, academicYear = academicYear,
    )

    companion object {
        /** Build a DTO from a domain [AcademicClass]. */
        fun fromDomain(c: AcademicClass): AcademicClassDto = AcademicClassDto(
            id = c.id, tenantId = c.tenantId, name = c.name, level = c.level, gradeYear = c.gradeYear,
            homeroomTeacherId = c.homeroomTeacherId, homeroomTeacherName = c.homeroomTeacherName,
            room = c.room, capacity = c.capacity, enrolledCount = c.enrolledCount, academicYear = c.academicYear,
        )
    }
}

/** Wire DTO for the `subjects` table. */
@Serializable
data class SubjectDto(
    val id: String,
    @SerialName("tenant_id") val tenantId: String,
    val name: String,
    @SerialName("name_ar") val nameAr: String? = null,
    val code: String,
    val level: String,
    val coefficient: Double = 1.0,
    @SerialName("is_extracurricular") val isExtracurricular: Boolean = false,
    @SerialName("passing_grade") val passingGrade: Double = 10.0,
) {
    /** Convert to a domain [Subject]. */
    fun toDomain(): Subject = Subject(
        id = id, tenantId = tenantId, name = name, nameAr = nameAr, code = code, level = level,
        coefficient = coefficient, isExtracurricular = isExtracurricular, passingGrade = passingGrade,
    )

    companion object {
        /** Build a DTO from a domain [Subject]. */
        fun fromDomain(s: Subject): SubjectDto = SubjectDto(
            id = s.id, tenantId = s.tenantId, name = s.name, nameAr = s.nameAr, code = s.code,
            level = s.level, coefficient = s.coefficient, isExtracurricular = s.isExtracurricular, passingGrade = s.passingGrade,
        )
    }
}

/** Wire DTO for the `class_subjects` join table. */
@Serializable
data class ClassSubjectDto(
    val id: String,
    @SerialName("class_id") val classId: String,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("teacher_id") val teacherId: String? = null,
    @SerialName("teacher_name") val teacherName: String? = null,
    @SerialName("weekly_hours") val weeklyHours: Int,
    val coefficient: Double = 1.0,
) {
    /** Convert to a domain [ClassSubject]. */
    fun toDomain(): ClassSubject = ClassSubject(
        id = id, classId = classId, subjectId = subjectId, teacherId = teacherId,
        teacherName = teacherName, weeklyHours = weeklyHours, coefficient = coefficient,
    )

    companion object {
        /** Build a DTO from a domain [ClassSubject]. */
        fun fromDomain(cs: ClassSubject): ClassSubjectDto = ClassSubjectDto(
            id = cs.id, classId = cs.classId, subjectId = cs.subjectId, teacherId = cs.teacherId,
            teacherName = cs.teacherName, weeklyHours = cs.weeklyHours, coefficient = cs.coefficient,
        )
    }
}

/** Wire DTO for the `assessments` table — captures Devoir 1/2 + Examen grades. */
@Serializable
data class AssessmentDto(
    val id: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("class_id") val classId: String,
    val term: String,
    @SerialName("academic_year") val academicYear: String,
    @SerialName("devoir_1") val devoir1: Double? = null,
    @SerialName("devoir_2") val devoir2: Double? = null,
    val examen: Double? = null,
    @SerialName("subject_average") val subjectAverage: Double? = null,
    val coefficient: Double = 1.0,
    @SerialName("entered_by") val enteredBy: String,
    @SerialName("entered_at") val enteredAt: String,
) {
    /** Convert to a domain [Assessment]. */
    fun toDomain(): Assessment = Assessment(
        id = id, studentId = studentId, subjectId = subjectId, classId = classId,
        term = term, academicYear = academicYear, devoir1 = devoir1, devoir2 = devoir2,
        examen = examen, subjectAverage = subjectAverage, coefficient = coefficient,
        enteredBy = enteredBy, enteredAt = enteredAt,
    )

    companion object {
        /** Build a DTO from a domain [Assessment]. */
        fun fromDomain(a: Assessment): AssessmentDto = AssessmentDto(
            id = a.id, studentId = a.studentId, subjectId = a.subjectId, classId = a.classId,
            term = a.term, academicYear = a.academicYear, devoir1 = a.devoir1, devoir2 = a.devoir2,
            examen = a.examen, subjectAverage = a.subjectAverage, coefficient = a.coefficient,
            enteredBy = a.enteredBy, enteredAt = a.enteredAt,
        )
    }
}

/** Wire DTO for the `attendance_records` table. */
@Serializable
data class AttendanceRecordDto(
    val id: String,
    @SerialName("student_id") val studentId: String,
    @SerialName("class_id") val classId: String,
    val date: String,
    val session: AttendanceSession,
    val status: String,
    val note: String? = null,
    @SerialName("recorded_by") val recordedBy: String,
    @SerialName("recorded_at") val recordedAt: String,
    @SerialName("synced_at") val syncedAt: String? = null,
) {
    /** Convert to a domain [AttendanceRecord]. */
    fun toDomain(): AttendanceRecord = AttendanceRecord(
        id = id, studentId = studentId, classId = classId, date = date, session = session,
        status = status, note = note, recordedBy = recordedBy, recordedAt = recordedAt, syncedAt = syncedAt,
    )

    companion object {
        /** Build a DTO from a domain [AttendanceRecord]. */
        fun fromDomain(r: AttendanceRecord): AttendanceRecordDto = AttendanceRecordDto(
            id = r.id, studentId = r.studentId, classId = r.classId, date = r.date, session = r.session,
            status = r.status, note = r.note, recordedBy = r.recordedBy, recordedAt = r.recordedAt, syncedAt = r.syncedAt,
        )
    }
}

/** Wire DTO for the `homework` table. */
@Serializable
data class HomeworkDto(
    val id: String,
    @SerialName("class_id") val classId: String,
    @SerialName("subject_id") val subjectId: String,
    @SerialName("subject_name") val subjectName: String,
    @SerialName("teacher_id") val teacherId: String,
    @SerialName("teacher_name") val teacherName: String,
    val title: String,
    val description: String,
    @SerialName("due_date") val dueDate: String,
    val attachments: List<String> = emptyList(),
    @SerialName("academic_year") val academicYear: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("pushed_at") val pushedAt: String? = null,
    @SerialName("acknowledged_count") val acknowledgedCount: Int = 0,
) {
    /** Convert to a domain [Homework]. */
    fun toDomain(): Homework = Homework(
        id = id, classId = classId, subjectId = subjectId, subjectName = subjectName,
        teacherId = teacherId, teacherName = teacherName, title = title, description = description,
        dueDate = dueDate, attachments = attachments, academicYear = academicYear, createdAt = createdAt,
        pushedAt = pushedAt, acknowledgedCount = acknowledgedCount,
    )

    companion object {
        /** Build a DTO from a domain [Homework]. */
        fun fromDomain(h: Homework): HomeworkDto = HomeworkDto(
            id = h.id, classId = h.classId, subjectId = h.subjectId, subjectName = h.subjectName,
            teacherId = h.teacherId, teacherName = h.teacherName, title = h.title, description = h.description,
            dueDate = h.dueDate, attachments = h.attachments, academicYear = h.academicYear, createdAt = h.createdAt,
            pushedAt = h.pushedAt, acknowledgedCount = h.acknowledgedCount,
        )
    }
}
