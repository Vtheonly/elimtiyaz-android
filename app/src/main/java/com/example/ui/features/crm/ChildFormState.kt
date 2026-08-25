package com.example.ui.features.crm

/**
 * Vault §04.03 — one repeatable child block of the Dynamic Batch Registration
 * workflow (1..N children, no upper bound).
 */
data class ChildFormState(
    val firstName: String = "",
    val lastName: String = "",
    val birthDate: String = "",
    val gradeLevel: String = "",
    /** M | F (vault: Gender). Blank = unspecified (legacy default). */
    val gender: String = "",
    /** Optional class assignment for the chosen level (vault: Assigned Class). */
    val classId: String? = null,
    /** tranches | full_annual — drives the canonical discount engine + charge split. */
    val paymentPlan: String = "tranches",
    /** Optional medical / special notes (vault: medical notes on the student). */
    val medicalNotes: String = "",
)
