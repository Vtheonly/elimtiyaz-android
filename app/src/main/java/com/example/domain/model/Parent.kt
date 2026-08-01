package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Parent (guardian) domain entity — mirrors desktop `src/domain/model/parent.ts`.
 *
 * Amounts are never stored on Parent; financial state lives on
 * [com.example.domain.model.Installment] / [com.example.domain.model.Payment]
 * and is aggregated via the ledger.
 */
@Serializable
data class Parent(
    val id: String,
    val tenantId: String,
    val code: String,                    // PAR-{year}-{4-char}
    val firstName: String,
    val lastName: String,
    val phone: String,
    val whatsapp: String? = null,
    val email: String? = null,
    val occupation: String? = null,
    val address: String? = null,
    val transportDestination: String? = null,
    val preferredLanguage: String = "fr",
    val avatarUrl: String? = null,
    val createdAt: String,
    val updatedAt: String,
) {
    val fullName: String get() = "$firstName $lastName"
}
