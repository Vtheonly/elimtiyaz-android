package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Subject domain entity — a teachable subject with coefficient and level scope.
 *
 * Each subject carries FOUR canonical weights:
 *  - [coefficient]            : the SUBJECT-level coefficient used to weight
 *                              this subject's average inside the overall GPA
 *                              (vault §05.06 + §06.03). The formula is
 *                              Σ(subject_avg × coefficient) / Σ(coefficient).
 *  - [coefficientDevoir1]     : per-COMPONENT coefficient for Devoir 1.
 *  - [coefficientDevoir2]     : per-COMPONENT coefficient for Devoir 2.
 *  - [coefficientExamen]      : per-COMPONENT coefficient for the Examen.
 *
 * The three per-component coefficients drive the subject-average formula
 * (vault §06.02):
 *     subject_avg = (D1×c1 + D2×c2 + Ex×c3) / (c1 + c2 + c3)
 *
 * ITERATION-2 NOTE (vault §06.02 — "we still don't know the actual formula"):
 * The previous build hard-coded the (D1 + D2 + 2×Ex) / 4 recipe, which locked
 * the entire platform into one specific weighting assumption (Examen = 2×).
 * Until the institution confirms its real formula, the per-component
 * coefficients remain ADMIN-CONFIGURABLE per subject and default to the
 * historical (1, 1, 2) values — so every existing GPA computation is
 * bit-identical to the previous build when no override is set. This is the
 * "old approach" the user requested: each component carries its own
 * coefficient instead of a hard-coded formula.
 */
@Serializable
data class Subject(
    val id: String,
    val tenantId: String,
    val name: String,
    val nameAr: String? = null,
    val code: String,
    val level: String,
    val coefficient: Double,
    val isExtracurricular: Boolean,
    val passingGrade: Double = 10.0,
    // Vault §06.02 — per-COMPONENT coefficients (admin-configurable per
    // subject). Defaults preserve the historical (1, 1, 2) recipe so the
    // platform stays bit-identical with the previous build when no override
    // is set. See [com.example.core.computeSubjectAverage].
    val coefficientDevoir1: Double = 1.0,
    val coefficientDevoir2: Double = 1.0,
    val coefficientExamen: Double = 2.0,
)
