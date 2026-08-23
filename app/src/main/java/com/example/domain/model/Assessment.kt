package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Subject-grade assessment for one student in one term — composed of two
 * devoirs and one examen, with the subject average recomputed server-side
 * by the `compute_grade_subject_average` trigger using the
 * `(D1 + D2 + 2*Ex) / 4.0` formula.
 */
@Serializable
data class Assessment(
    val id: String,
    val tenantId: String,
    val studentId: String,
    val subjectId: String,
    val classId: String,
    val term: String,                    // T1 | T2 | T3
    val academicYear: String,
    val devoir1: Double? = null,
    val devoir2: Double? = null,
    val examen: Double? = null,
    val subjectAverage: Double? = null,
    val coefficient: Double,
    /** Canonical rule (desktop academic.ts + SQL fn_calculate_student_term_gpa):
     *  extracurricular modules are EXCLUDED from the official GPA. */
    val isExtracurricular: Boolean = false,
    val enteredBy: String,
    val enteredAt: String,
)
