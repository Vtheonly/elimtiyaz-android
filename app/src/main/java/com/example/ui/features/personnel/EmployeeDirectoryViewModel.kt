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

// ── 1. Personnel Directory ──────────────────────────────────────────────────

@HiltViewModel
class EmployeeDirectoryViewModel @Inject constructor(
    private val personnelRepository: PersonnelRepository,
) : ViewModel() {
    val personnel: StateFlow<List<Personnel>> = personnelRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}

@Composable
