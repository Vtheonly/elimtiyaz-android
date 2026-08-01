package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.Homework
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.HomeworkRepository
import com.example.domain.repository.PushHomeworkInput
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of HomeworkRepository.
 *
 * Table: `homework` — columns mirror the [Homework] domain model.
 * Attachments are stored as a JSON array column (`attachments`) so the
 * client can attach multiple files per assignment. The server validates
 * that the teacher (actor) is assigned to the class+subject via
 * `class_subjects.teacher_id` (RLS-enforced).
 *
 * Audit action: `AuditActions.HOMEWORK_PUSH` (`homework.push`).
 */
@Singleton
class SupabaseHomeworkRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : HomeworkRepository {

    override fun observeForClass(classId: String) = flow {
        emit(try {
            provider.postgrest.from("homework")
                .select {
                    filter { eq("class_id", classId) }
                    order("due_date", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<HomeworkDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override fun observeForTeacher(teacherId: String) = flow {
        emit(try {
            provider.postgrest.from("homework")
                .select {
                    filter { eq("teacher_id", teacherId) }
                    order("created_at", Order.DESCENDING)
                    limit(100)
                }
                .decodeList<HomeworkDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override suspend fun push(input: PushHomeworkInput, actorId: String, actorName: String): Result<Homework> = try {
        require(input.classId.isNotBlank()) { "Class ID is required" }
        require(input.subjectId.isNotBlank()) { "Subject ID is required" }
        require(input.title.isNotBlank()) { "Title is required" }
        require(input.description.isNotBlank()) { "Description is required" }
        require(input.dueDate.isNotBlank()) { "Due date is required" }

        val dto = HomeworkInsertDto(
            classId = input.classId,
            subjectId = input.subjectId,
            teacherId = actorId,
            teacherName = actorName,
            subjectName = "", // desktop can backfill via join
            title = input.title,
            description = input.description,
            dueDate = input.dueDate,
            attachments = input.attachments,
            academicYear = input.academicYear,
        )
        val inserted = provider.postgrest.from("homework").insert(dto) {
            select()
        }.decodeList<HomeworkDto>().first()
        val homework = inserted.toDomain()
        auditRepository.log(AuditLogInput(
            action = AuditActions.HOMEWORK_PUSH,
            entityType = "homework",
            entityId = homework.id,
            afterJson = """{"title":"${homework.title}","class_id":"${homework.classId}","subject_id":"${homework.subjectId}","due_date":"${homework.dueDate}","attachment_count":${homework.attachments.size}}""",
            note = "Homework pushed from Android app by $actorName",
        ))
        Result.Ok(homework)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    @Serializable
    data class HomeworkDto(
        val id: String,
        val tenantId: String,
        val classId: String,
        val subjectId: String,
        val subjectName: String? = null,
        val teacherId: String,
        val teacherName: String? = null,
        val title: String,
        val description: String,
        val dueDate: String,
        val attachments: List<String> = emptyList(),
        val academicYear: String? = null,
        val createdAt: String,
        val pushedAt: String? = null,
        val acknowledgedCount: Int = 0,
    ) {
        fun toDomain() = Homework(
            id = id,
            tenantId = tenantId,
            classId = classId,
            subjectId = subjectId,
            subjectName = subjectName ?: "",
            teacherId = teacherId,
            teacherName = teacherName ?: "",
            title = title,
            description = description,
            dueDate = dueDate,
            attachments = attachments,
            academicYear = academicYear ?: "",
            createdAt = createdAt,
            pushedAt = pushedAt,
            acknowledgedCount = acknowledgedCount,
        )
    }

    @Serializable
    data class HomeworkInsertDto(
        val classId: String,
        val subjectId: String,
        val teacherId: String,
        val teacherName: String,
        val subjectName: String = "",
        val title: String,
        val description: String,
        val dueDate: String,
        val attachments: List<String> = emptyList(),
        val academicYear: String,
    )
}
