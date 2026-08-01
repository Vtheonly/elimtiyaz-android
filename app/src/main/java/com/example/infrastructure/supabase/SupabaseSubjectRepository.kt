package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.Subject
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.CreateSubjectInput
import com.example.domain.repository.SubjectRepository
import com.example.domain.repository.UpdateSubjectInput
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of SubjectRepository.
 *
 * Tables: `subjects` (catalog) + `class_subjects` (junction with per-class
 * coefficient + teacher_id). Migration 0004 declares the schema.
 *
 * - `observeByClass` joins through `class_subjects` to fetch subjects
 *   assigned to a given class.
 * - `archiveSubject` soft-deletes via `is_active = false` (matches DB
 *   convention; `subjects` has no `archived_at` column).
 * - `assignSubjectToClass` upserts into `class_subjects` on the
 *   `(tenant_id, class_id, subject_id)` unique key.
 */
@Singleton
class SupabaseSubjectRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : SubjectRepository {

    override fun observe() = flow {
        emit(fetchAll())
    }

    override fun observeByLevel(level: String) = flow {
        emit(try {
            // `level` is mapped to `domain` (scolarite | club | therapy | auxiliary)
            provider.postgrest.from("subjects")
                .select {
                    filter { eq("domain", level); eq("is_active", true) }
                    order("name_fr", Order.ASCENDING)
                    limit(200)
                }
                .decodeList<SubjectDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override fun observeByClass(classId: String) = flow {
        emit(try {
            // Fetch all class_subjects for this class, then fetch subjects in a second query.
            val classSubjects = provider.postgrest.from("class_subjects")
                .select {
                    filter { eq("class_id", classId); eq("is_active", true) }
                    order("subject_id", Order.ASCENDING)
                }
                .decodeList<ClassSubjectDto>()
            val subjectIds = classSubjects.map { it.subjectId }.distinct()
            if (subjectIds.isEmpty()) emptyList()
            else {
                val subjects = provider.postgrest.from("subjects")
                    .select { filter { eq("is_active", true) } }
                    .decodeList<SubjectDto>()
                subjects.filter { it.id in subjectIds }.map { it.toDomain() }
            }
        } catch (e: Exception) { emptyList() })
    }

    override suspend fun createSubject(input: CreateSubjectInput, actorId: String, actorName: String): Result<Subject> = try {
        require(input.name.isNotBlank()) { "Subject name is required" }
        require(input.code.isNotBlank()) { "Subject code is required" }
        require(input.coefficient > 0) { "Coefficient must be > 0" }
        val dto = SubjectInsertDto(
            nameFr = input.name,
            nameAr = input.nameAr,
            code = input.code,
            domain = if (input.isExtracurricular) "club" else "scolarite",
            defaultCoefficient = input.coefficient,
        )
        val inserted = provider.postgrest.from("subjects").insert(dto) {
            select()
        }.decodeList<SubjectDto>().first()
        val subject = inserted.toDomain()
        auditRepository.log(AuditLogInput(
            action = AuditActions.SUBJECT_CREATE,
            entityType = "subject",
            entityId = subject.id,
            afterJson = """{"code":"${subject.code}","name":"${subject.name}"}""",
            note = "Subject created from Android app",
        ))
        Result.Ok(subject)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun updateSubject(id: String, input: UpdateSubjectInput, actorId: String, actorName: String): Result<Subject> {
        val updates = mutableMapOf<String, String>()
        input.name?.let { updates["name_fr"] = it }
        input.coefficient?.let { updates["default_coefficient"] = it.toString() }
        if (updates.isEmpty()) return Result.Err(Errors.validation("No fields to update"))
        return try {
            val updated = provider.postgrest.from("subjects").update(updates) {
                filter { eq("id", id) }
                select()
            }.decodeList<SubjectDto>().first()
            val subject = updated.toDomain()
            auditRepository.log(AuditLogInput(
                action = AuditActions.SUBJECT_UPDATE,
                entityType = "subject",
                entityId = id,
                afterJson = updates.entries.joinToString(",", "{", "}") { (k, v) -> """"$k":"$v"""" },
                note = "Subject updated from Android app",
            ))
            Result.Ok(subject)
        } catch (e: Exception) {
            Result.Err(Errors.fromException(e))
        }
    }

    override suspend fun archiveSubject(id: String, actorId: String, actorName: String): Result<Unit> = try {
        provider.postgrest.from("subjects").update(mapOf("is_active" to "false")) {
            filter { eq("id", id) }
        }
        auditRepository.log(AuditLogInput(
            action = AuditActions.SUBJECT_ARCHIVE,
            entityType = "subject",
            entityId = id,
            afterJson = """{"is_active":false}""",
            note = "Subject archived (is_active=false) from Android app",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun assignSubjectToClass(
        classId: String, subjectId: String, teacherId: String?,
        weeklyHours: Int, coefficient: Int,
        actorId: String, actorName: String,
    ): Result<Unit> = try {
        require(coefficient > 0) { "Coefficient must be > 0" }
        val dto = ClassSubjectInsertDto(
            classId = classId,
            subjectId = subjectId,
            teacherId = teacherId,
            coefficient = coefficient,
        )
        provider.postgrest.from("class_subjects").upsert(dto) {
            select()
        }
        auditRepository.log(AuditLogInput(
            action = AuditActions.SUBJECT_ASSIGN,
            entityType = "class_subject",
            entityId = "$classId:$subjectId",
            afterJson = """{"class_id":"$classId","subject_id":"$subjectId","coefficient":$coefficient,"teacher_id":"${teacherId ?: ""}"}""",
            note = "Subject assigned to class from Android app",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    private suspend fun fetchAll(): List<Subject> = try {
        provider.postgrest.from("subjects")
            .select {
                filter { eq("is_active", true) }
                order("name_fr", Order.ASCENDING)
                limit(300)
            }
            .decodeList<SubjectDto>()
            .map { it.toDomain() }
    } catch (e: Exception) { emptyList() }

    @Serializable
    data class SubjectDto(
        val id: String,
        val tenantId: String,
        val code: String,
        val nameFr: String,
        val nameAr: String? = null,
        val nameEn: String? = null,
        val domain: String = "scolarite",
        val defaultCoefficient: Int = 1,
        val isActive: Boolean = true,
    ) {
        fun toDomain() = Subject(
            id = id,
            tenantId = tenantId,
            name = nameFr,
            nameAr = nameAr,
            code = code,
            level = domain,            // domain acts as level filter on mobile
            coefficient = defaultCoefficient,
            isExtracurricular = domain == "club" || domain == "therapy",
            passingGrade = 10.0,
        )
    }

    @Serializable
    data class SubjectInsertDto(
        val nameFr: String,
        val nameAr: String? = null,
        val code: String,
        val domain: String = "scolarite",
        val defaultCoefficient: Int = 1,
    )

    @Serializable
    data class ClassSubjectDto(
        val id: String,
        val classId: String,
        val subjectId: String,
        val teacherId: String? = null,
        val coefficient: Int = 1,
        val isActive: Boolean = true,
        val subject: SubjectDto? = null,
    )

    @Serializable
    data class ClassSubjectInsertDto(
        val classId: String,
        val subjectId: String,
        val teacherId: String? = null,
        val coefficient: Int = 1,
    )
}
