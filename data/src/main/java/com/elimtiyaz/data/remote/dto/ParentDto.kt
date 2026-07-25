package com.elimtiyaz.data.remote.dto

import com.elimtiyaz.domain.model.CreateParentInput
import com.elimtiyaz.domain.model.Gender
import com.elimtiyaz.domain.model.Parent
import com.elimtiyaz.domain.model.UpdateParentInput
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire DTO for the `parents` Supabase table. Field names mirror Postgres
 * columns (snake_case) so the kotlinx.serialization JSON parser maps them
 * without any custom strategy.
 */
@Serializable
data class ParentDto(
    val id: String,
    @SerialName("tenant_id") val tenantId: String,
    val code: String,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    val gender: Gender = Gender.Unspecified,
    val phone: String,
    val whatsapp: String? = null,
    val email: String? = null,
    val occupation: String? = null,
    val address: String? = null,
    @SerialName("city_tier") val cityTier: String? = null,
    @SerialName("preferred_language") val preferredLanguage: String = "fr",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
) {
    /** Convert this wire DTO to the domain [Parent] model. */
    fun toDomain(students: List<com.elimtiyaz.domain.model.Student> = emptyList()): Parent = Parent(
        id = id,
        tenantId = tenantId,
        code = code,
        firstName = firstName,
        lastName = lastName,
        gender = gender,
        phone = phone,
        whatsapp = whatsapp,
        email = email,
        occupation = occupation,
        address = address,
        cityTier = cityTier,
        preferredLanguage = preferredLanguage,
        avatarUrl = avatarUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
        students = students,
    )

    companion object {
        /** Build a DTO from a domain [Parent]. */
        fun fromDomain(p: Parent): ParentDto = ParentDto(
            id = p.id,
            tenantId = p.tenantId,
            code = p.code,
            firstName = p.firstName,
            lastName = p.lastName,
            gender = p.gender,
            phone = p.phone,
            whatsapp = p.whatsapp,
            email = p.email,
            occupation = p.occupation,
            address = p.address,
            cityTier = p.cityTier,
            preferredLanguage = p.preferredLanguage,
            avatarUrl = p.avatarUrl,
            createdAt = p.createdAt,
            updatedAt = p.updatedAt,
        )

        /** Build a DTO from a [CreateParentInput] using the supplied identifiers. */
        fun fromCreate(input: CreateParentInput, id: String, tenantId: String, code: String, nowIso: String): ParentDto =
            ParentDto(
                id = id,
                tenantId = tenantId,
                code = code,
                firstName = input.firstName,
                lastName = input.lastName,
                gender = input.gender,
                phone = input.phone,
                whatsapp = input.whatsapp,
                email = input.email,
                occupation = input.occupation,
                address = input.address,
                cityTier = input.cityTier,
                preferredLanguage = input.preferredLanguage,
                avatarUrl = null,
                createdAt = nowIso,
                updatedAt = nowIso,
            )
    }
}

/** JSON-serialisable payload used by the `updateParent` Supabase call. */
@Serializable
data class UpdateParentDto(
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    val phone: String? = null,
    val whatsapp: String? = null,
    val email: String? = null,
    val occupation: String? = null,
    val address: String? = null,
    @SerialName("city_tier") val cityTier: String? = null,
    @SerialName("preferred_language") val preferredLanguage: String? = null,
    @SerialName("updated_at") val updatedAt: String,
) {
    companion object {
        /** Build a partial-update DTO from an [UpdateParentInput]. */
        fun fromInput(input: UpdateParentInput, nowIso: String): UpdateParentDto = UpdateParentDto(
            firstName = input.firstName,
            lastName = input.lastName,
            phone = input.phone,
            whatsapp = input.whatsapp,
            email = input.email,
            occupation = input.occupation,
            address = input.address,
            cityTier = input.cityTier,
            preferredLanguage = input.preferredLanguage,
            updatedAt = nowIso,
        )
    }
}
