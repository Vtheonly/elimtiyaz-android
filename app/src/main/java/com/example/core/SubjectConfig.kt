package com.example.core

/**
 * CANONICAL ENGINE MIRROR (Android) — verbatim Kotlin mirror of the desktop
 * canonical subject-configuration module (T-348 / MATIERE-500 / ADR-018).
 * Source: elimtiyaz-desktop/src/domain/calc/academics/subject-config.ts
 * (sha256 d902b8f0938c) — keep in lockstep with it and with the website port
 * (elimtiyaz-website/src/lib/canonical/subject-config.ts); the corpus
 * category `subject_configuration` pins the equivalence (ADR-002).
 */
// ─── Types ───────────────────────────────────────────────────────────────────

/** The component-weight recipe of a subject's term average (ADR-018). */
data class GradingRecipe(
    val devoir1: Double,
    val devoir2: Double,
    val examen: Double,
    /** Contrôle continu (المراقبة المستمرة) weight — 0 = excluded. */
    val cc: Double,
)

/** The DEFAULT recipe — bit-identical to (D1 + D2 + 2×Ex) / 4. */
val DEFAULT_GRADING_RECIPE = GradingRecipe(devoir1 = 1.0, devoir2 = 1.0, examen = 2.0, cc = 0.0)

/** The legacy subject-directory layer the resolver falls back to. */
data class LegacySubjectLayer(
    val id: String? = null,
    val code: String? = null,
    val coefficient: Double? = null,
    val passingGrade: Double? = null,
    val isExtracurricular: Boolean? = null,
)

/** A context-specific subject configuration row (migration 0094). */
data class SubjectConfiguration(
    val subjectId: String,
    val academicYearId: String,
    val academicLevelId: String,
    val direction: String = "general",
    val coefficient: Double,
    val subjectCode: String? = null,
    val passingGrade: Double? = null,
    val isExtracurricular: Boolean? = null,
    val gradingRecipe: GradingRecipe? = null,
    val isActive: Boolean = true,
)

/** What the resolver hands back — everything a surface needs, one source. */
data class ResolvedSubjectContext(
    val coefficient: Double,
    val subjectCode: String,
    val passingGrade: Double,
    val isExtracurricular: Boolean,
    val gradingRecipe: GradingRecipe,
    val source: String, // "configuration" | "legacy-subject" | "default"
)

/** The assessment-row snapshot (history is never re-resolved — ADR-018 §3). */
data class SubjectContextSnapshot(
    val coefficient: Double? = null,
    val coefficientDevoir1: Double? = null,
    val coefficientDevoir2: Double? = null,
    val coefficientExamen: Double? = null,
    val coefficientCc: Double? = null,
)

// ─── The ONE canonical resolution rule (ADR-018 §5) ─────────────────────────

/**
 * Resolution order:
 *   1. the assessment SNAPSHOT (the values in force at entry);
 *   2. the context CONFIGURATION row matching (subject, level, year) —
 *      exact direction first, then the 'general' row;
 *   3. the legacy `subjects` columns (the pre-0094 world);
 *   4. the hard default (coefficient 1, passing 10, recipe {1,1,2,0}).
 */
fun resolveSubjectConfiguration(
    subject: LegacySubjectLayer?,
    configurations: List<SubjectConfiguration>,
    academicLevelId: String? = null,
    academicYearId: String? = null,
    direction: String = "general",
    snapshot: SubjectContextSnapshot? = null,
): ResolvedSubjectContext {
    val matches = configurations.filter { c ->
        c.subjectId == subject?.id &&
            c.academicLevelId == academicLevelId &&
            c.academicYearId == academicYearId &&
            c.isActive
    }
    val config = matches.firstOrNull { it.direction == direction }
        ?: matches.firstOrNull { it.direction == "general" }

    val legacy = ResolvedSubjectContext(
        coefficient = subject?.coefficient ?: 1.0,
        subjectCode = subject?.code ?: "",
        passingGrade = subject?.passingGrade ?: 10.0,
        isExtracurricular = subject?.isExtracurricular ?: false,
        gradingRecipe = DEFAULT_GRADING_RECIPE,
        source = "default",
    )

    val resolved = if (config != null) {
        ResolvedSubjectContext(
            coefficient = config.coefficient,
            subjectCode = config.subjectCode ?: subject?.code ?: "",
            passingGrade = config.passingGrade ?: 10.0,
            isExtracurricular = config.isExtracurricular ?: false,
            gradingRecipe = config.gradingRecipe ?: DEFAULT_GRADING_RECIPE,
            source = "configuration",
        )
    } else if (subject != null) {
        legacy.copy(source = "legacy-subject")
    } else {
        legacy
    }

    val s = snapshot ?: return resolved
    val snapCoefficient = s.coefficient?.takeIf { it > 0 }
    val snapRecipe =
        if (s.coefficientDevoir1 == null && s.coefficientDevoir2 == null &&
            s.coefficientExamen == null && s.coefficientCc == null
        ) {
            resolved.gradingRecipe
        } else {
            GradingRecipe(
                devoir1 = s.coefficientDevoir1 ?: DEFAULT_GRADING_RECIPE.devoir1,
                devoir2 = s.coefficientDevoir2 ?: DEFAULT_GRADING_RECIPE.devoir2,
                examen = s.coefficientExamen ?: DEFAULT_GRADING_RECIPE.examen,
                cc = s.coefficientCc ?: DEFAULT_GRADING_RECIPE.cc,
            )
        }
    return resolved.copy(
        coefficient = snapCoefficient ?: resolved.coefficient,
        gradingRecipe = snapRecipe,
    )
}

// ─── The recipe-aware canonical engine ───────────────────────────────────────

/**
 * subject_average = Σ(mark × weight) / Σ(weight) over the POSITIVE-weight
 * components; every one of them must be present, else null (the T-336
 * honesty rule). Bit-parity: centi-scaled integer math (half-up) —
 * identical to the migration-0094 SQL trigger, the desktop engine and the
 * website port. With the DEFAULT recipe this is exactly (D1 + D2 + 2×Ex)/4.
 */
fun computeSubjectAverageFromRecipe(
    devoir1: Double?,
    devoir2: Double?,
    examen: Double?,
    cc: Double?,
    recipe: GradingRecipe = DEFAULT_GRADING_RECIPE,
): Double? {
    if (devoir1 == null && devoir2 == null && examen == null && cc == null) return null

    val w1 = Math.round(recipe.devoir1 * 100.0).toLong()
    val w2 = Math.round(recipe.devoir2 * 100.0).toLong()
    val w3 = Math.round(recipe.examen * 100.0).toLong()
    val wcc = Math.round(recipe.cc * 100.0).toLong()

    // A positive-weight component is REQUIRED.
    if (w1 > 0 && devoir1 == null) return null
    if (w2 > 0 && devoir2 == null) return null
    if (w3 > 0 && examen == null) return null
    if (wcc > 0 && cc == null) return null

    var numeratorCents = 0L
    var denominatorCents = 0L
    if (w1 > 0) {
        numeratorCents += Math.round(devoir1!! * 100.0) * w1
        denominatorCents += w1
    }
    if (w2 > 0) {
        numeratorCents += Math.round(devoir2!! * 100.0) * w2
        denominatorCents += w2
    }
    if (w3 > 0) {
        numeratorCents += Math.round(examen!! * 100.0) * w3
        denominatorCents += w3
    }
    if (wcc > 0) {
        numeratorCents += Math.round(cc!! * 100.0) * wcc
        denominatorCents += wcc
    }
    if (denominatorCents == 0L) return null
    val avgCents = Math.round(numeratorCents.toDouble() / denominatorCents.toDouble())
    return avgCents / 100.0
}
