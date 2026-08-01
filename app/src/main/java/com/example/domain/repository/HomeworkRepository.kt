package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.Homework
import kotlinx.coroutines.flow.Flow

/** Homework repository contract. */
interface HomeworkRepository {
    fun observeForClass(classId: String): Flow<List<Homework>>
    fun observeForTeacher(teacherId: String): Flow<List<Homework>>
    suspend fun push(input: PushHomeworkInput, actorId: String, actorName: String): Result<Homework>
}

/** Input payload for [HomeworkRepository.push]. */
data class PushHomeworkInput(
    val classId: String, val subjectId: String,
    val title: String, val description: String, val dueDate: String,
    val attachments: List<String> = emptyList(), val academicYear: String,
)
