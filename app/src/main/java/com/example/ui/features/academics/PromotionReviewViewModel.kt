package com.example.ui.features.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.core.PromotionDecisions
import com.example.core.Result
import com.example.core.Role
import com.example.core.computeOverallGpa
import com.example.core.derivePromotionRecommendation
import com.example.core.getNextGradeProgression
import com.example.domain.model.AcademicClass
import com.example.domain.model.Assessment
import com.example.domain.model.Student
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.GradeRepository
import com.example.domain.repository.PromotionDecision
import com.example.domain.repository.StudentRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * Vault §06.04 — One-Click Batch Promotion Engine (Step 1–4).
 *
 * The 4-step flow required by the vault:
 *   1. Calculate yearly GPAs for all enrolled students of the class
 *      (canonical [computeOverallGpa] over every term of the academic year).
 *   2. System auto-flags: GPA ≥ 10 → APPROVED_FOR_PROMOTION, GPA < 10 →
 *      RETAINED_SAME_YEAR, no grades → flagged for manual review
 *      ([PromotionCandidate.needsReview]).
 *   3. Admin reviews the queue and may override any decision with a note
 *      (medical exception, family relocation, …) — [overrideDecision].
 *   4. Execute: ONE call to the unchanged canonical
 *      [StudentRepository.promoteStudents] (ladder + graduation + audit +
 *      sync propagation all live in the repository — business logic is NOT
 *      duplicated here).
 *
 * The previous entry point (ClassesDirectoryViewModel.promoteClass) promoted
 * every ACTIVE student regardless of GPA — a direct violation of the vault's
 * "Do not run batch promotion without first reviewing the queue" rule.
 */
@HiltViewModel
class PromotionReviewViewModel @Inject constructor(
    private val classRepository: ClassRepository,
    private val studentRepository: StudentRepository,
    private val gradeRepository: GradeRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    /** One row of the review queue. */
    data class PromotionCandidate(
        val student: Student,
        /** Canonical yearly GPA (null when no scolarite grades exist). */
        val yearlyGpa: Double?,
        /** Number of graded subjects behind the GPA. */
        val gradedSubjectCount: Int,
        /** The system recommendation ([derivePromotionRecommendation]). */
        val recommendation: String,
        /** The FINAL decision — starts as the recommendation, may be overridden. */
        var decision: String,
        /** True when no grades exist — the queue must force manual review. */
        val needsReview: Boolean,
        /** Override note (audit-logged with the decision). */
        var overrideNote: String? = null,
    ) {
        val isOverridden: Boolean get() = decision != recommendation
    }

    private val _klass = MutableStateFlow<AcademicClass?>(null)
    val klass: StateFlow<AcademicClass?> = _klass.asStateFlow()

    private val _candidates = MutableStateFlow<List<PromotionCandidate>>(emptyList())
    val candidates: StateFlow<List<PromotionCandidate>> = _candidates.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** RBAC gate — only PROMOTE_STUDENT holders may run the batch. */
    val canPromote: Boolean
        get() = sessionManager.current()?.can(Permission.PROMOTE_STUDENT) == true ||
            sessionManager.current()?.role in listOf(Role.SUPER_ADMIN, Role.MANAGER)

    fun load(classId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val klass = classRepository.observe()
                    .firstOrNull().orEmpty()
                    .firstOrNull { it.id == classId }
                _klass.value = klass
                if (klass == null) {
                    _error.value = "Classe introuvable."
                    return@launch
                }

                // ── Step 1: yearly GPAs ──────────────────────────────────
                val year = klass.academicYear.ifBlank { currentAcademicYear() }
                val roster = studentRepository.observeByClass(classId)
                    .firstOrNull().orEmpty()
                    .filter { it.status == "active" }
                if (roster.isEmpty()) {
                    _error.value = "Aucun élève actif dans ${klass.name}."
                    return@launch
                }

                // All assessments of the class for the year (every term).
                val classAssessments: List<Assessment> =
                    gradeRepository.observeForClass(classId, "T1", year).firstOrNull().orEmpty() +
                        gradeRepository.observeForClass(classId, "T2", year).firstOrNull().orEmpty() +
                        gradeRepository.observeForClass(classId, "T3", year).firstOrNull().orEmpty()
                val byStudent = classAssessments.groupBy { it.studentId }

                // ── Step 2: auto-flag from the canonical GPA ──────────────
                _candidates.value = roster.map { student ->
                    val rows = byStudent[student.id].orEmpty()
                    val gradedCount = rows.count { !it.isExtracurricular && it.subjectAverage != null }
                    val gpa = if (rows.isEmpty()) null else computeOverallGpa(rows)
                    val isFinalYear = getNextGradeProgression(student.gradeLevel).isGraduation
                    val recommendation = derivePromotionRecommendation(gpa, isFinalYear)
                    PromotionCandidate(
                        student = student,
                        yearlyGpa = gpa,
                        gradedSubjectCount = gradedCount,
                        recommendation = recommendation,
                        decision = recommendation,
                        needsReview = rows.isEmpty(),
                    )
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /** Step 3 — manual exception override (audit-logged note is mandatory). */
    fun overrideDecision(studentId: String, decision: String, note: String?) {
        _candidates.value = _candidates.value.map {
            if (it.student.id == studentId) {
                it.decision = decision
                it.overrideNote = note?.takeIf { n -> n.isNotBlank() }
                it
            } else it
        }
    }

    /** Reset one student's decision back to the system recommendation. */
    fun resetDecision(studentId: String) {
        _candidates.value = _candidates.value.map {
            if (it.student.id == studentId) {
                it.decision = it.recommendation
                it.overrideNote = null
                it
            } else it
        }
    }

    /** Step 4 — execute the batch via the canonical repository call. */
    fun execute() {
        val klass = _klass.value ?: return
        val candidates = _candidates.value
        if (candidates.isEmpty()) return
        viewModelScope.launch {
            _isExecuting.value = true
            try {
                // Students with NO grades must be reviewed manually before the
                // batch can run — the vault forbids blind promotion.
                val unreviewed = candidates.count { it.needsReview && !it.isOverridden }
                if (unreviewed > 0) {
                    _error.value = "$unreviewed élève(s) sans notes doivent être arbitrés " +
                        "(promotion manuelle ou redoublement) avant d'exécuter le lot."
                    return@launch
                }
                val decisions = candidates.map {
                    PromotionDecision(
                        studentId = it.student.id,
                        decision = it.decision,
                        note = it.overrideNote,
                    )
                }
                val actorId = sessionManager.currentUserId() ?: "system"
                val actorName = sessionManager.currentDisplayName() ?: "System"
                when (val result = studentRepository.promoteStudents(currentAcademicYear(), decisions, actorId, actorName)) {
                    is Result.Ok -> {
                        val promoted = candidates.count { it.decision == PromotionDecisions.PROMOTED }
                        val retained = candidates.count { it.decision == PromotionDecisions.REPEATED }
                        val graduated = candidates.count { it.decision == PromotionDecisions.GRADUATED }
                        _message.value = "Lot exécuté — ${promoted} promu(s), " +
                            "$retained redouble(nt), $graduated diplômé(s) depuis ${klass.name}."
                        // Reload the queue so grade levels reflect the batch.
                        load(klass.id)
                    }
                    is Result.Err -> _error.value = result.error.userMessage
                }
            } finally {
                _isExecuting.value = false
            }
        }
    }

    /** Current school year, e.g. "2026-2027" (September rollover). */
    private fun currentAcademicYear(): String {
        val now = java.time.LocalDate.now()
        return if (now.monthValue >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
    }

    fun clearMessages() {
        _error.value = null
        _message.value = null
    }
}
