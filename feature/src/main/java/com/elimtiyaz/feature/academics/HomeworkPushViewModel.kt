package com.elimtiyaz.feature.academics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.Homework
import com.elimtiyaz.domain.model.Subject
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.HomeworkRepository
import com.elimtiyaz.domain.repository.SubjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * View-model for the Homework Push screen.
 *
 * Owns two responsibilities:
 * 1. Lists past homework for the class so the teacher can scroll, review, and
 *    "Renvoyer" (re-push) a previous assignment.
 * 2. Hosts the push form (title, description, subject, due date, attachments)
 *    and on submit calls [HomeworkRepository.push] which triggers the FCM
 *    notification to parents server-side (mock-mode just logs).
 */
@HiltViewModel
class HomeworkPushViewModel @Inject constructor(
    private val homework: HomeworkRepository,
    private val subjects: SubjectRepository,
    private val auth: AuthRepository,
) : ViewModel() {

    /** Current session — used for `teacherId` / `teacherName` and to gate the form. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _uiState = MutableStateFlow(HomeworkPushUiState())
    val uiState: StateFlow<HomeworkPushUiState> = _uiState.asStateFlow()

    /** Load past homework for the class + the subject dropdown options. */
    fun load(classId: String) {
        if (_uiState.value.classId == classId && _uiState.value.pastHomework.isNotEmpty()) return
        _uiState.update {
            it.copy(isLoading = true, error = null, classId = classId)
        }
        viewModelScope.launch {
            launch { collectHomework(classId) }
            launch { collectSubjectsForClass(classId) }
        }
    }

    // ----- Form field setters ------------------------------------------------

    fun titleChanged(v: String) = _uiState.update { it.copy(title = v, error = null) }
    fun descriptionChanged(v: String) = _uiState.update { it.copy(description = v, error = null) }
    fun subjectChanged(subjectId: String) = _uiState.update {
        it.copy(selectedSubjectId = subjectId, error = null)
    }
    fun dueDateChanged(iso: String) = _uiState.update { it.copy(dueDate = iso, error = null) }
    fun addAttachment(uri: String) = _uiState.update {
        it.copy(attachments = it.attachments + uri)
    }
    fun removeAttachment(uri: String) = _uiState.update {
        it.copy(attachments = it.attachments.filterNot { a -> a == uri })
    }

    // ----- Push & re-push ----------------------------------------------------

    /** Push a new homework assignment. [onDone] receives null on success or an error. */
    fun push(onDone: (String?) -> Unit) {
        val st = _uiState.value
        val s = session.value ?: return run { onDone("Session expirée.") }
        val cid = st.classId ?: return run { onDone("Classe invalide.") }
        val sid = st.selectedSubjectId ?: return run { onDone("Veuillez sélectionner une matière.") }
        if (st.title.isBlank()) return run { onDone("Le titre est requis.") }
        if (st.description.isBlank()) return run { onDone("La description est requise.") }
        if (st.dueDate.isBlank()) return run { onDone("La date d'échéance est requise.") }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val r = homework.push(
                classId = cid,
                subjectId = sid,
                teacherId = s.userId,
                teacherName = s.displayName,
                title = st.title.trim(),
                description = st.description.trim(),
                dueDate = st.dueDate,
                attachments = st.attachments,
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            // Prepend the new homework to the past list so the UI
                            // reflects it without an extra round-trip.
                            pastHomework = listOf(r.data) + it.pastHomework,
                            title = "",
                            description = "",
                            dueDate = "",
                            attachments = emptyList(),
                            savedAt = Formatters.nowIso(),
                            error = null,
                        )
                    }
                    onDone(null)
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(isSaving = false, error = r.error) }
                    onDone(r.error.userMessage)
                }
            }
        }
    }

    /** Re-push an existing homework assignment as a new entry. */
    fun rePush(homeworkId: String, onDone: (String?) -> Unit) {
        val st = _uiState.value
        val s = session.value ?: return run { onDone("Session expirée.") }
        val cid = st.classId ?: return run { onDone("Classe invalide.") }
        val original = st.pastHomework.firstOrNull { it.id == homeworkId }
            ?: return run { onDone("Devoir introuvable.") }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            when (val r = homework.push(
                classId = cid,
                subjectId = original.subjectId,
                teacherId = s.userId,
                teacherName = s.displayName,
                title = original.title,
                description = original.description,
                dueDate = original.dueDate,
                attachments = original.attachments,
            )) {
                is Result.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            pastHomework = listOf(r.data) + it.pastHomework,
                            savedAt = Formatters.nowIso(),
                        )
                    }
                    onDone(null)
                }
                is Result.Failure -> {
                    _uiState.update { it.copy(isSaving = false, error = r.error) }
                    onDone(r.error.userMessage)
                }
            }
        }
    }

    private suspend fun collectHomework(classId: String) {
        homework.homeworkForClass(classId).collect { result ->
            when (result) {
                is Result.Success -> _uiState.update {
                    it.copy(isLoading = false, pastHomework = result.data, error = null)
                }
                is Result.Failure -> _uiState.update { it.copy(isLoading = false, error = result.error) }
            }
        }
    }

    private suspend fun collectSubjectsForClass(classId: String) {
        val r = subjects.subjectsByClass(classId).first()
        if (r is Result.Success) {
            // Fetch full Subject objects to populate the dropdown.
            val all = subjects.subjects().first()
            val subjectMap: Map<String, Subject> = (all as? Result.Success)?.data
                ?.associateBy { it.id }
                ?: emptyMap()
            val options = r.data.mapNotNull { cs ->
                subjectMap[cs.subjectId]?.let { subj ->
                    SubjectOption(id = subj.id, name = subj.name, code = subj.code)
                }
            }
            _uiState.update {
                it.copy(
                    subjectOptions = options,
                    selectedSubjectId = it.selectedSubjectId ?: options.firstOrNull()?.id,
                )
            }
        }
    }

    /** True iff the user can push homework. */
    fun canAssignHomework(): Boolean = session.value?.can(Permission.AssignHomework) == true
}

/** Dropdown option for the subject picker. */
data class SubjectOption(
    val id: String,
    val name: String,
    val code: String,
)

/**
 * Homework push screen state. The form fields live alongside the past-homework
 * list so the screen can render both sections from a single state.
 */
data class HomeworkPushUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: AppError? = null,
    val classId: String? = null,
    val pastHomework: List<Homework> = emptyList(),
    val subjectOptions: List<SubjectOption> = emptyList(),
    val selectedSubjectId: String? = null,
    val title: String = "",
    val description: String = "",
    val dueDate: String = "",
    val attachments: List<String> = emptyList(),
    val savedAt: String? = null,
)
