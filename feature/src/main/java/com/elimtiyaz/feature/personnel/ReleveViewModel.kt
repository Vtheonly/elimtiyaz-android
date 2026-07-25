package com.elimtiyaz.feature.personnel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.Personnel
import com.elimtiyaz.domain.model.ReleveEntry
import com.elimtiyaz.domain.repository.AuditRepository
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.PersonnelRepository
import com.elimtiyaz.domain.repository.ReleveRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import javax.inject.Inject

/**
 * ReleveViewModel — powers Route.Releve.
 *
 * Clock-in / clock-out style form for logging a [ReleveEntry] for a given
 * personnel member. The personnel id is read from the navigation
 * [SavedStateHandle] arg `personnelId`.
 *
 * Form fields: date (default today), hoursIn, optional hoursOut, activity
 * (Cours / Réunion / Surveillance / Correction / Autre), optional class id,
 * optional subject id. On submit the VM:
 *  1. Validates that hoursIn is non-empty and that hoursOut (if provided) is
 *     strictly greater than hoursIn.
 *  2. Calls [ReleveRepository.logEntry] with the new entry.
 *  3. Calls [AuditRepository.log] with action `releve.create` so the audit
 *     trail is updated (master plan §12).
 *  4. Refreshes the today's-entries list.
 */
@HiltViewModel
class ReleveViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val personnelRepo: PersonnelRepository,
    private val releveRepo: ReleveRepository,
    private val auditRepo: AuditRepository,
    auth: AuthRepository,
) : ViewModel() {

    /** Latest session — used to derive `actorId` and `tenantId`. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val personnelId: String = savedStateHandle.get<String>("personnelId").orEmpty()

    private val _state = MutableStateFlow(ReleveUiState())
    val state: StateFlow<ReleveUiState> = _state.asStateFlow()

    init {
        loadPersonnel()
        loadTodayEntries()
    }

    /** Load the personnel header (so the screen can show its name + target). */
    fun loadPersonnel() {
        viewModelScope.launch {
            personnelRepo.personnel(personnelId).collect { result ->
                when (result) {
                    is Result.Success -> _state.update { it.copy(personnel = result.data) }
                    is Result.Failure -> _state.update { it.copy(personnel = null) }
                }
            }
        }
    }

    /** Load today's entries for this personnel. */
    fun loadTodayEntries() {
        val today = Formatters.today()
        val todayIso = Formatters.isoFromLocal(today)
        val tomorrowIso = Formatters.isoFromLocal(today.plus(1, DateTimeUnit.DAY))
        viewModelScope.launch {
            releveRepo.releveByPersonnel(personnelId, todayIso, tomorrowIso).collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(todayEntries = result.data.sortedBy { e -> e.hoursIn })
                    }
                    is Result.Failure -> _state.update { it.copy(todayEntries = emptyList()) }
                }
            }
        }
    }

    // ----- form mutations -----------------------------------------------------

    fun dateChanged(dateIso: String) {
        _state.update { it.copy(date = dateIso) }
    }

    fun hoursInChanged(v: String) {
        _state.update { it.copy(hoursIn = sanitizeTime(v)) }
    }

    fun hoursOutChanged(v: String) {
        _state.update { it.copy(hoursOut = sanitizeTime(v)) }
    }

    fun activityChanged(activity: ReleveActivity) {
        _state.update { it.copy(activity = activity) }
    }

    fun classIdChanged(v: String) {
        _state.update { it.copy(classId = v.ifBlank { null }) }
    }

    fun subjectIdChanged(v: String) {
        _state.update { it.copy(subjectId = v.ifBlank { null }) }
    }

    /** Validate + submit. Calls back with (ok, errorMessage?). */
    fun submit(onResult: (Boolean, String?) -> Unit) {
        val s = _state.value
        val hoursIn = s.hoursIn.toDoubleOrNull()
        if (hoursIn == null) {
            _state.update { it.copy(error = "Veuillez saisir l'heure d'arrivée.") }
            return
        }
        val hoursOut = s.hoursOut.takeIf { it.isNotBlank() }?.toDoubleOrNull()
        if (s.hoursOut.isNotBlank() && hoursOut == null) {
            _state.update { it.copy(error = "Heure de sortie invalide.") }
            return
        }
        if (hoursOut != null && hoursOut <= hoursIn) {
            _state.update { it.copy(error = "L'heure de sortie doit être après l'arrivée.") }
            return
        }

        _state.update { it.copy(isSubmitting = true, error = null) }
        val sess = session.value
        val actorId = sess?.userId.orEmpty()
        val tenantId = sess?.tenantId.orEmpty()
        val personnelName = s.personnel?.let { Formatters.fullName(it.firstName, it.lastName) } ?: ""
        val dateIso = s.date.ifBlank { Formatters.isoFromLocal(Formatters.today()) }
        viewModelScope.launch {
            val entry = ReleveEntry(
                id = "",
                personnelId = personnelId,
                personnelName = personnelName,
                date = dateIso,
                hoursIn = hoursIn,
                hoursOut = hoursOut,
                activity = s.activity.label,
                classId = s.classId,
                subjectId = s.subjectId,
                recordedAt = Formatters.nowIso(),
            )
            when (val r = releveRepo.logEntry(entry)) {
                is Result.Success -> {
                    auditRepo.log(
                        action = "releve.create",
                        entityType = "releve_entry",
                        entityId = r.data.id.ifBlank { personnelId },
                        actorId = actorId,
                        tenantId = tenantId,
                        note = "Relevé ${s.activity.label} pour $personnelName",
                    )
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            error = null,
                            hoursIn = "",
                            hoursOut = "",
                            classId = null,
                            subjectId = null,
                        )
                    }
                    loadTodayEntries()
                    onResult(true, null)
                }
                is Result.Failure -> {
                    _state.update { it.copy(isSubmitting = false, error = r.error.userMessage) }
                    onResult(false, r.error.userMessage)
                }
            }
        }
    }

    /** Clear the visible error. */
    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    /** Allow only digits + colon + dot in time fields. */
    private fun sanitizeTime(v: String): String =
        v.filter { it.isDigit() || it == ':' || it == '.' || it == ',' }.replace(',', '.')
}

/** Allowed activities on the Relevé form. */
enum class ReleveActivity(val label: String) {
    Cours("Cours"),
    Reunion("Réunion"),
    Surveillance("Surveillance"),
    Correction("Correction"),
    Autre("Autre"),
}

/** Relevé form + transient state. */
data class ReleveUiState(
    val personnel: Personnel? = null,
    val date: String = "",
    val hoursIn: String = "",
    val hoursOut: String = "",
    val activity: ReleveActivity = ReleveActivity.Cours,
    val classId: String? = null,
    val subjectId: String? = null,
    val todayEntries: List<ReleveEntry> = emptyList(),
    val isSubmitting: Boolean = false,
    val error: String? = null,
) {
    /** True when the form is ready to submit. */
    val canSubmit: Boolean
        get() = hoursIn.isNotBlank() && !isSubmitting
}
