package com.example.infrastructure.supabase

import kotlinx.serialization.Serializable

/**
 * DTOs for the Supabase `grades` / `assessments` / `class_subjects` tables.
 *
 * Used exclusively by [SupabaseGradeRepository] for serialization to/from
 * the PostgREST API. Mirrors migration 0004's schema.
 */

@Serializable
data class ClassSubjectRowDto(
    val id: String,
    val classId: String,
    val subjectId: String,
)

@Serializable
data class AssessmentRowDto(
    val id: String,
    val classSubjectId: String,
    val term: Int,
    val kind: String,
    val label: String? = null,
)

@Serializable
data class AssessmentInsertDto(
    val classSubjectId: String,
    val term: Int,
    val kind: String,
    val label: String? = null,
    val maxScore: Double = 20.0,
    val weight: Double = 1.0,
)

@Serializable
data class GradeRowDto(
    val id: String,
    val tenantId: String,
    val studentId: String,
    val assessmentId: String,
    val classSubjectId: String,
    val score: Double,
    val subjectAverage: Double? = null,
    val isAbsent: Boolean = false,
    val enteredBy: String? = null,
    val enteredAt: String,
    val assessment: AssessmentRowDto? = null,
)

@Serializable
data class GradeUpsertDto(
    val studentId: String,
    val classSubjectId: String,
    val assessmentId: String,
    val score: Double,
    val isAbsent: Boolean = false,
    val enteredBy: String? = null,
)
