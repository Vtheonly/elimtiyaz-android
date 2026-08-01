package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.AcademicClass
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.CreateClassInput
import com.example.domain.repository.UpdateClassInput
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of ClassRepository.
 *
 * Table: `classes`. The DB schema (migration 0004) stores only FKs to
 * `academic_years` and `academic_levels`; the mobile DTO maps those to the
 * flat [AcademicClass] domain model with sensible defaults for derived
 * fields (level/gradeYear/academicYear/enrolledCount). The desktop is the
 * source of truth for fully-denormalized class views; the mobile app reads
 * enough to render class lists and picker dropdowns.
 *
 * Mutations audit-log via [AuditRepository.log] — actor_id/actor_name are
 * derived server-side from the JWT (per constraint §9).
 */
@Singleton
class SupabaseClassRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : ClassRepository {

    override fun observe() = flow {
        emit(fetchAll())
    }

    override fun observeByLevel(level: String) = flow {
        emit(try {
            // `level` is a derived field — fetch all active classes, filter client-side.
            provider.postgrest.from("classes")
                .select {
                    filter { eq("is_active", true) }
                    order("code", Order.ASCENDING)
                    limit(200)
                }
                .decodeList<ClassDto>()
                .map { it.toDomain() }
                .filter { it.level.isBlank() || it.level.equals(level, ignoreCase = true) }
        } catch (e: Exception) { fetchAll() })
    }

    override fun observeById(id: String) = flow {
        emit(fetchById(id))
    }

    override suspend fun createClass(input: CreateClassInput, actorId: String, actorName: String): Result<AcademicClass> = try {
        require(input.name.isNotBlank()) { "Class name is required" }
        require(input.capacity > 0) { "Capacity must be > 0" }
        require(input.academicYear.isNotBlank()) { "Academic year is required" }
        val dto = ClassInsertDto(
            name = input.name,
            section = "",
            code = input.name,
            capacity = input.capacity,
            room = input.room,
            homeroomTeacherId = input.homeroomTeacherId,
        )
        val inserted = provider.postgrest.from("classes").insert(dto) {
            select()
        }.decodeList<ClassDto>().first()
        val klass = inserted.toDomain()
        auditRepository.log(AuditLogInput(
            action = AuditActions.CLASS_CREATE,
            entityType = "class",
            entityId = klass.id,
            afterJson = """{"code":"${inserted.code}","name":"${inserted.name ?: inserted.code}","capacity":${klass.capacity}}""",
            note = "Class created from Android app",
        ))
        Result.Ok(klass)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun updateClass(id: String, input: UpdateClassInput, actorId: String, actorName: String): Result<AcademicClass> {
        val updates = mutableMapOf<String, String>()
        input.name?.let { updates["name"] = it }
        input.room?.let { updates["room"] = it }
        input.capacity?.let { updates["capacity"] = it.toString() }
        input.homeroomTeacherId?.let { updates["homeroom_teacher_id"] = it }
        if (updates.isEmpty()) return Result.Err(Errors.validation("No fields to update"))
        return try {
            val updated = provider.postgrest.from("classes").update(updates) {
                filter { eq("id", id) }
                select()
            }.decodeList<ClassDto>().first()
            val klass = updated.toDomain()
            auditRepository.log(AuditLogInput(
                action = AuditActions.CLASS_UPDATE,
                entityType = "class",
                entityId = id,
                afterJson = updates.entries.joinToString(",", "{", "}") { (k, v) -> "\"$k\":\"$v\"" },
                note = "Class updated from Android app",
            ))
            Result.Ok(klass)
        } catch (e: Exception) {
            Result.Err(Errors.fromException(e))
        }
    }

    override suspend fun deleteClass(id: String, actorId: String, actorName: String): Result<Unit> = try {
        // Soft-delete via is_active flag (RLS hides inactive classes from SELECT).
        provider.postgrest.from("classes").update(mapOf("is_active" to "false")) {
            filter { eq("id", id) }
        }
        auditRepository.log(AuditLogInput(
            action = AuditActions.CLASS_DELETE,
            entityType = "class",
            entityId = id,
            note = "Class archived (is_active=false) from Android app",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    private suspend fun fetchAll(): List<AcademicClass> = try {
        provider.postgrest.from("classes")
            .select {
                filter { eq("is_active", true) }
                order("code", Order.ASCENDING)
                limit(200)
            }
            .decodeList<ClassDto>()
            .map { it.toDomain() }
    } catch (e: Exception) { emptyList() }

    private suspend fun fetchById(id: String): AcademicClass? = try {
        provider.postgrest.from("classes")
            .select { filter { eq("id", id) } }
            .decodeList<ClassDto>()
            .firstOrNull()
            ?.toDomain()
    } catch (e: Exception) { null }

    @Serializable
    data class ClassDto(
        val id: String,
        val tenantId: String,
        val code: String,
        val name: String? = null,
        val section: String? = null,
        val capacity: Int,
        val homeroomTeacherId: String? = null,
        val room: String? = null,
        val isActive: Boolean = true,
    ) {
        fun toDomain() = AcademicClass(
            id = id,
            tenantId = tenantId,
            name = name ?: code,
            level = "",            // derived from academic_levels.cycle (desktop fills this)
            gradeYear = 0,         // derived from academic_levels.year_number
            homeroomTeacherId = homeroomTeacherId,
            homeroomTeacherName = null, // joined from personnel on desktop side
            room = room,
            capacity = capacity,
            enrolledCount = 0,     // computed by desktop via students WHERE class_id=...
            academicYear = "",     // derived from academic_years.label
        )
    }

    @Serializable
    data class ClassInsertDto(
        val name: String,
        val section: String,
        val code: String,
        val capacity: Int,
        val room: String? = null,
        val homeroomTeacherId: String? = null,
    )
}
