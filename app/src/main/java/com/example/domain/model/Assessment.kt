package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Subject-grade assessment for one student in one term — composed of two
 * devoirs and one examen, plus a per-row snapshot of the SUBJECT's
 * per-component coefficients used to derive [subjectAverage].
 *
 * ITERATION-2 (vault §06.02 — "we still don't know the actual formula"):
 * The previous build hard-coded the (D1 + D2 + 2×Ex) / 4 recipe. Until the
 * institution confirms its real formula, each component (Devoir 1, Devoir 2,
 * Examen) carries its OWN coefficient, snapshotted from the Subject at
 * grade-entry time. Defaults (1, 1, 2) preserve the historical arithmetic
 * bit-identically when no override is configured. The canonical Android
 * formula is now:
 *     subjectAverage = (D1×c1 + D2×c2 + Ex×c3) / (c1 + c2 + c3)
 * computed via integer-scaled centime math (see
 * [com.example.core.computeSubjectAverage]).
 *
 * Snapshotting the per-component coefficients onto the assessment row at
 * entry time preserves archival integrity (vault §04.07 "append-only"):
 * when an admin later edits the subject's coefficients, only the CURRENT
 * academic year's assessment rows are recomputed; archived years keep
 * their original coefficients and original subjectAverage.
 */
@Serializable
data class Assessment(
    val id: String,
    val tenantId: String,
    val studentId: String,
    val subjectId: String,
    val classId: String,
    val term: String,                    // T1 | T2 | T3
    val academicYear: String,
    val devoir1: Double? = null,
    val devoir2: Double? = null,
    val examen: Double? = null,
    val subjectAverage: Double? = null,
    val coefficient: Double,
    /** Canonical rule (desktop academic.ts + SQL fn_calculate_student_term_gpa):
     *  extracurricular modules are EXCLUDED from the official GPA. */
    val isExtracurricular: Boolean = false,
    val enteredBy: String,
    val enteredAt: String,
    // Vault §06.02 — per-COMPONENT coefficient snapshot, copied from the
    // Subject at grade-entry time. Defaults preserve the historical
    // (1, 1, 2) recipe when an assessment row predates this field (legacy
    // rows created before the migration), so cross-year GPAs remain
    // bit-identical to the previous build.
    val coefficientDevoir1: Double = 1.0,
    val coefficientDevoir2: Double = 1.0,
    val coefficientExamen: Double = 2.0,
)
