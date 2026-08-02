package com.example.ui.features.personnel

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AuditLog
import com.example.domain.repository.AuditRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

// ── 3. Live Audit Stream ────────────────────────────────────────────────────

@HiltViewModel
class AuditStreamViewModel @Inject constructor(
    private val auditRepository: AuditRepository,
) : ViewModel() {
    val logs: StateFlow<List<AuditLog>> = auditRepository.observe(limit = 50)
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
