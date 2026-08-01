package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.Department
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.CreateDepartmentInput
import com.example.domain.repository.DepartmentRepository
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of DepartmentRepository.
 *
 * Table: `departments` (migration 0010). The DB uses `is_archived` (boolean)
 * rather than `archived_at` (timestamp); we map the domain model's
 * `archivedAt` to `updated_at` when archived, null otherwise.
 *
 * Archive / unarchive flip the `is_archived` flag — RLS hides archived
 * departments from new assignments but preserves them for history.
 *
 * Audit actions:
 *   - DEPARTMENT_CREATE / DEPARTMENT_ARCHIVE / DEPARTMENT_UNARCHIVE
 */
@Singleton
class SupabaseDepartmentRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : DepartmentRepository {

    override fun observe() = flow {
        emit(try {
            provider.postgrest.from("departments")
                .select {
                    filter {
                        eq("is_archived", false)
                        eq("is_active", true)
                    }
                    order("sort_order", Order.ASCENDING)
                    limit(100)
                }
                .decodeList<DepartmentDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override fun observeById(id: String) = flow {
        emit(try {
            provider.postgrest.from("departments")
                .select { filter { eq("id", id) } }
                .decodeList<DepartmentDto>()
                .firstOrNull()
                ?.toDomain()
        } catch (e: Exception) { null })
    }

    override suspend fun createDepartment(input: CreateDepartmentInput, actorId: String, actorName: String): Result<Department> = try {
        require(input.name.isNotBlank()) { "Department name is required" }
        val dto = DepartmentInsertDto(
            nameFr = input.name,
            description = input.description,
            headPersonnelId = input.headPersonnelId,
            colorHex = input.colorHex,
            code = "DEP-${System.currentTimeMillis().toString().takeLast(6)}",
        )
        val inserted = provider.postgrest.from("departments").insert(dto) { select() }
            .decodeList<DepartmentDto>().first()
        val department = inserted.toDomain()
        auditRepository.log(AuditLogInput(
            action = AuditActions.DEPARTMENT_CREATE,
            entityType = "department",
            entityId = department.id,
            afterJson = """{"code":"${inserted.code}","name":"${department.name}"}""",
            note = "Department created from Android app",
        ))
        Result.Ok(department)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun archiveDepartment(id: String, actorId: String, actorName: String): Result<Unit> = try {
        provider.postgrest.from("departments").update(mapOf("is_archived" to "true")) {
            filter { eq("id", id) }
        }
        auditRepository.log(AuditLogInput(
            action = AuditActions.DEPARTMENT_ARCHIVE,
            entityType = "department",
            entityId = id,
            afterJson = """{"is_archived":true}""",
            note = "Department archived from Android app",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun unarchiveDepartment(id: String, actorId: String, actorName: String): Result<Unit> = try {
        provider.postgrest.from("departments").update(mapOf("is_archived" to "false")) {
            filter { eq("id", id) }
        }
        auditRepository.log(AuditLogInput(
            action = AuditActions.DEPARTMENT_UNARCHIVE,
            entityType = "department",
            entityId = id,
            afterJson = """{"is_archived":false}""",
            note = "Department unarchived from Android app",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    @Serializable
    data class DepartmentDto(
        val id: String,
        val tenantId: String,
        val code: String,
        val nameFr: String,
        val labelAr: String? = null,
        val colorHex: String? = null,
        val headPersonnelId: String? = null,
        val description: String? = null,
        val sortOrder: Int = 0,
        val isActive: Boolean = true,
        val isArchived: Boolean = false,
        val createdAt: String = "",
        val updatedAt: String = "",
    ) {
        fun toDomain() = Department(
            id = id,
            tenantId = tenantId,
            name = nameFr,
            description = description,
            headPersonnelId = headPersonnelId,
            parentDepartmentId = null, // not modeled in DB schema
            colorHex = colorHex,
            archivedAt = if (isArchived) updatedAt else null,
        )
    }

    @Serializable
    data class DepartmentInsertDto(
        val code: String,
        val nameFr: String,
        val description: String? = null,
        val headPersonnelId: String? = null,
        val colorHex: String? = null,
    )
}
