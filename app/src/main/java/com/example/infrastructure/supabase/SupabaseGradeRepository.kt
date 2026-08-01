package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.Assessment
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.EnterGradeInput
import com.example.domain.repository.GradeRepository
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of GradeRepository.
 *
 * Table: `grades` (joined with `assessments` + `class_subjects` for
 * denormalized reads). Migration 0004 declares the schema.
 *
 * - `enterGrade` upserts three grade rows (devoir_1, devoir_2, examen) for
 *   the given (student, subject, term, year). The
 *   `compute_grade_subject_average()` trigger auto-fills `subject_average`
 *   via `(D1 + D2 + 2*Examen) / 4`.
 * - Observers aggregate the three grade rows into a single [Assessment]
 *   domain object per (student, subject, term).
 *
 * Audit action: `AuditActions.GRADE_ENTER`.
 */
@Singleton
class SupabaseGradeRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : GradeRepository {

    override fun observeForStudent(studentId: String, term: String, academicYear: String) = flow {
        emit(fetchForStudent(studentId, term, academicYear))
    }

    override fun observeForClass(classId: String, subjectId: String, term: String, academicYear: String) = flow {
        emit(fetchForClass(classId, subjectId, term, academicYear))
    }

    override suspend fun enterGrade(input: EnterGradeInput, actorId: String, actorName: String): Result<Assessment> {
        return try {
            require(input.studentId.isNotBlank()) { "Student ID required" }
            require(input.subjectId.isNotBlank()) { "Subject ID required" }
            require(input.coefficient > 0) { "Coefficient must be > 0" }

            // Resolve the class_subject_id for (classId, subjectId)
            val classSubjectId = resolveClassSubjectId(input.classId, input.subjectId)
                ?: return Result.Err(Errors.notFound("class_subjects row not found for class=${input.classId} subject=${input.subjectId}"))

            // Resolve (or create) the three assessment rows for this (class_subject_id, term)
            val termInt = input.term.removePrefix("T").toIntOrNull() ?: 1

            val devoir1AssessmentId = resolveAssessmentId(classSubjectId, termInt, "devoir_1", "Devoir 1 Trimestre $termInt")
            val devoir2AssessmentId = resolveAssessmentId(classSubjectId, termInt, "devoir_2", "Devoir 2 Trimestre $termInt")
            val examenAssessmentId = resolveAssessmentId(classSubjectId, termInt, "examen", "Examen Trimestre $termInt")

            // Upsert the three grade rows (the trigger recomputes subject_average)
            input.devoir1?.let { upsertGrade(input.studentId, classSubjectId, devoir1AssessmentId, it, input.coefficient, actorId) }
            input.devoir2?.let { upsertGrade(input.studentId, classSubjectId, devoir2AssessmentId, it, input.coefficient, actorId) }
            input.examen?.let { upsertGrade(input.studentId, classSubjectId, examenAssessmentId, it, input.coefficient, actorId) }

            // Fetch back the aggregated row (trigger has populated subject_average)
            val aggregated = fetchAggregatedForStudentSubject(input.studentId, classSubjectId, termInt, input.academicYear, input.coefficient)

            auditRepository.log(AuditLogInput(
                action = AuditActions.GRADE_ENTER,
                entityType = "grade",
                entityId = "${input.studentId}:${input.subjectId}:T$termInt",
                afterJson = """{"devoir1":${input.devoir1 ?: "null"},"devoir2":${input.devoir2 ?: "null"},"examen":${input.examen ?: "null"},"subject_average":${aggregated?.subjectAverage ?: "null"}}""",
                note = "Grade entered from Android app",
            ))

            Result.Ok(aggregated ?: Assessment(
                id = "${input.studentId}:${input.subjectId}:T$termInt",
                tenantId = "",
                studentId = input.studentId,
                subjectId = input.subjectId,
                classId = input.classId,
                term = "T$termInt",
                academicYear = input.academicYear,
                devoir1 = input.devoir1,
                devoir2 = input.devoir2,
                examen = input.examen,
                subjectAverage = null,
                coefficient = input.coefficient,
                enteredBy = actorId,
                enteredAt = java.time.Instant.now().toString(),
            ))
        } catch (e: Exception) {
            Result.Err(Errors.fromException(e))
        }
    }

    private suspend fun resolveClassSubjectId(classId: String, subjectId: String): String? = try {
        provider.postgrest.from("class_subjects")
            .select {
                filter {
                    eq("class_id", classId)
                    eq("subject_id", subjectId)
                }
                limit(1)
            }
            .decodeList<ClassSubjectRowDto>()
            .firstOrNull()
            ?.id
    } catch (e: Exception) { null }

    private suspend fun resolveAssessmentId(classSubjectId: String, term: Int, kind: String, label: String): String = try {
        val existing = provider.postgrest.from("assessments")
            .select {
                filter {
                    eq("class_subject_id", classSubjectId)
                    eq("term", term.toString())
                    eq("kind", kind)
                }
                limit(1)
            }
            .decodeList<AssessmentRowDto>()
            .firstOrNull()
        existing?.id ?: run {
            val dto = AssessmentInsertDto(
                classSubjectId = classSubjectId,
                term = term,
                kind = kind,
                label = label,
            )
            provider.postgrest.from("assessments").insert(dto) { select() }
                .decodeList<AssessmentRowDto>().first().id
        }
    } catch (e: Exception) {
        // If we can't resolve/create the assessment row, fall back to a sentinel UUID
        "00000000-0000-0000-0000-000000000000"
    }

    private suspend fun upsertGrade(
        studentId: String, classSubjectId: String, assessmentId: String,
        score: Double, coefficient: Int, actorId: String,
    ) {
        val dto = GradeUpsertDto(
            studentId = studentId,
            classSubjectId = classSubjectId,
            assessmentId = assessmentId,
            score = score,
            isAbsent = false,
            enteredBy = actorId.takeIf { it.isNotBlank() },
        )
        provider.postgrest.from("grades").upsert(dto) {
            select()
        }
    }

    private suspend fun fetchAggregatedForStudentSubject(
        studentId: String, classSubjectId: String, term: Int,
        academicYear: String, coefficient: Int,
    ): Assessment? {
        return try {
            val rows = provider.postgrest.from("grades")
                .select {
                    filter {
                        eq("student_id", studentId)
                        eq("class_subject_id", classSubjectId)
                    }
                    order("entered_at", Order.DESCENDING)
                    limit(20)
                }
                .decodeList<GradeRowDto>()
            if (rows.isEmpty()) return null
        // Group by assessment.kind via the assessments table
        val d1 = rows.firstOrNull { it.assessment?.kind == "devoir_1" }?.score
        val d2 = rows.firstOrNull { it.assessment?.kind == "devoir_2" }?.score
        val ex = rows.firstOrNull { it.assessment?.kind == "examen" }?.score
        val sa = rows.mapNotNull { it.subjectAverage }.maxOrNull()
        Assessment(
            id = "$studentId:$classSubjectId:T$term",
            tenantId = rows.first().tenantId,
            studentId = studentId,
            subjectId = classSubjectId, // best-effort: class_subject_id acts as subject ref
            classId = "",
            term = "T$term",
            academicYear = academicYear,
            devoir1 = d1,
            devoir2 = d2,
            examen = ex,
            subjectAverage = sa,
            coefficient = coefficient,
            enteredBy = rows.first().enteredBy ?: actorIdPlaceholder,
            enteredAt = rows.first().enteredAt,
        )
        } catch (e: Exception) { null }
    }

    private suspend fun fetchForStudent(studentId: String, term: String, academicYear: String): List<Assessment> = try {
        val termInt = term.removePrefix("T").toIntOrNull() ?: 1
        provider.postgrest.from("grades")
            .select {
                filter { eq("student_id", studentId) }
                order("entered_at", Order.DESCENDING)
                limit(200)
            }
            .decodeList<GradeRowDto>()
            .filter { it.assessment?.term == termInt }
            .groupBy { it.classSubjectId }
            .map { (csId, rows) ->
                val d1 = rows.firstOrNull { it.assessment?.kind == "devoir_1" }?.score
                val d2 = rows.firstOrNull { it.assessment?.kind == "devoir_2" }?.score
                val ex = rows.firstOrNull { it.assessment?.kind == "examen" }?.score
                val sa = rows.mapNotNull { it.subjectAverage }.maxOrNull()
                Assessment(
                    id = "$studentId:$csId:T$termInt",
                    tenantId = rows.first().tenantId,
                    studentId = studentId,
                    subjectId = csId,
                    classId = "",
                    term = "T$termInt",
                    academicYear = academicYear,
                    devoir1 = d1, devoir2 = d2, examen = ex,
                    subjectAverage = sa,
                    coefficient = 1,
                    enteredBy = rows.first().enteredBy ?: actorIdPlaceholder,
                    enteredAt = rows.first().enteredAt,
                )
            }
    } catch (e: Exception) { emptyList() }

    private suspend fun fetchForClass(classId: String, subjectId: String, term: String, academicYear: String): List<Assessment> {
        val termInt = term.removePrefix("T").toIntOrNull() ?: 1
        val classSubjectId = resolveClassSubjectId(classId, subjectId) ?: return emptyList()
        return try {
            provider.postgrest.from("grades")
                .select {
                    filter { eq("class_subject_id", classSubjectId) }
                    order("entered_at", Order.DESCENDING)
                    limit(500)
                }
                .decodeList<GradeRowDto>()
                .filter { it.assessment?.term == termInt }
                .groupBy { it.studentId }
                .map { (studentId, rows) ->
                    val d1 = rows.firstOrNull { it.assessment?.kind == "devoir_1" }?.score
                    val d2 = rows.firstOrNull { it.assessment?.kind == "devoir_2" }?.score
                    val ex = rows.firstOrNull { it.assessment?.kind == "examen" }?.score
                    val sa = rows.mapNotNull { it.subjectAverage }.maxOrNull()
                    Assessment(
                        id = "$studentId:$classSubjectId:T$termInt",
                        tenantId = rows.first().tenantId,
                        studentId = studentId,
                        subjectId = classSubjectId,
                        classId = classId,
                        term = "T$termInt",
                        academicYear = academicYear,
                        devoir1 = d1, devoir2 = d2, examen = ex,
                        subjectAverage = sa,
                        coefficient = 1,
                        enteredBy = rows.first().enteredBy ?: actorIdPlaceholder,
                        enteredAt = rows.first().enteredAt,
                    )
                }
        } catch (e: Exception) { emptyList() }
    }

    private val actorIdPlaceholder = "unknown"

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
}
