package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Homework pushed by a teacher to a class.
 */
@Serializable
data class Homework(
    val id: String,
    val tenantId: String,
    val classId: String,
    val subjectId: String,
    val subjectName: String,
    val teacherId: String,
    val teacherName: String,
    val title: String,
    val description: String,
    val dueDate: String,
    val attachments: List<String> = emptyList(),
    val academicYear: String,
    val createdAt: String,
    val pushedAt: String? = null,
    val acknowledgedCount: Int = 0,
)
