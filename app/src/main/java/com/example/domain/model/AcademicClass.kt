package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Academic class (group of students for a given level + grade year).
 */
@Serializable
data class AcademicClass(
    val id: String,
    val tenantId: String,
    val name: String,
    val level: String,
    val gradeYear: Int,
    val homeroomTeacherId: String? = null,
    val homeroomTeacherName: String? = null,
    val room: String? = null,
    val capacity: Int,
    val enrolledCount: Int,
    val academicYear: String,
)
