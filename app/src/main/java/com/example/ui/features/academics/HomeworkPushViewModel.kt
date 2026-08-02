package com.example.ui.features.academics

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.domain.model.AcademicClass
import com.example.domain.model.Subject
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.HomeworkRepository
import com.example.domain.repository.PushHomeworkInput
import com.example.domain.repository.SubjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ── 3. Homework Push ──────────────────────────────────────────────────────

@HiltViewModel
class HomeworkPushViewModel @Inject constructor(
    private val classRepository: ClassRepository,
    private val subjectRepository: SubjectRepository,
    private val homeworkRepository: HomeworkRepository,
) : ViewModel() {

    val classes: StateFlow<List<AcademicClass>> = classRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _subjects = kotlinx.coroutines.flow.MutableStateFlow<List<Subject>>(emptyList())
    val subjects: StateFlow<List<Subject>> = _subjects

    private val _busy = kotlinx.coroutines.flow.MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _message = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun loadSubjectsForClass(classId: String) {
        viewModelScope.launch {
            subjectRepository.observeByClass(classId).collect { _subjects.value = it }
        }
    }

    fun pushHomework(
        classId: String,
        subjectId: String,
        title: String,
        description: String,
        dueDate: String,
        attachments: List<String>,
        academicYear: String,
        actorId: String,
        actorName: String,
    ) {
        viewModelScope.launch {
            _busy.value = true
            val input = PushHomeworkInput(
                classId = classId, subjectId = subjectId,
                title = title, description = description,
                dueDate = dueDate, attachments = attachments,
                academicYear = academicYear,
            )
            val result = homeworkRepository.push(input, actorId, actorName)
            _busy.value = false
            result.onSuccess { _message.value = "Devoir diffusé à la classe." }
                .onFailure { _message.value = it.userMessage }
        }
    }
}
