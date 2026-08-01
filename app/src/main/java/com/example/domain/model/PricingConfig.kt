package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Tenant pricing config — registration fee, late penalty, second-apron fee.
 * Grade-level-specific tuition is in [GradeLevelTuition].
 */
@Serializable
data class PricingConfig(
    val id: String,
    val tenantId: String,
    val isActive: Boolean,
    val registrationFee: Long,
    val latePenaltyPerDay: Long,
    val secondApronFee: Long,
    val updatedAt: String,
)
