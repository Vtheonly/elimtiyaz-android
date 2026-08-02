package com.example.ui.features.personnel

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.Personnel
import com.example.domain.repository.PersonnelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

// ── 2. Teacher Activity Ledger (Relevé) ───────────────────────────────────

/**
 * Activity / compliance view derived from [PersonnelRepository].
 *
 * Mirrors desktop `releve-tab` — shows each personnel's weekly hours
 * compliance (logged / target). The desktop also tracks `ReleveEntry`
 * events (course, meeting, supervision, …) but the mobile repo doesn't
 * expose them yet, so we derive compliance from the `weeklyHoursLogged`
 * vs `weeklyHoursTarget` fields on the Personnel entity.
 */
@HiltViewModel
class ReleveViewModel @Inject constructor(
    private val personnelRepository: PersonnelRepository,
) : ViewModel() {
    val personnel: StateFlow<List<Personnel>> = personnelRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
