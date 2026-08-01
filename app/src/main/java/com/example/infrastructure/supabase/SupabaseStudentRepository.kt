package com.example.infrastructure.supabase

import com.example.core.AuditActions
import com.example.core.Errors
import com.example.core.Result
import com.example.domain.model.Parent
import com.example.domain.model.Student
import com.example.domain.repository.AuditLogInput
import com.example.domain.repository.AuditRepository
import com.example.domain.repository.BatchRegisterResult
import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.CreateStudentInput
import com.example.domain.repository.PromotionDecision
import com.example.domain.repository.StudentRepository
import com.example.domain.repository.UpdateStudentInput
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SupabaseStudentRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val auditRepository: AuditRepository,
) : StudentRepository {

    override fun observe() = flow {
        emit(fetchAll())
    }

    override fun observeByParent(parentId: String) = flow {
        emit(fetchBy("parent_id", parentId))
    }

    override fun observeByClass(classId: String) = flow {
        emit(fetchBy("class_id", classId))
    }

    override fun observeById(id: String) = flow {
        emit(try {
            provider.postgrest.from("students")
                .select { filter { eq("id", id) } }
                .decodeList<StudentDto>()
                .firstOrNull()
                ?.toDomain()
        } catch (e: Exception) { null })
    }

    override fun search(query: String) = flow {
        val rows = if (query.isBlank()) fetchAll() else try {
            provider.postgrest.from("students")
                .select {
                    filter {
                        or {
                            ilike("first_name", "%$query%")
                            ilike("last_name", "%$query%")
                            ilike("code", "%$query%")
                        }
                    }
                    order("last_name", Order.ASCENDING)
                    limit(50)
                }
                .decodeList<StudentDto>()
                .map { it.toDomain() }
        } catch (e: Exception) { emptyList() }
        emit(rows)
    }

    override suspend fun createStudent(input: CreateStudentInput, actorId: String, actorName: String): Result<Student> = try {
        require(input.parentId != null) { "Parent ID is required (parent-first dependency)" }
        val dto = StudentInsertDto(
            parentId = input.parentId,
            firstName = input.firstName, lastName = input.lastName,
            gender = input.gender, birthDate = input.birthDate,
            level = input.level, gradeLevel = input.gradeLevel,
            classId = input.classId, medicalNotes = input.medicalNotes,
        )
        val inserted = provider.postgrest.from("students").insert(dto) { select() }.decodeList<StudentDto>().first()
        val student = inserted.toDomain()
        auditRepository.log(AuditLogInput(
            action = AuditActions.STUDENT_CREATE,
            entityType = "student",
            entityId = student.id,
            afterJson = """{"code":"${student.code}","name":"${student.fullName}"}""",
            note = "Student created from Android app",
        ))
        Result.Ok(student)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun updateStudent(id: String, input: UpdateStudentInput, actorId: String, actorName: String): Result<Student> {
        return try {
            val updates = mutableMapOf<String, String>()
            input.firstName?.let { updates["first_name"] = it }
            input.lastName?.let { updates["last_name"] = it }
            input.classId?.let { updates["class_id"] = it }
            input.status?.let { updates["status"] = it }
            input.medicalNotes?.let { updates["medical_notes"] = it }
            if (updates.isEmpty()) return Result.Err(Errors.validation("No fields to update"))
            val updated = provider.postgrest.from("students").update(updates) {
                filter { eq("id", id) }
                select()
            }.decodeList<StudentDto>().first()
            val student = updated.toDomain()
            auditRepository.log(AuditLogInput(
                action = AuditActions.STUDENT_UPDATE,
                entityType = "student",
                entityId = id,
                afterJson = updates.entries.joinToString(",", "{", "}") { (k, v) -> """"$k":"$v"""" },
                note = "Student updated from Android app",
            ))
            Result.Ok(student)
        } catch (e: Exception) {
            Result.Err(Errors.fromException(e))
        }
    }

    /**
     * Atomic batch registration via the `batch_register_family` RPC.
     * The RPC creates parent + N students + activation code in a single
     * transaction. If any step fails, the entire operation rolls back.
     */
    override suspend fun batchRegister(parent: CreateParentInput, students: List<CreateStudentInput>, actorId: String, actorName: String): Result<BatchRegisterResult> = try {
        require(students.isNotEmpty()) { "At least one student is required" }
        val params = buildJsonObject {
            put("p_parent", buildJsonObject {
                put("first_name", parent.firstName)
                put("last_name", parent.lastName)
                put("phone", parent.phone)
                parent.email?.let { put("email", it) }
                parent.occupation?.let { put("occupation", it) }
                parent.address?.let { put("address", it) }
                parent.transportDestination?.let { put("transport_destination", it) }
                put("preferred_language", parent.preferredLanguage)
            })
            put("p_students", kotlinx.serialization.json.JsonArray(students.map { s ->
                buildJsonObject {
                    put("first_name", s.firstName)
                    put("last_name", s.lastName)
                    put("gender", s.gender)
                    put("birth_date", s.birthDate)
                    put("level", s.level)
                    put("grade_level", s.gradeLevel)
                    s.classId?.let { put("class_id", it) }
                    s.medicalNotes?.let { put("medical_notes", it) }
                }
            }))
        }
        val response = provider.postgrest.rpc("batch_register_family", params)
            .decodeAs<BatchRegisterResponse>()
        val parentRow = fetchParentById(response.parentId) ?: return Result.Err(Errors.notFound("Parent ${response.parentId} not found after batch register"))
        val studentRows = fetchBy("parent_id", response.parentId)
        auditRepository.log(AuditLogInput(
            action = AuditActions.BATCH_REGISTER,
            entityType = "parent",
            entityId = response.parentId,
            afterJson = """{"student_count":${studentRows.size},"activation_code":"${response.activationCode ?: ""}"}""",
            note = "Batch registration from Android app",
        ))
        Result.Ok(BatchRegisterResult(parent = parentRow, students = studentRows, activationCode = response.activationCode))
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun promoteStudents(academicYear: String, decisions: List<PromotionDecision>, actorId: String, actorName: String): Result<Unit> = try {
        val params = buildJsonObject {
            put("p_academic_year", academicYear)
            put("p_decisions", kotlinx.serialization.json.JsonArray(decisions.map { d ->
                buildJsonObject {
                    put("student_id", d.studentId)
                    put("decision", d.decision)
                    d.note?.let { put("note", it) }
                }
            }))
        }
        provider.postgrest.rpc("promote_students", params)
        decisions.forEach { d ->
            auditRepository.log(AuditLogInput(
                action = AuditActions.STUDENT_PROMOTE,
                entityType = "student",
                entityId = d.studentId,
                afterJson = """{"decision":"${d.decision}","year":"$academicYear"}""",
                note = "Promotion from Android app",
            ))
        }
        Result.Ok(Unit)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    private suspend fun fetchAll(): List<Student> = try {
        provider.postgrest.from("students")
            .select { order("last_name", Order.ASCENDING); limit(500) }
            .decodeList<StudentDto>()
            .map { it.toDomain() }
    } catch (e: Exception) { emptyList() }

    private suspend fun fetchBy(column: String, value: String): List<Student> = try {
        provider.postgrest.from("students")
            .select { filter { eq(column, value) }; order("last_name", Order.ASCENDING) }
            .decodeList<StudentDto>()
            .map { it.toDomain() }
    } catch (e: Exception) { emptyList() }

    private suspend fun fetchParentById(id: String): Parent? = try {
        provider.postgrest.from("parents")
            .select { filter { eq("id", id) } }
            .decodeList<SupabaseParentRepository.ParentDto>()
            .firstOrNull()
            ?.toDomain()
    } catch (e: Exception) { null }

    @Serializable
    data class StudentDto(
        val id: String, val tenantId: String, val code: String,
        val parentId: String, val firstName: String, val lastName: String,
        val gender: String, val birthDate: String, val enrollmentDate: String,
        val level: String, val gradeLevel: String,
        val classId: String? = null, val photoUrl: String? = null,
        val medicalNotes: String? = null, val status: String = "active",
        val createdAt: String, val updatedAt: String,
    ) {
        fun toDomain() = Student(
            id = id, tenantId = tenantId, code = code, parentId = parentId,
            firstName = firstName, lastName = lastName, gender = gender,
            birthDate = birthDate, enrollmentDate = enrollmentDate,
            level = level, gradeLevel = gradeLevel, classId = classId,
            photoUrl = photoUrl, medicalNotes = medicalNotes, status = status,
            createdAt = createdAt, updatedAt = updatedAt,
        )
    }

    @Serializable
    data class StudentInsertDto(
        val parentId: String,
        val firstName: String, val lastName: String,
        val gender: String, val birthDate: String,
        val level: String, val gradeLevel: String,
        val classId: String? = null, val medicalNotes: String? = null,
    )

    @Serializable
    data class BatchRegisterResponse(
        val parentId: String,
        val activationCode: String? = null,
        val studentCount: Int = 0,
    )
}
