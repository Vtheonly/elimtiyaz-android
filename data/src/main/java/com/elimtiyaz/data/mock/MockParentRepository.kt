package com.elimtiyaz.data.mock

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.model.BatchRegistrationInput
import com.elimtiyaz.domain.model.BatchRegistrationResult
import com.elimtiyaz.domain.model.CreateParentInput
import com.elimtiyaz.domain.model.CreateStudentInput
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.model.UpdateParentInput
import com.elimtiyaz.domain.repository.ParentRepository
import com.elimtiyaz.domain.repository.StudentRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Mock delay range (200-500ms) — simulates network latency for realism. */
private fun mockDelay() = delay((200L..500L).random())

/**
 * Mock [ParentRepository] — fully in-memory, seeded from [MockData.parents].
 * Mutations update the underlying [MutableStateFlow] so collectors see the
 * change immediately.
 */
@Singleton
class MockParentRepository @Inject constructor() : ParentRepository {

    private val log = Logger.withTag("Mock.Parent")
    private val state = MutableStateFlow(MockData.parents)

    /** Stream all parents. */
    override fun parents(): Flow<Result<List<Parent>>> =
        state.map { Result.success(it) }

    /** Stream a single parent (with their students). */
    override fun parent(id: String): Flow<Result<Parent>> = state.map { parents ->
        val p = parents.firstOrNull { it.id == id }
            ?: return@map Result.failure("Parent $id introuvable.")
        val students = MockData.students.filter { it.parentId == id }
        Result.success(p.copy(students = students))
    }

    /** Stream parents whose code/name/phone matches the query. */
    override fun search(query: String): Flow<Result<List<Parent>>> = state.map { parents ->
        val q = query.trim().lowercase()
        Result.success(parents.filter { p ->
            p.firstName.lowercase().contains(q) ||
                p.lastName.lowercase().contains(q) ||
                p.code.lowercase().contains(q) ||
                p.phone.lowercase().contains(q)
        })
    }

    /** Create a parent — generates a code and adds it to the state. */
    override suspend fun createParent(input: CreateParentInput): Result<Parent> {
        mockDelay()
        val nowIso = Clock.System.now().toString()
        val year = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        val id = "p-new-${UUID.randomUUID().toString().take(6)}"
        val code = "PAR-$year-${id.takeLast(4).uppercase()}"
        val parent = Parent(
            id = id, tenantId = MockData.TENANT_ID, code = code, firstName = input.firstName,
            lastName = input.lastName, gender = input.gender, phone = input.phone, whatsapp = input.whatsapp,
            email = input.email, occupation = input.occupation, address = input.address,
            cityTier = input.cityTier, preferredLanguage = input.preferredLanguage,
            avatarUrl = null, createdAt = nowIso, updatedAt = nowIso,
        )
        state.value = state.value + parent
        log.i { "Created parent $code" }
        return Result.success(parent)
    }

    /** Update a parent's mutable fields. */
    override suspend fun updateParent(id: String, input: UpdateParentInput): Result<Parent> {
        mockDelay()
        val updated = state.value.map { p ->
            if (p.id != id) p else p.copy(
                firstName = input.firstName ?: p.firstName,
                lastName = input.lastName ?: p.lastName,
                phone = input.phone ?: p.phone,
                whatsapp = input.whatsapp ?: p.whatsapp,
                email = input.email ?: p.email,
                occupation = input.occupation ?: p.occupation,
                address = input.address ?: p.address,
                cityTier = input.cityTier ?: p.cityTier,
                preferredLanguage = input.preferredLanguage ?: p.preferredLanguage,
                updatedAt = Clock.System.now().toString(),
            )
        }
        state.value = updated
        val result = updated.firstOrNull { it.id == id }
            ?: return Result.failure("Parent $id introuvable.")
        log.i { "Updated parent $id" }
        return Result.success(result)
    }

    /** Delete a parent. */
    override suspend fun deleteParent(id: String): Result<Unit> {
        mockDelay()
        state.value = state.value.filterNot { it.id == id }
        log.i { "Deleted parent $id" }
        return Result.success(Unit)
    }
}

/**
 * Mock [StudentRepository] — in-memory, seeded from [MockData.students].
 * Joins the parent via the shared [MockData.parents] list.
 */
@Singleton
class MockStudentRepository @Inject constructor() : StudentRepository {

    private val log = Logger.withTag("Mock.Student")
    private val state = MutableStateFlow(MockData.students)

    /** Helper: look up the parent from the parent repository's current state. */
    private fun parentFor(student: Student): Parent? =
        MockData.parents.firstOrNull { it.id == student.parentId }

    /** Stream all students. */
    override fun students(): Flow<Result<List<Student>>> = state.map { students ->
        Result.success(students.map { it.copy(parent = parentFor(it)) })
    }

    /** Stream students by parent id. */
    override fun studentsByParent(parentId: String): Flow<Result<List<Student>>> = state.map { students ->
        val parent = MockData.parents.firstOrNull { it.id == parentId }
        Result.success(students.filter { it.parentId == parentId }.map { it.copy(parent = parent) })
    }

    /** Stream students by class id. */
    override fun studentsByClass(classId: String): Flow<Result<List<Student>>> = state.map { students ->
        Result.success(students.filter { it.classId == classId }.map { it.copy(parent = parentFor(it)) })
    }

    /** Stream a single student. */
    override fun student(id: String): Flow<Result<Student>> = state.map { students ->
        val s = students.firstOrNull { it.id == id }
            ?: return@map Result.failure("Élève $id introuvable.")
        Result.success(s.copy(parent = parentFor(s)))
    }

    /** Stream students whose code/name matches the query. */
    override fun search(query: String): Flow<Result<List<Student>>> = state.map { students ->
        val q = query.trim().lowercase()
        Result.success(students.filter { s ->
            s.firstName.lowercase().contains(q) ||
                s.lastName.lowercase().contains(q) ||
                s.code.lowercase().contains(q)
        })
    }

    /** Create a student. */
    override suspend fun createStudent(input: CreateStudentInput): Result<Student> {
        mockDelay()
        val nowIso = Clock.System.now().toString()
        val year = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year
        val id = "st-new-${UUID.randomUUID().toString().take(6)}"
        val code = "ELV-$year-${(100..99999).random().toString().padStart(6, '0')}"
        val student = Student(
            id = id, tenantId = MockData.TENANT_ID, code = code, parentId = input.parentId,
            firstName = input.firstName, lastName = input.lastName, gender = input.gender,
            birthDate = input.birthDate, enrollmentDate = nowIso, level = input.level,
            gradeYear = input.gradeYear, classId = input.classId, photoUrl = null,
            medicalNotes = input.medicalNotes, transportTier = input.transportTier,
            createdAt = nowIso, updatedAt = nowIso,
        )
        state.value = state.value + student
        log.i { "Created student $code" }
        return Result.success(student)
    }

    /** Update a student's mutable fields. */
    override suspend fun updateStudent(
        id: String, firstName: String?, lastName: String?,
        classId: String?, medicalNotes: String?,
    ): Result<Student> {
        mockDelay()
        val updated = state.value.map { s ->
            if (s.id != id) s else s.copy(
                firstName = firstName ?: s.firstName,
                lastName = lastName ?: s.lastName,
                classId = classId ?: s.classId,
                medicalNotes = medicalNotes ?: s.medicalNotes,
                updatedAt = Clock.System.now().toString(),
            )
        }
        state.value = updated
        val result = updated.firstOrNull { it.id == id }
            ?: return Result.failure("Élève $id introuvable.")
        log.i { "Updated student $id" }
        return Result.success(result)
    }

    /** Delete a student. */
    override suspend fun deleteStudent(id: String): Result<Unit> {
        mockDelay()
        state.value = state.value.filterNot { it.id == id }
        log.i { "Deleted student $id" }
        return Result.success(Unit)
    }

    /** Atomically register a parent + N children. */
    override suspend fun batchRegister(input: BatchRegistrationInput): Result<BatchRegistrationResult> {
        mockDelay()
        val parentResult = createParent(input.parent)
        val parent = parentResult.getOrNull() ?: return parentResult as Result<BatchRegistrationResult>
        val students = input.students.map { input ->
            createStudent(input.copy(parentId = parent.id)).getOrNull()!!
        }
        log.i { "Batch-registered parent ${parent.code} with ${students.size} children" }
        return Result.success(BatchRegistrationResult(parent, students))
    }

    /** Promote students — increments their grade year (capped at level max). */
    override suspend fun promote(studentIds: List<String>, academicYear: String): Result<List<Student>> {
        mockDelay()
        val updated = state.value.map { s ->
            if (s.id !in studentIds) s else {
                val maxYear = when (s.level) {
                    "primaire" -> 5
                    "cem" -> 4
                    "lycee" -> 3
                    else -> 1
                }
                val nextYear = (s.gradeYear + 1).coerceAtMost(maxYear)
                s.copy(gradeYear = nextYear, updatedAt = Clock.System.now().toString())
            }
        }
        state.value = updated
        val result = updated.filter { it.id in studentIds }
        log.i { "Promoted ${result.size} students to $academicYear" }
        return Result.success(result)
    }
}
