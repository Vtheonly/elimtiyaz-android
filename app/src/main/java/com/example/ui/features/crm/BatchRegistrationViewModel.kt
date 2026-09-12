package com.example.ui.features.crm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.EvaluateAllDiscountsParams
import com.example.core.PaymentPlan
import com.example.core.Result
import com.example.core.evaluateAllSystemDiscounts
import com.example.core.splitNetTuitionByOfficialSchedule
import com.example.core.sumDiscounts
import com.example.domain.model.AcademicClass
import com.example.domain.model.GradeLevelTuition
import com.example.domain.model.TransportPricing
import com.example.domain.repository.ClassRepository
import com.example.domain.repository.CreateParentInput
import com.example.domain.repository.CreateStudentInput
import com.example.domain.repository.PricingRepository
import com.example.domain.repository.StudentRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Simulated billing for ONE child — computed by the SAME engine functions the
 * repository's `batchRegister` uses (T-320). Money values are centimes (Long).
 *
 * CALC-001 honesty rules baked into the shape:
 *  - [tuitionGross] is null when the grade has NO tuition row (the repository
 *    bills nothing in that case — the simulation must warn, not invent).
 *  - There is NO registration-fee row: `batchRegister` bills tuition +
 *    transport only (FI is bundled per Prices.md and the seeded config fee
 *    is 0), so showing one would promise a charge that never happens.
 */
data class CalculatedChildBilling(
    val childId: String,
    val childName: String,
    val gradeLevel: String,
    /** Gross annual scolarité in centimes — null = no tuition row for this grade. */
    val tuitionGross: Long?,
    /** Negative centimes — sum of the REAL engine rules that fired. */
    val discounts: Long,
    /** Canonical engine labels, one per fired rule. */
    val discountLabels: List<String>,
    val netTuition: Long,
    val transportAnnual: Long?,
    /** 3-tranche preview (post-discount 40/30/30) — singleton list for full_annual. */
    val tranchePreview: List<Pair<String, Long>>,
    val totalChild: Long,
)

/** Simulated billing for the whole family (step 3 of the wizard). */
data class BillingSimulation(
    val children: List<CalculatedChildBilling> = emptyList(),
    val tuitionGrossTotal: Long = 0L,
    val discountsTotal: Long = 0L,
    val tuitionNetTotal: Long = 0L,
    val transportTotal: Long = 0L,
    val grandTotal: Long = 0L,
    /** True when at least one child's grade has no tuition row (silent zero-billing trap). */
    val hasMissingTuition: Boolean = false,
)

@HiltViewModel
class BatchRegistrationViewModel @Inject constructor(
    private val studentRepository: StudentRepository,
    private val classRepository: ClassRepository,
    private val pricingRepository: PricingRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    // ── Legacy state (unchanged contract) ────────────────────────────────
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** T-320: dismissible error banner support. */
    fun clearError() {
        _error.value = null
    }

    private val _activationCode = MutableStateFlow<String?>(null)
    val activationCode: StateFlow<String?> = _activationCode.asStateFlow()

    /**
     * Vault §04.03 — class catalogue for the per-child "Assigned Academic
     * Level & Class" dropdown (filtered by the chosen grade's cycle in the UI).
     */
    val classes: StateFlow<List<AcademicClass>> = classRepository.observe()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── T-320 wizard state (pure UI state — no repository contract change) ──
    private val _currentStep = MutableStateFlow(1)
    val currentStep: StateFlow<Int> = _currentStep.asStateFlow()

    private val _children = MutableStateFlow(listOf(ChildFormState()))
    val children: StateFlow<List<ChildFormState>> = _children.asStateFlow()

    private val _selectedChildIndex = MutableStateFlow(0)
    val selectedChildIndex: StateFlow<Int> = _selectedChildIndex.asStateFlow()

    /** Codes surfaced by the success screen — from BatchRegisterResult, zero repo change. */
    private val _generatedParentCode = MutableStateFlow<String?>(null)
    val generatedParentCode: StateFlow<String?> = _generatedParentCode.asStateFlow()

    /** "Prénom Nom (ELV-2026-000123)" per registered student. */
    private val _registeredStudentCodes = MutableStateFlow<List<String>>(emptyList())
    val registeredStudentCodes: StateFlow<List<String>> = _registeredStudentCodes.asStateFlow()

    // ── Pricing data for the step-3 simulation (real per-zone transport) ──
    val gradeTuitions: StateFlow<List<GradeLevelTuition>> = pricingRepository.observeGradeLevelTuition()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val transportPricing: StateFlow<List<TransportPricing>> = pricingRepository.observeTransportPricing()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setStep(step: Int) {
        // Backwards jumps (completed steps) are allowed from the stepper;
        // forward navigation goes through [nextStep] so validation runs.
        _currentStep.value = step.coerceIn(1, 4)
    }

    fun nextStep() {
        _currentStep.value = (_currentStep.value + 1).coerceAtMost(4)
    }

    fun prevStep() {
        _currentStep.value = (_currentStep.value - 1).coerceAtLeast(1)
    }

    fun addChild(lastName: String) {
        _children.value = _children.value + ChildFormState(lastName = lastName)
        _selectedChildIndex.value = _children.value.lastIndex
    }

    fun removeChild(index: Int) {
        if (_children.value.size <= 1) return
        _children.value = _children.value.filterIndexed { i, _ -> i != index }
        _selectedChildIndex.value = _selectedChildIndex.value.coerceAtMost(_children.value.lastIndex)
    }

    fun updateChild(index: Int, child: ChildFormState) {
        if (index !in _children.value.indices) return
        _children.value = _children.value.toMutableList().also { it[index] = child }
    }

    /** Step-1 convenience: auto-populate the children's (still blank) last name. */
    fun fillChildrenLastNameIfBlank(lastName: String) {
        if (lastName.isBlank()) return
        _children.value = _children.value.map { if (it.lastName.isBlank()) it.copy(lastName = lastName) else it }
    }

    fun selectChild(index: Int) {
        if (index in _children.value.indices) _selectedChildIndex.value = index
    }

    /**
     * Pure simulation — the SAME discount engine + tranche split the
     * repository's `batchRegister` applies, fed by the REAL per-zone
     * transport rows. No hard-coded fees, no fictional rules.
     */
    fun computeSimulation(
        parentTransportDestination: String?,
        children: List<ChildFormState>,
    ): BillingSimulation {
        val year = LocalDate.now().year
        val tuitions = gradeTuitions.value
        val transportRow = parentTransportDestination
            ?.takeIf { it.isNotBlank() }
            ?.let { zone -> transportPricing.value.firstOrNull { it.destination == zone } }

        val perChild = children.mapIndexed { index, child ->
            val tuition = tuitions.firstOrNull { it.gradeLevel == child.gradeLevel }
            val plan = PaymentPlan.fromCode(child.paymentPlan)
            val evaluations = if (tuition != null) {
                evaluateAllSystemDiscounts(
                    EvaluateAllDiscountsParams(
                        grossScolarite = tuition.annualAmount,
                        currentGradeLevel = child.gradeLevel,
                        childIndex = index + 1,
                        paymentPlan = plan,
                        // Mirrors batchRegister: "now" as the payment moment and
                        // the current year as the academic-year start year.
                        paymentDate = java.time.Instant.now().toString(),
                        academicYearStartYear = year,
                    ),
                )
            } else {
                emptyList()
            }
            val discounts = sumDiscounts(evaluations)
            val net = tuition?.let { (it.annualAmount + discounts).coerceAtLeast(0L) }
            val tranchePreview: List<Pair<String, Long>> = when {
                net == null -> emptyList()
                plan == PaymentPlan.FULL_ANNUAL -> listOf("Année complète" to net)
                else -> {
                    val (t1, t2, t3) = splitNetTuitionByOfficialSchedule(net)
                    listOf(
                        "Tranche 1 (40%)" to t1,
                        "Tranche 2 (30%)" to t2,
                        "Tranche 3 (30%)" to t3,
                    )
                }
            }
            CalculatedChildBilling(
                childId = child.id,
                childName = listOf(child.firstName, child.lastName).filter { it.isNotBlank() }
                    .joinToString(" ").ifBlank { "Élève ${index + 1}" },
                gradeLevel = child.gradeLevel,
                tuitionGross = tuition?.annualAmount,
                discounts = discounts,
                discountLabels = evaluations.map { it.label },
                netTuition = net ?: 0L,
                transportAnnual = transportRow?.annualAmount,
                tranchePreview = tranchePreview,
                totalChild = (net ?: 0L) + (transportRow?.annualAmount ?: 0L),
            )
        }

        return BillingSimulation(
            children = perChild,
            tuitionGrossTotal = perChild.sumOf { it.tuitionGross ?: 0L },
            discountsTotal = perChild.sumOf { it.discounts },
            tuitionNetTotal = perChild.sumOf { it.netTuition },
            transportTotal = (transportRow?.annualAmount ?: 0L) * children.size,
            grandTotal = perChild.sumOf { it.totalChild },
            hasMissingTuition = perChild.any { it.tuitionGross == null },
        )
    }

    fun register(parent: CreateParentInput, students: List<CreateStudentInput>, onSuccess: () -> Unit) {
        // A parent is valid when EITHER (firstName + lastName) is non-blank OR
        // displayName is non-blank. The importer path stores the full NOM
        // column as `displayName` with empty firstName (migration 0027), so
        // a parent with only `displayName` set is a legitimate record that
        // must pass validation. The previous check rejected these.
        val hasName = parent.displayName?.isNotBlank() == true ||
            (parent.firstName.isNotBlank() && parent.lastName.isNotBlank())
        if (!hasName || parent.phone.isBlank()) {
            _error.value = "Veuillez renseigner le nom complet (ou prénom + nom) et téléphone du parent"
            return
        }
        if (students.isEmpty()) {
            _error.value = "Au moins un élève est requis"
            return
        }
        // Vault §04.01 (Parent-First Entity Dependency): the UI must enforce
        // the dependency VISUALLY before submission — every child block needs
        // at least a first name + birth date so no partial student rows are
        // submitted inside the atomic batch.
        val invalidChild = students.withIndex().firstOrNull { (_, s) ->
            s.firstName.isBlank() || s.birthDate.isBlank()
        }
        if (invalidChild != null) {
            _error.value = "Enfant ${invalidChild.index + 1} : prénom et date de naissance sont requis"
            return
        }
        // T-320 (UI-310): a blank gradeLevel would make `getTuitionByGrade`
        // return null and the repository would register the child with ZERO
        // tuition charges, silently. Block it here — defence in depth with
        // the wizard's per-step validation.
        val noGrade = students.withIndex().firstOrNull { (_, s) -> s.gradeLevel.isBlank() }
        if (noGrade != null) {
            _error.value = "Enfant ${noGrade.index + 1} : le niveau scolaire est requis (sinon aucun frais de scolarité ne peut être facturé)"
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = studentRepository.batchRegister(parent, students, actorId, actorName)) {
                is Result.Ok -> {
                    _isLoading.value = false
                    _activationCode.value = result.value.activationCode
                    // T-320: surface the generated codes on the success screen
                    // (the data was always in BatchRegisterResult — it was just dropped).
                    _generatedParentCode.value = result.value.parent.code
                    _registeredStudentCodes.value = result.value.students.map { s ->
                        val name = s.displayName ?: "${s.firstName} ${s.lastName}".trim()
                        "$name (${s.code})"
                    }
                }
                is Result.Err -> {
                    _isLoading.value = false
                    _error.value = result.error.userMessage
                }
            }
        }
    }

    /** T-320: explicit reset from the success screen ("Nouvelle inscription"). */
    fun resetWizard() {
        _currentStep.value = 1
        _children.value = listOf(ChildFormState())
        _selectedChildIndex.value = 0
        _error.value = null
        _activationCode.value = null
        _generatedParentCode.value = null
        _registeredStudentCodes.value = emptyList()
    }
}
