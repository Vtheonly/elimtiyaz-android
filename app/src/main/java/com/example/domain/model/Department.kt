package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Organizational department — used for personnel grouping and reporting.
 */
@Serializable
data class Department(
    val id: String,
    val tenantId: String,
    val name: String,
    val description: String? = null,
    val headPersonnelId: String? = null,
    val parentDepartmentId: String? = null,
    val colorHex: String? = null,
    val archivedAt: String? = null,
)
