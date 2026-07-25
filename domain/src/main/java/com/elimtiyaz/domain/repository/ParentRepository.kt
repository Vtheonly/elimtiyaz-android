package com.elimtiyaz.domain.repository

import com.elimtiyaz.core.common.Result
import com.elimtiyaz.domain.model.BatchRegistrationInput
import com.elimtiyaz.domain.model.BatchRegistrationResult
import com.elimtiyaz.domain.model.CreateParentInput
import com.elimtiyaz.domain.model.CreateStudentInput
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.Student
import com.elimtiyaz.domain.model.UpdateParentInput
import kotlinx.coroutines.flow.Flow

interface ParentRepository {
    fun parents(): Flow<Result<List<Parent>>>
    fun parent(id: String): Flow<Result<Parent>>
    fun search(query: String): Flow<Result<List<Parent>>>
    suspend fun createParent(input: CreateParentInput): Result<Parent>
    suspend fun updateParent(id: String, input: UpdateParentInput): Result<Parent>
    suspend fun deleteParent(id: String): Result<Unit>
}

interface StudentRepository {
    fun students(): Flow<Result<List<Student>>>
    fun studentsByParent(parentId: String): Flow<Result<List<Student>>>
    fun studentsByClass(classId: String): Flow<Result<List<Student>>>
    fun student(id: String): Flow<Result<Student>>
    fun search(query: String): Flow<Result<List<Student>>>
    suspend fun createStudent(input: CreateStudentInput): Result<Student>
    suspend fun updateStudent(id: String, firstName: String?, lastName: String?, classId: String?, medicalNotes: String?): Result<Student>
    suspend fun deleteStudent(id: String): Result<Unit>
    suspend fun batchRegister(input: BatchRegistrationInput): Result<BatchRegistrationResult>
    suspend fun promote(studentIds: List<String>, academicYear: String): Result<List<Student>>
}
