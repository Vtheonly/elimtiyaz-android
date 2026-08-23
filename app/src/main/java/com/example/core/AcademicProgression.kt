package com.example.core

/**
 * Algerian National Education grade progression — canonical port of the
 * desktop `src/domain/calc/academics/promotion.ts` (`getNextGradeProgression`).
 *
 * Primary (5 yrs): prescolaire_1 -> prescolaire_2 -> 1ap -> 2ap -> 3ap -> 4ap -> 5ap -> 1am
 * CEM (4 yrs)    : 1am -> 2am -> 3am -> 4am -> 1ere_annee
 * Lycée (3 yrs)  : 1ere_annee -> 2eme_annee -> 3eme_annee -> GRADUATED
 *
 * TIER 4 FIX — Android's `promoteStudents` was a stub (it bumped `updatedAt`
 * only). Shared-domain state transitions (grade level / graduation status)
 * must follow the SAME canonical ladder as the desktop or synced student
 * state diverges after every promotion cycle.
 */
data class GradeProgression(
    val nextGradeCode: String?,
    val nextLevel: String?,
    val nextGradeYear: Int?,
    val nextCycle: String?,
    val isGraduation: Boolean,
)

private val PROGRESSION: Map<String, GradeProgression> = mapOf(
    "prescolaire_1" to GradeProgression("prescolaire_2", "primaire", 0, "prescolaire", false),
    "prescolaire_2" to GradeProgression("1ap", "primaire", 1, "primaire", false),
    "1ap" to GradeProgression("2ap", "primaire", 2, "primaire", false),
    "2ap" to GradeProgression("3ap", "primaire", 3, "primaire", false),
    "3ap" to GradeProgression("4ap", "primaire", 4, "primaire", false),
    "4ap" to GradeProgression("5ap", "primaire", 5, "primaire", false),
    // Cycle Transition: Primary Grade 5 -> Middle School (CEM Year 1)
    "5ap" to GradeProgression("1am", "cem", 1, "cem", false),
    "1am" to GradeProgression("2am", "cem", 2, "cem", false),
    "2am" to GradeProgression("3am", "cem", 3, "cem", false),
    "3am" to GradeProgression("4am", "cem", 4, "cem", false),
    // Cycle Transition: Middle School Year 4 -> High School (Lycée Year 1)
    "4am" to GradeProgression("1ere_annee", "lycee", 1, "lycee", false),
    "1ere_annee" to GradeProgression("2eme_annee", "lycee", 2, "lycee", false),
    "2eme_annee" to GradeProgression("3eme_annee", "lycee", 3, "lycee", false),
    // Final Graduation from Scolarité
    "3eme_annee" to GradeProgression(null, null, null, null, true),
)

fun getNextGradeProgression(current: String): GradeProgression =
    PROGRESSION[current] ?: GradeProgression(null, null, null, null, false)

/**
 * The canonical promotion decision values (mirrors the desktop
 * `PromotionDecision` union + the promotion review queue semantics).
 */
object PromotionDecisions {
    const val PROMOTED = "promoted"
    const val REPEATED = "repeated"
    const val GRADUATED = "graduated"
}
