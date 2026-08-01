package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.Personnel
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.CreatePersonnelInput
import com.example.domain.repository.PersonnelRepository
import com.example.domain.repository.UpdatePersonnelInput
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of PersonnelRepository.
 *
 * Table: `personnel` (migration 0009). Mobile-only fields
 * (`avatarUrl`, `weeklyHoursTarget`, `weeklyHoursLogged`) are not present
 * in the DB schema and are filled with sensible defaults by [PersonnelDto.toDomain].
 *
 * Soft-delete uses the `deleted_at` column (RLS hides soft-deleted rows on
 * SELECT). The `status` field is derived from `is_active` + `end_date`.
 *
 * Audit actions:
 *   - PERSONNEL_CREATE / PERSONNEL_UPDATE / PERSONNEL_DELETE
 */
@Singleton
class SupabasePersonnelRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : PersonnelRepository {

    override fun observe() = flow { emit(fetchAll()) }

    override fun observeByCategory(category: String) = flow {
        emit(try {
            provider.postgrest.from("personnel")
                .select {
                    filter { eq("staff_category", category) }
                    order("last_name", Order.ASCENDING)
                    limit(200)
                }
                .decodeList<PersonnelDto>()
                .filter { it.endDate == null }
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() })
    }

    override fun observeById(id: String) = flow {
        emit(try {
            provider.postgrest.from("personnel")
                .select { filter { eq("id", id) } }
                .decodeList<PersonnelDto>()
                .firstOrNull()
                ?.toDomain()
        } catch (e: Exception) { null })
    }

    override fun observeByUserId(userId: String) = flow {
        emit(try {
            provider.postgrest.from("personnel")
                .select { filter { eq("user_id", userId) } }
                .decodeList<PersonnelDto>()
                .firstOrNull()
                ?.toDomain()
        } catch (e: Exception) { null })
    }

    override suspend fun createPersonnel(input: CreatePersonnelInput, actorId: String, actorName: String): Result<Personnel> = try {
        require(input.firstName.isNotBlank()) { "First name is required" }
        require(input.lastName.isNotBlank()) { "Last name is required" }
        require(input.staffCategory.isNotBlank()) { "Staff category is required" }
        require(input.roleId.isNotBlank()) { "Role ID is required" }
        require(input.phone.isNotBlank()) { "Phone is required" }

        val dto = PersonnelInsertDto(
            firstName = input.firstName,
            lastName = input.lastName,
            staffCategory = input.staffCategory,
            roleId = input.roleId,
            departmentId = input.departmentId,
            position = input.position,
            primaryPhone = input.phone,
            email = input.email,
            hireDate = input.hireDate,
            baseSalary = input.salary,
        )
        val inserted = provider.postgrest.from("personnel").insert(dto) { select() }
            .decodeList<PersonnelDto>().first()
        val personnel = inserted.toDomain()
        auditRepository.log(AuditLogInput(
            action = AuditActions.PERSONNEL_CREATE,
            entityType = "personnel",
            entityId = personnel.id,
            afterJson = """{"code":"${inserted.personnelCode}","name":"${personnel.fullName}","category":"${personnel.staffCategory}"}""",
            note = "Personnel created from Android app",
        ))
        Result.Ok(personnel)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun updatePersonnel(id: String, input: UpdatePersonnelInput, actorId: String, actorName: String): Result<Personnel> {
        val updates = mutableMapOf<String, String>()
        input.position?.let { updates["position"] = it }
        input.phone?.let { updates["primary_phone"] = it }
        input.email?.let { updates["email"] = it }
        input.salary?.let { updates["base_salary"] = it.toString() }
        input.departmentId?.let { updates["department_id"] = it }
        input.status?.let {
            updates["is_active"] = (it == "active").toString()
            if (it == "terminated") updates["end_date"] = java.time.LocalDate.now().toString()
        }
        if (updates.isEmpty()) return Result.Err(Errors.validation("No fields to update"))
        return try {
            val updated = provider.postgrest.from("personnel").update(updates) {
                filter { eq("id", id) }
                select()
            }.decodeList<PersonnelDto>().first()
            val personnel = updated.toDomain()
            auditRepository.log(AuditLogInput(
                action = AuditActions.PERSONNEL_UPDATE,
                entityType = "personnel",
                entityId = id,
                afterJson = updates.entries.joinToString(",", "{", "}") { (k, v) -> """"$k":"$v"""" },
                note = "Personnel updated from Android app",
            ))
            Result.Ok(personnel)
        } catch (e: Exception) {
            Result.Err(Errors.fromException(e))
        }
    }

    override suspend fun deletePersonnel(id: String, actorId: String, actorName: String): Result<Unit> = try {
        // Soft-delete via deleted_at (RLS hides soft-deleted rows on SELECT).
        provider.postgrest.from("personnel").update(mapOf("deleted_at" to java.time.Instant.now().toString())) {
            filter { eq("id", id) }
        }
        auditRepository.log(AuditLogInput(
            action = AuditActions.PERSONNEL_DELETE,
            entityType = "personnel",
            entityId = id,
            note = "Personnel soft-deleted from Android app",
        ))
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    private suspend fun fetchAll(): List<Personnel> = try {
        provider.postgrest.from("personnel")
            .select {
                order("last_name", Order.ASCENDING)
                limit(300)
            }
            .decodeList<PersonnelDto>()
            .filter { it.endDate == null }
            .map { it.toDomain() }
    } catch (e: Exception) { emptyList() }

    @Serializable
    data class PersonnelDto(
        val id: String,
        val tenantId: String,
        val personnelCode: String,
        val userId: String? = null,
        val firstName: String,
        val lastName: String,
        val staffCategory: String,
        val roleId: String? = null,
        val departmentId: String? = null,
        val position: String? = null,
        val hireDate: String? = null,
        val endDate: String? = null,
        val isActive: Boolean = true,
        val baseSalary: Long? = null,
        val primaryPhone: String? = null,
        val email: String? = null,
        val createdAt: String = "",
        val updatedAt: String = "",
    ) {
        fun toDomain() = Personnel(
            id = id,
            tenantId = tenantId,
            userId = userId,
            firstName = firstName,
            lastName = lastName,
            staffCategory = staffCategory,
            roleId = roleId ?: "",
            departmentId = departmentId,
            position = position ?: "",
            phone = primaryPhone ?: "",
            email = email,
            hireDate = hireDate ?: "",
            terminationDate = endDate,
            salary = baseSalary,
            status = if (isActive) "active" else "terminated",
            avatarUrl = null, // not stored in DB; desktop can add later
            weeklyHoursTarget = 0, // not stored in DB; derived from schedules
            weeklyHoursLogged = 0, // not stored in DB; derived from releve_entries
        )
    }

    @Serializable
    data class PersonnelInsertDto(
        val firstName: String,
        val lastName: String,
        val staffCategory: String,
        val roleId: String,
        val departmentId: String? = null,
        val position: String? = null,
        val primaryPhone: String,
        val email: String? = null,
        val hireDate: String,
        val baseSalary: Long? = null,
    )
}
