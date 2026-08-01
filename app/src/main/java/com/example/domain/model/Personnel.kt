package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Personnel (staff) domain entity.
 */
@Serializable
data class Personnel(
    val id: String,
    val tenantId: String,
    val userId: String? = null,
    val firstName: String,
    val lastName: String,
    val staffCategory: String,           // teacher | administration | support | maintenance | driver | buyer | warehouse | worker
    val roleId: String,                  // Role.code
    val departmentId: String? = null,
    val position: String,
    val phone: String,
    val email: String? = null,
    val hireDate: String,
    val terminationDate: String? = null,
    val salary: Long? = null,
    val status: String = "active",
    val avatarUrl: String? = null,
    val weeklyHoursTarget: Int = 0,
    val weeklyHoursLogged: Int = 0,
) {
    val fullName: String get() = "$firstName $lastName"
}
