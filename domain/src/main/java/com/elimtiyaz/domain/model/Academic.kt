package com.elimtiyaz.domain.model

import kotlinx.serialization.Serializable

/**
 * Academic structure per master plan §05.
 *
 * Levels: Primaire (5 years) / CEM (4 years) / Lycée (3 years).
 * Each level has classes; each class has subject mappings with coefficients.
 */
@Serializable
data class AcademicClass(
    val id: String,
    val tenantId: String,
    val name: String,                  // e.g. "5ème A"
    val level: String,                 // primaire / cem / lycee
    val gradeYear: Int,
    val homeroomTeacherId: String? = null,
    val homeroomTeacherName: String? = null,
    val room: String? = null,
    val capacity: Int = 30,
    val enrolledCount: Int = 0,
    val academicYear: String,
)

@Serializable
data class Subject(
    val id: String,
    val tenantId: String,
    val name: String,
    val nameAr: String? = null,
    val code: String,                  // MATH, PHY, AR, FR, EN, ISL...
    val level: String,
    val coefficient: Double = 1.0,
    val isExtracurricular: Boolean = false,  // clubs & therapy (§05.07)
    val passingGrade: Double = 10.0,
)

@Serializable
data class ClassSubject(
    val id: String,
    val classId: String,
    val subjectId: String,
    val teacherId: String? = null,
    val teacherName: String? = null,
    val weeklyHours: Int,
    val coefficient: Double = 1.0,
)

/**
 * Grade entry — Devoir 1, Devoir 2, Examen (×2 weighting).
 * Subject average = (D1 + D2 + 2*Examen) / 4.
 * Overall GPA = Σ(subject_avg × coef) / Σ(coef).
 */
@Serializable
data class Assessment(
    val id: String,
    val studentId: String,
    val subjectId: String,
    val classId: String,
    val term: String,                  // T1 / T2 / T3
    val academicYear: String,
    val devoir1: Double? = null,       // 0..20
    val devoir2: Double? = null,
    val examen: Double? = null,
    val subjectAverage: Double? = null,
    val coefficient: Double = 1.0,
    val enteredBy: String,
    val enteredAt: String,
)

@Serializable
data class Homework(
    val id: String,
    val classId: String,
    val subjectId: String,
    val subjectName: String,
    val teacherId: String,
    val teacherName: String,
    val title: String,
    val description: String,
    val dueDate: String,
    val attachments: List<String> = emptyList(),   // signed-URL media
    val academicYear: String,
    val createdAt: String,
    val pushedAt: String? = null,
    val acknowledgedCount: Int = 0,
)

/**
 * Attendance — supports the 30-second roll call workflow (§09.01).
 * Persisted per student per session (morning / afternoon).
 */
@Serializable
data class AttendanceRecord(
    val id: String,
    val studentId: String,
    val classId: String,
    val date: String,
    val session: AttendanceSession,
    val status: String,                // present / absent_excused / absent_unexcused / late
    val note: String? = null,
    val recordedBy: String,
    val recordedAt: String,
    val syncedAt: String? = null,
)

@Serializable
enum class AttendanceSession { Morning, Afternoon, Both }
