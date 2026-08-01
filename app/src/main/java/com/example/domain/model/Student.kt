package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Student domain entity — mirrors desktop `src/domain/model/student.ts`.
 *
 * `gradeLevel` is one of 14 canonical codes (`prescolaire_1` ... `3eme_annee`).
 * `status` is the student lifecycle state.
 */
@Serializable
data class Student(
    val id: String,
    val tenantId: String,
    val code: String,                    // ELV-{year}-{6-digit}
    val parentId: String,
    val firstName: String,
    val lastName: String,
    val gender: String,
    val birthDate: String,
    val enrollmentDate: String,
    val level: String,                   // primaire | cem | lycee
    val gradeLevel: String,              // 14 codes: prescolaire_1 ... 3eme_annee
    val classId: String? = null,
    val photoUrl: String? = null,
    val medicalNotes: String? = null,
    val status: String = "active",       // active | graduated | transferred | suspended | withdrawn
    val createdAt: String,
    val updatedAt: String,
) {
    val fullName: String get() = "$firstName $lastName"
}
