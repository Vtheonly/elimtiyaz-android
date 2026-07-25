package com.elimtiyaz.feature.personnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.AuditEntry
import com.elimtiyaz.domain.model.Personnel
import com.elimtiyaz.domain.model.StaffCategory
import com.elimtiyaz.domain.repository.AuditRepository
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.PersonnelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PersonnelHubViewModel — root of the Personnel tab (Route.Personnel).
 *
 * Aggregates three data sources into a single [PersonnelHubUiState]:
 *  - [PersonnelRepository.personnel] for the "Annuaire" tab (full directory,
 *    filtered by [StaffCategory] on the client).
 *  - [PersonnelRepository.personnel] (re-used) for the "Relevé" tab where each
 *    row shows the weekly hours progress (logged/target).
 *  - [AuditRepository.recent] for the "Audit" tab preview (last 10 entries;
 *    tap → full AuditLogScreen).
 *
 * The Workflows tab is powered by a static mock list (see [WorkflowMonitorViewModel])
 * — the actual DAG canvas editor is desktop-only per master plan §13.
 *
 * The current [Session] is exposed so the screen can gate the FAB on
 * [Permission.ManagePersonnel] and the Audit tab on [Permission.ViewAuditLog].
 */
@HiltViewModel
class PersonnelHubViewModel @Inject constructor(
    private val personnelRepo: PersonnelRepository,
    private val auditRepo: AuditRepository,
    auth: AuthRepository,
) : ViewModel() {

    /** Latest session — used by the screen to gate the FAB and Audit tab. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _state = MutableStateFlow(PersonnelHubUiState())
    val state: StateFlow<PersonnelHubUiState> = _state.asStateFlow()

    init {
        loadPersonnel()
        loadAuditPreview()
    }

    /** Refresh the personnel directory (drives both Annuaire and Relevé tabs). */
    fun loadPersonnel() {
        _state.update { it.copy(personnelLoading = true, personnelError = null) }
        viewModelScope.launch {
            personnelRepo.personnel().collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(
                            personnelLoading = false,
                            personnelError = null,
                            personnel = result.data.sortedBy { p -> p.lastName },
                        )
                    }
                    is Result.Failure -> _state.update {
                        it.copy(
                            personnelLoading = false,
                            personnelError = result.error,
                            personnel = emptyList(),
                        )
                    }
                }
            }
        }
    }

    /** Refresh the recent audit entries (preview shown on the Audit tab). */
    fun loadAuditPreview() {
        _state.update { it.copy(auditLoading = true, auditError = null) }
        viewModelScope.launch {
            auditRepo.recent(limit = 10).collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(
                            auditLoading = false,
                            auditError = null,
                            auditPreview = result.data,
                        )
                    }
                    is Result.Failure -> _state.update {
                        it.copy(
                            auditLoading = false,
                            auditError = result.error,
                            auditPreview = emptyList(),
                        )
                    }
                }
            }
        }
    }

    /** Set the staff-category filter for the Annuaire tab (null = all). */
    fun filterByCategory(category: StaffCategory?) {
        _state.update { it.copy(categoryFilter = category) }
    }
}

/**
 * Hub screen state. Each tab carries its own loading/error flag so the
 * screen can render [com.elimtiyaz.core.ui.AsyncContent] independently.
 */
data class PersonnelHubUiState(
    val personnel: List<Personnel> = emptyList(),
    val personnelLoading: Boolean = true,
    val personnelError: AppError? = null,
    val categoryFilter: StaffCategory? = null,
    val auditPreview: List<AuditEntry> = emptyList(),
    val auditLoading: Boolean = true,
    val auditError: AppError? = null,
) {
    /** Personnel filtered by the selected staff category (or all). */
    val filteredPersonnel: List<Personnel>
        get() = if (categoryFilter == null) personnel
        else personnel.filter { it.staffCategory == categoryFilter }

    /** Aggregated weekly hours — sum across all personnel for the Relevé tab header. */
    val weeklyHoursTotalLogged: Int get() = personnel.sumOf { it.weeklyHoursLogged }
    val weeklyHoursTotalTarget: Int get() = personnel.sumOf { it.weeklyHoursTarget }
}
