package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Tenant pricing config — registration fee, late penalty, second-apron fee,
 * and the list of active discounts (sibling_fixed, passage_palier, etc.).
 * Grade-level-specific tuition is in [GradeLevelTuition].
 *
 * Mirrors desktop `domain/model/pricing.ts:PricingConfig`.
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
    /** Active discounts (sibling_fixed, passage_palier, seniority_5y, etc.). Empty by default. */
    val discounts: List<PricingDiscount> = emptyList(),
)

/**
 * Single discount entry. Mirrors desktop `PricingEntry` (filtered to discounts).
 *
 * The `amount` field uses the signed-amount convention:
 *   - **Percentage discounts** (e.g. seniority_5y −5%): `amount = -5` (the
 *     caller multiplies by the base amount to get the reduction).
 *   - **Fixed-amount discounts** (e.g. sibling_fixed −5,000 DZD per additional
 *     child): `amount = -500_000` (centimes, negative).
 *
 * The canonical `code` drives UI grouping + ledger metadata. The 5 canonical
 * discount codes (per plan §06.04 + desktop `DiscountCode`):
 *   - `passage_palier`  — −10,000 DZD fixed  (when a student passes to the next palier)
 *   - `seniority_5y`    — −5%                (5+ years at the school)
 *   - `full_annual`     — −10%               (paid in full before June 30)
 *   - `highest_average` — −10%               (highest class average)
 *   - `sibling_fixed`   — −5,000 DZD per additional child
 */
@Serializable
data class PricingDiscount(
    val id: String,
    val tenantId: String,
    val code: String,
    val label: String,
    /** Negative number: percentage (e.g. -5 = −5%) or centimes (e.g. -500_000 = −5,000 DZD). */
    val amount: Long,
    val discountType: String = "fixed_amount", // "fixed_amount" | "percentage"
)
