package com.elimtiyaz.feature.crm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.Formatters
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.BatchRegistrationInput
import com.elimtiyaz.domain.model.BatchRegistrationResult
import com.elimtiyaz.domain.model.CreateParentInput
import com.elimtiyaz.domain.model.CreateStudentInput
import com.elimtiyaz.domain.model.Gender
import com.elimtiyaz.domain.repository.AuthRepository
import com.elimtiyaz.domain.repository.StudentRepository
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
 * BatchRegistrationViewModel — powers the 4-step wizard at Route.BatchRegistration.
 *
 * The wizard state machine:
 *   Step 1 (ParentInfo) → Step 2 (Students) → Step 3 (Review) → Step 4 (Submit)
 *
 * Each step exposes explicit mutator functions; the screen calls them rather than
 * passing callbacks into child composables. On submit the VM calls
 * [StudentRepository.batchRegister] which is atomic per master plan §04.03.
 */
@HiltViewModel
class BatchRegistrationViewModel @Inject constructor(
    private val studentRepo: StudentRepository,
    auth: AuthRepository,
) : ViewModel() {

    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _state = MutableStateFlow(BatchRegistrationUiState())
    val state: StateFlow<BatchRegistrationUiState> = _state.asStateFlow()

    // ---------- Step navigation ----------

    /** Move to the next step, validating the current step first. */
    fun goToNext(): Boolean {
        val s = _state.value
        val error = when (s.step) {
            BatchStep.ParentInfo -> validateParent(s.parentForm)
            BatchStep.Students -> validateStudents(s.studentForms)
            BatchStep.Review -> null
            BatchStep.Submit -> null
        }
        if (error != null) {
            _state.update { it.copy(formError = error) }
            return false
        }
        _state.update {
            it.copy(step = nextStep(it.step), formError = null)
        }
        return true
    }

    /** Move back one step (no validation). */
    fun goToPrevious() {
        _state.update {
            it.copy(step = previousStep(it.step), formError = null)
        }
    }

    /** Jump directly to a step (used by the stepper when revisiting). */
    fun jumpTo(step: BatchStep) {
        _state.update { it.copy(step = step, formError = null) }
    }

    // ---------- Parent form mutators (Step 1) ----------

    fun updateParentForm(transform: (ParentForm) -> ParentForm) {
        _state.update { it.copy(parentForm = transform(it.parentForm), formError = null) }
    }

    // ---------- Student form mutators (Step 2) ----------

    /** Add an empty student form to the wizard. */
    fun addStudent() {
        _state.update {
            it.copy(studentForms = it.studentForms + StudentForm())
        }
    }

    /** Remove a student form by index. */
    fun removeStudent(index: Int) {
        _state.update {
            val updated = it.studentForms.toMutableList()
            if (index in updated.indices) updated.removeAt(index)
            it.copy(studentForms = updated)
        }
    }

    /** Patch a single student form at [index]. */
    fun updateStudent(index: Int, transform: (StudentForm) -> StudentForm) {
        _state.update {
            val updated = it.studentForms.toMutableList()
            if (index in updated.indices) updated[index] = transform(updated[index])
            it.copy(studentForms = updated, formError = null)
        }
    }

    // ---------- Submit (Step 4) ----------

    /** Submit the atomic batch registration. */
    fun submit(onResult: (Boolean, String?, String?) -> Unit) {
        val s = _state.value
        val parentError = validateParent(s.parentForm)
        if (parentError != null) {
            onResult(false, parentError, null); return
        }
        val studentsError = validateStudents(s.studentForms)
        if (studentsError != null) {
            onResult(false, studentsError, null); return
        }
        if (s.studentForms.isEmpty()) {
            onResult(false, "Veuillez ajouter au moins un élève.", null); return
        }
        _state.update { it.copy(isSubmitting = true, formError = null) }
        viewModelScope.launch {
            val input = BatchRegistrationInput(
                parent = s.parentForm.toCreateParentInput(),
                students = s.studentForms.map { it.toCreateStudentInput() },
            )
            when (val r = studentRepo.batchRegister(input)) {
                is Result.Success -> {
                    _state.update { it.copy(isSubmitting = false, result = r.data) }
                    onResult(true, null, r.data.parent.id)
                }
                is Result.Failure -> {
                    _state.update { it.copy(isSubmitting = false, formError = r.error.userMessage) }
                    onResult(false, r.error.userMessage, null)
                }
            }
        }
    }

    // ---------- Validation helpers ----------

    private fun validateParent(p: ParentForm): String? {
        if (p.firstName.isBlank()) return "Le prénom du parent est requis."
        if (p.lastName.isBlank()) return "Le nom du parent est requis."
        if (p.phone.isBlank()) return "Le téléphone est requis."
        if (!PHONE_REGEX.matches(p.phone)) return "Numéro de téléphone invalide (ex. 0550123456)."
        if (p.email.isNotBlank() && !EMAIL_REGEX.matches(p.email)) return "Adresse e-mail invalide."
        if (!p.whatsapp.isNullOrBlank() && !PHONE_REGEX.matches(p.whatsapp)) return "Numéro WhatsApp invalide."
        return null
    }

    private fun validateStudents(students: List<StudentForm>): String? {
        if (students.isEmpty()) return "Veuillez ajouter au moins un élève."
        students.forEachIndexed { i, s ->
            if (s.firstName.isBlank()) return "Élève #${i + 1}: prénom requis."
            if (s.lastName.isBlank()) return "Élève #${i + 1}: nom requis."
            if (s.birthDate.isBlank()) return "Élève #${i + 1}: date de naissance requise."
            if (s.level.isBlank()) return "Élève #${i + 1}: niveau requis."
            if (s.gradeYear <= 0) return "Élève #${i + 1}: année du niveau requise."
        }
        return null
    }

    private fun nextStep(s: BatchStep): BatchStep = when (s) {
        BatchStep.ParentInfo -> BatchStep.Students
        BatchStep.Students -> BatchStep.Review
        BatchStep.Review -> BatchStep.Submit
        BatchStep.Submit -> BatchStep.Submit
    }

    private fun previousStep(s: BatchStep): BatchStep = when (s) {
        BatchStep.ParentInfo -> BatchStep.ParentInfo
        BatchStep.Students -> BatchStep.ParentInfo
        BatchStep.Review -> BatchStep.Students
        BatchStep.Submit -> BatchStep.Review
    }

    companion object {
        private val PHONE_REGEX = Regex("^[+]?[0-9\\s]{8,15}$")
        private val EMAIL_REGEX = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")
    }
}

/** Wizard step enum — exposed to the screen for the stepper. */
enum class BatchStep(val index: Int, val label: String) {
    ParentInfo(0, "Parent"),
    Students(1, "Élèves"),
    Review(2, "Révision"),
    Submit(3, "Validation"),
}

/** Mutable parent form state (Step 1). */
data class ParentForm(
    val firstName: String = "",
    val lastName: String = "",
    val gender: Gender = Gender.Unspecified,
    val phone: String = "",
    val whatsapp: String? = null,
    val email: String = "",
    val occupation: String? = null,
    val address: String? = null,
    val cityTier: String? = null,         // t1 / t2 / t3
    val preferredLanguage: String = "fr",
) {
    fun toCreateParentInput(): CreateParentInput = CreateParentInput(
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        gender = gender,
        phone = phone.trim(),
        whatsapp = whatsapp?.trim()?.ifBlank { null },
        email = email.trim().ifBlank { null },
        occupation = occupation?.trim()?.ifBlank { null },
        address = address?.trim()?.ifBlank { null },
        cityTier = cityTier,
        preferredLanguage = preferredLanguage,
    )
}

/** Mutable student form state (Step 2). */
data class StudentForm(
    val firstName: String = "",
    val lastName: String = "",
    val gender: Gender = Gender.Unspecified,
    val birthDate: String = "",
    val level: String = "primaire",
    val gradeYear: Int = 1,
    val classId: String? = null,
    val transportTier: String? = null,
    val medicalNotes: String? = null,
) {
    fun toCreateStudentInput(): CreateStudentInput = CreateStudentInput(
        parentId = "",  // set by the repository (atomic batch creates parent first)
        firstName = firstName.trim(),
        lastName = lastName.trim(),
        gender = gender,
        birthDate = birthDate.trim(),
        level = level,
        gradeYear = gradeYear,
        classId = classId,
        medicalNotes = medicalNotes?.trim()?.ifBlank { null },
        transportTier = transportTier,
    )
}

/** Whole wizard state. */
data class BatchRegistrationUiState(
    val step: BatchStep = BatchStep.ParentInfo,
    val parentForm: ParentForm = ParentForm(),
    val studentForms: List<StudentForm> = emptyList(),
    val isSubmitting: Boolean = false,
    val formError: String? = null,
    val result: BatchRegistrationResult? = null,
)
