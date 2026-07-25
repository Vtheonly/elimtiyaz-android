package com.elimtiyaz.domain.model

import kotlinx.serialization.Serializable

/**
 * Parent entity — the master plan §04.01 mandates a parent-first dependency
 * (a student CANNOT exist without a parent). One parent can have unlimited
 * children (1→N), replacing the deprecated four-child limit.
 */
@Serializable
data class Parent(
    val id: String,
    val tenantId: String,
    val code: String,                  // PAR-2025-A4F9
    val firstName: String,
    val lastName: String,
    val gender: Gender = Gender.Unspecified,
    val phone: String,
    val whatsapp: String? = null,
    val email: String? = null,
    val occupation: String? = null,
    val address: String? = null,
    val cityTier: String? = null,      // t1 / t2 / t3 — drives transport fee
    val preferredLanguage: String = "fr",
    val avatarUrl: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val students: List<Student> = emptyList(),
)

@Serializable
data class CreateParentInput(
    val firstName: String,
    val lastName: String,
    val gender: Gender = Gender.Unspecified,
    val phone: String,
    val whatsapp: String? = null,
    val email: String? = null,
    val occupation: String? = null,
    val address: String? = null,
    val cityTier: String? = null,
    val preferredLanguage: String = "fr",
)

@Serializable
data class UpdateParentInput(
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val whatsapp: String? = null,
    val email: String? = null,
    val occupation: String? = null,
    val address: String? = null,
    val cityTier: String? = null,
    val preferredLanguage: String? = null,
)

@Serializable
enum class Gender { Male, Female, Unspecified }
