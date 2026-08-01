package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.Parent
import com.example.domain.model.Student
import kotlinx.coroutines.flow.Flow

/** Student entity repository contract. */
interface StudentRepository {
    fun observe(): Flow<List<Student>>
    fun observeByParent(parentId: String): Flow<List<Student>>
    fun observeByClass(classId: String): Flow<List<Student>>
    fun observeById(id: String): Flow<Student?>
    fun search(query: String): Flow<List<Student>>
    suspend fun createStudent(input: CreateStudentInput, actorId: String, actorName: String): Result<Student>
    suspend fun updateStudent(id: String, input: UpdateStudentInput, actorId: String, actorName: String): Result<Student>
    suspend fun batchRegister(parent: CreateParentInput, students: List<CreateStudentInput>, actorId: String, actorName: String): Result<BatchRegisterResult>
    suspend fun promoteStudents(academicYear: String, decisions: List<PromotionDecision>, actorId: String, actorName: String): Result<Unit>
}

/** Input payload for [StudentRepository.createStudent]. */
data class CreateStudentInput(
    val firstName: String, val lastName: String, val gender: String,
    val birthDate: String, val level: String, val gradeLevel: String,
    val classId: String? = null, val parentId: String? = null,
    val medicalNotes: String? = null,
)

/** Input payload for [StudentRepository.updateStudent]. */
data class UpdateStudentInput(
    val firstName: String? = null, val lastName: String? = null,
    val classId: String? = null, val status: String? = null,
    val medicalNotes: String? = null,
)

/** Result of [StudentRepository.batchRegister] — the created parent + students + activation code. */
data class BatchRegisterResult(val parent: Parent, val students: List<Student>, val activationCode: String?)

/** Single promotion decision for [StudentRepository.promoteStudents]. */
data class PromotionDecision(val studentId: String, val decision: String, val note: String? = null)
