package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Subject domain entity — a teachable subject with coefficient and level scope.
 */
@Serializable
data class Subject(
    val id: String,
    val tenantId: String,
    val name: String,
    val nameAr: String? = null,
    val code: String,
    val level: String,
    val coefficient: Double,
    val isExtracurricular: Boolean,
    val passingGrade: Double = 10.0,
)
