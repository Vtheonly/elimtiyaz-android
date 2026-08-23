package com.example.ui.features.academics

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Permission
import com.example.core.PromotionDecisions
import com.example.core.Result
import com.example.core.Role
import com.example.core.getNextGradeProgression
import com.example.domain.model.AcademicClass
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.PromotionDecision
import com.example.domain.repository.StudentRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

// ── 4. Classes Directory ──────────────────────────────────────────────────

@HiltViewModel
class ClassesDirectoryViewModel @Inject constructor(
    private val classRepository: ClassRepository,
    private val studentRepository: StudentRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {
    val classes: StateFlow<List<AcademicClass>> = classRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    /** RBAC gate — only PROMOTE_STUDENT holders may run a promotion. */
    val canPromote: Boolean
        get() = sessionManager.current()?.can(Permission.PROMOTE_STUDENT) == true ||
            sessionManager.current()?.role in listOf(Role.SUPER_ADMIN, Role.MANAGER)

    /**
     * Promote every ACTIVE student of [klass] up one level of the canonical
     * Algerian ladder (core/AcademicProgression.kt). Final-year students
     * (3eme_annee) are graduated instead of promoted; students at an unknown
     * ladder position are skipped (kept as-is by the repository).
     */
    fun promoteClass(klass: AcademicClass) {
        viewModelScope.launch {
            _busy.value = true
            try {
                val roster = studentRepository.observeByClass(klass.id)
                    .firstOrNull()
                    .orEmpty()
                    .filter { it.status == "active" }
                if (roster.isEmpty()) {
                    _error.value = "Aucun élève actif dans ${klass.name}."
                    return@launch
                }
                val decisions = roster.mapNotNull { student ->
                    val progression = getNextGradeProgression(student.gradeLevel)
                    when {
                        progression.isGraduation ->
                            PromotionDecision(student.id, PromotionDecisions.GRADUATED)
                        progression.nextGradeCode != null ->
                            PromotionDecision(student.id, PromotionDecisions.PROMOTED)
                        else -> null // unknown ladder position — keep state
                    }
                }
                if (decisions.isEmpty()) {
                    _error.value = "Aucun élève promouvable dans ${klass.name}."
                    return@launch
                }
                val actorId = sessionManager.currentUserId() ?: "system"
                val actorName = sessionManager.currentDisplayName() ?: "System"
                when (val result = studentRepository.promoteStudents(currentAcademicYear(), decisions, actorId, actorName)) {
                    is Result.Ok -> _message.value =
                        "${decisions.size} élève(s) promu(s) depuis ${klass.name}."
                    is Result.Err -> _error.value = result.error.userMessage
                }
            } finally {
                _busy.value = false
            }
        }
    }

    /** Current school year, e.g. "2026-2027" (September rollover). */
    private fun currentAcademicYear(): String {
        val now = Clock.System.todayIn(TimeZone.currentSystemDefault())
        return if (now.monthNumber >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
    }

    fun clearMessages() {
        _error.value = null
        _message.value = null
    }
}
