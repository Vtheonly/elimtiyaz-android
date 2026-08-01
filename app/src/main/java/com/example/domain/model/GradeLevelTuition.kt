package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Per-grade-level tuition schedule — annual amount + 3 tranche splits.
 * Linked to a [PricingConfig] via `pricingConfigId`.
 */
@Serializable
data class GradeLevelTuition(
    val id: String,
    val pricingConfigId: String,
    val gradeLevel: String,
    val annualAmount: Long,
    val tranche1: Long,
    val tranche2: Long,
    val tranche3: Long,
)
