package com.example.ui.features.academics

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AcademicClass
import com.example.domain.repository.ClassRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

// ── 4. Classes Directory ──────────────────────────────────────────────────

@HiltViewModel
class ClassesDirectoryViewModel @Inject constructor(
    private val classRepository: ClassRepository,
) : ViewModel() {
    val classes: StateFlow<List<AcademicClass>> = classRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
}
