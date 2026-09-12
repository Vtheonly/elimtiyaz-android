package com.example.ui.features.crm

import java.util.UUID

/**
 * Vault §04.03 — one repeatable child block of the Dynamic Batch Registration
 * workflow (1..N children, no upper bound).
 *
 * T-320 (55th session): [id] added as a stable Compose key for the tabbed
 * child manager. CALC-001 note: the previous conversation's
 * previousGradeLevel / previousRank fields are deliberately ABSENT — the
 * discount rules they fed (passage_palier, highest_average) never existed at
 * the school and were deleted (see core/DiscountEngine.kt); shipping the
 * fields would promise reductions that can never apply.
 */
data class ChildFormState(
    val id: String = UUID.randomUUID().toString(),
    val firstName: String = "",
    val lastName: String = "",
    val birthDate: String = "",
    val gradeLevel: String = "",
    /** M | F | unspecified (legacy default: blank). */
    val gender: String = "",
    /** Optional class assignment for the chosen level (vault: Assigned Class). */
    val classId: String? = null,
    /** tranches | full_annual — drives the canonical discount engine + charge split. */
    val paymentPlan: String = "tranches",
    /** Optional medical / special notes (vault: medical notes on the student). */
    val medicalNotes: String = "",
)
