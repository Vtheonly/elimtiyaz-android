package com.example.ui.features.financials

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.SubmitExpenseInput
import com.example.session.SessionManager
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.input.ElDropdown
import com.example.ui.designsystem.components.input.ElDropdownOption
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.foundation.elMoneyParse
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.ui.designsystem.overlays.LocalElToast
import com.example.ui.designsystem.theme.ElTheme

/**
 * Expense submission form ViewModel.
 *
 * Restored behavior (commit a34333a + desktop §08):
 *  - Validates title non-blank, amount > 0, payee non-blank.
 *  - Controlled category enum (no free-text).
 *  - Calls `expenseRepository.submit(input, actorId, actorName)`.
 *  - Audit logging happens inside the repository (server-side RPC `submit_expense`).
 *
 * T-322 fix: the amount is parsed with [elMoneyParse] (EXACT centimes) — the
 * previous `toDouble() * 100` rounded 15.07 DZD to 1506¢ (a financial
 * correctness bug). canSubmit uses the same parser.
 *
 * T-492 (UI-331) — DESKTOP-PARITY VALIDATION + THE HONEST DISABLED STATE.
 * The owner's report: "The expenses section does not work (button grayed
 * out)" — their screenshot showed every VISIBLE field filled while the
 * required Titre sat scrolled OUT of the viewport and the disabled button
 * said nothing. The desktop modal validates with per-field zod messages
 * ("Titre requis (min. 3 caractères)" / "Montant supérieur à 0 requis" /
 * "Bénéficiaire requis") — this ViewModel now mirrors those exact rules and
 * exposes per-field error state so the screen can render them, plus the
 * missing-field list for the under-button helper (the button is never
 * silently disabled anymore: it stays tappable and an invalid tap surfaces
 * the errors).
 */
@HiltViewModel
class ExpenseSubmitViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _description = MutableStateFlow("")
    val description: StateFlow<String> = _description.asStateFlow()

    private val _amount = MutableStateFlow("")
    val amount: StateFlow<String> = _amount.asStateFlow()

    private val _category = MutableStateFlow(ExpenseCategoryOptions.Supplies)
    val category: StateFlow<String> = _category.asStateFlow()

    private val _payee = MutableStateFlow("")
    val payee: StateFlow<String> = _payee.asStateFlow()

    private val _urgency = MutableStateFlow("normal")
    val urgency: StateFlow<String> = _urgency.asStateFlow()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** T-492 (UI-331): per-field errors render only after a submit attempt — the fields stay calm while typing. */
    private val _validationAttempted = MutableStateFlow(false)
    val validationAttempted: StateFlow<Boolean> = _validationAttempted.asStateFlow()

    /**
     * T-492 (UI-331) — THE STALE-ENABLED-STATE FIX. The original screen read
     * `viewModel.canSubmit` (a plain getter) inside the BUTTON's recompose
     * scope — a scope that reads NO field state, so it NEVER invalidated on
     * typing: the button computed `enabled=false` at first composition and
     * STAYED disabled no matter what the user typed (the owner's "button
     * grayed out" — unreachable-by-typing). Every UI-affecting derivation
     * now flows through StateFlow so the recomposition is driven by
     * snapshot state: the missing-field list below (the helper line), the
     * per-field errors (rendered after the attempt flag flips a tracked
     * state), and the button's enabled = !isSubmitting (a tracked state).
     */
    val missingFields: StateFlow<List<String>> = combine(_title, _amount, _payee) { t, a, p ->
        buildList {
            if (t.trim().length < 3) add("Titre")
            if (elMoneyParse(a) <= 0L) add("Montant")
            if (p.trim().length < 2) add("Bénéficiaire")
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.Lazily,
        // The honest empty-form initial (the first combine emission lands
        // the moment the screen collects).
        listOf("Titre", "Montant", "Bénéficiaire"),
    )

    fun titleChanged(v: String) { _title.value = v }
    fun descriptionChanged(v: String) { _description.value = v }
    fun amountChanged(v: String) { _amount.value = v.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' || ch == ' ' } }
    fun categoryChanged(v: String) { _category.value = v }
    fun payeeChanged(v: String) { _payee.value = v }
    fun urgencyChanged(v: String) { _urgency.value = v }

    // ── T-492: the desktop's zod rules, verbatim ──────────────────────
    //   title: z.string().min(3, "Titre requis (min. 3 caractères)")
    //   amount: z.number().min(1, "Montant supérieur à 0 requis")
    //   payee: z.string().min(2, "Bénéficiaire requis")
    // ─────────────────────────────────────────────────────────────────
    // The per-field error getters (used by the screen's errorText params —
    // read inside the FIELD scopes, which DO invalidate on typing — and by
    // the tests). The button's own scope consumes the state-driven
    // missingFields flow above, never these untracked getters.

    val titleError: String?
        get() = if (_title.value.trim().length < 3) "Titre requis (min. 3 caractères)" else null

    val amountError: String?
        get() = if (elMoneyParse(_amount.value) <= 0L) "Montant supérieur à 0 requis" else null

    val payeeError: String?
        get() = if (_payee.value.trim().length < 2) "Bénéficiaire requis" else null

    val canSubmit: Boolean
        get() = titleError == null && amountError == null && payeeError == null && !_isSubmitting.value

    /**
     * T-492 (UI-331): the button's click handler — NEVER a silent no-op. An
     * invalid tap marks the attempt (the fields render their errors, the
     * banner names them) and returns; a valid tap submits.
     */
    fun submit(onSuccess: (String) -> Unit) {
        if (!canSubmit) {
            _validationAttempted.value = true
            _error.value = "Champs obligatoires manquants : ${missingFields.value.joinToString(", ")}"
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            val input = SubmitExpenseInput(
                title = _title.value.trim(),
                description = _description.value.trim(),
                amount = elMoneyParse(_amount.value), // EXACT centimes (T-322)
                category = _category.value,
                payee = _payee.value.trim(),
                urgency = _urgency.value,
            )
            val actorId = sessionManager.currentUserId() ?: "system"
            val actorName = sessionManager.currentDisplayName() ?: "System"
            when (val result = expenseRepository.submit(input, actorId, actorName)) {
                is Result.Ok -> {
                    _isSubmitting.value = false
                    onSuccess(result.value.requestCode)
                }
                is Result.Err -> {
                    _isSubmitting.value = false
                    _error.value = result.error.userMessage
                }
            }
        }
    }

    fun clearError() { _error.value = null }
}

/** Canonical expense categories (per desktop migration 0008). */
object ExpenseCategoryOptions {
    const val Utilities = "utilities"
    const val Supplies = "supplies"
    const val Maintenance = "maintenance"
    const val Transport = "transport"
    const val Event = "event"
    const val Salary = "salary"
    const val Tax = "tax"
    const val Rent = "rent"
    const val Other = "other"

    val AllLabels: List<String> = listOf(
        "Utilities", "Fournitures", "Maintenance", "Transport",
        "Événement", "Salaires", "Taxes", "Loyer", "Autre",
    )

    private val LabelToCode = mapOf(
        "Utilities" to Utilities,
        "Fournitures" to Supplies,
        "Maintenance" to Maintenance,
        "Transport" to Transport,
        "Événement" to Event,
        "Salaires" to Salary,
        "Taxes" to Tax,
        "Loyer" to Rent,
        "Autre" to Other,
    )

    fun labelFor(code: String): String = LabelToCode.entries.firstOrNull { it.value == code }?.key ?: "Autre"
    fun codeFor(label: String): String = LabelToCode[label] ?: Other
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ExpenseSubmitScreen(
    onBack: () -> Unit,
    viewModel: ExpenseSubmitViewModel = hiltViewModel(),
) {
    val title by viewModel.title.collectAsState()
    val description by viewModel.description.collectAsState()
    val amount by viewModel.amount.collectAsState()
    val category by viewModel.category.collectAsState()
    val payee by viewModel.payee.collectAsState()
    val urgency by viewModel.urgency.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val error by viewModel.error.collectAsState()
    // T-492 (UI-331): the validation state — collected as State so the
    // error rendering recomposes when an invalid tap flips the flag.
    val validationAttempted by viewModel.validationAttempted.collectAsState()
    // T-492 (UI-331): THE STALE-ENABLED-STATE FIX — the missing-field list
    // is a STATE FLOW collected here (the button's scope), so the helper
    // line recomposes on every keystroke. The original screen read the
    // untracked `viewModel.canSubmit` getter in this scope — it never
    // invalidated on typing and the disabled button was unreachable.
    val missingFields by viewModel.missingFields.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val toast = LocalElToast.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // T-492 (UI-331): bringIntoView — an invalid tap scrolls the FIRST
    // offending field into the viewport (the owner's trap: Titre sat
    // scrolled out while the button said nothing).
    val titleBringIntoView = remember { BringIntoViewRequester() }
    val amountBringIntoView = remember { BringIntoViewRequester() }
    val payeeBringIntoView = remember { BringIntoViewRequester() }

    val onAttemptSubmit: () -> Unit = {
        val beforeAttempt = !viewModel.canSubmit
        viewModel.submit { requestCode ->
            // T-460 H2 (F-18): the app-root ElToastHost renders this
            // ABOVE the nav host, so the toast survives the pop — the
            // same lifetime property the platform toast had
            // (a screen-scoped snackbar would be destroyed).
            toast.showSuccess("Dépense $requestCode soumise pour approbation", durationMs = 4000)
            onBack()
        }
        // The failed-attempt scroll: the FIRST missing field comes into
        // view (BringIntoViewRequester — same-frame after the state flip).
        if (beforeAttempt && missingFields.isNotEmpty()) {
            scope.launch {
                when (missingFields.firstOrNull()) {
                    "Titre" -> titleBringIntoView.bringIntoView()
                    "Montant" -> amountBringIntoView.bringIntoView()
                    "Bénéficiaire" -> payeeBringIntoView.bringIntoView()
                }
            }
        }
    }

    ElScaffold(
        topBar = {
            ElTopBar(
                title = "Nouvelle dépense",
                subtitle = "Soumission au workflow d'approbation",
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(ElTheme.spacing.lg)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
        ) {
            error?.let {
                ElAlertBanner(
                    title = "Erreur de soumission",
                    message = it,
                    severity = ElAlertSeverity.DANGER,
                    onDismiss = viewModel::clearError,
                )
            }

            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ElTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
                ) {
                    ElSectionHeader(title = "Informations de la dépense")

                    ElTextField(
                        value = title,
                        onValueChange = viewModel::titleChanged,
                        label = "Titre *",
                        placeholder = "Ex : Achat de fournitures pédagogiques",
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(titleBringIntoView),
                        isError = validationAttempted && viewModel.titleError != null,
                        errorText = if (validationAttempted) viewModel.titleError else null,
                    )

                    ElTextField(
                        value = description,
                        onValueChange = viewModel::descriptionChanged,
                        label = "Description",
                        placeholder = "Précisez l'usage, la classe, le fournisseur…",
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                    )

                    ElTextField(
                        value = amount,
                        onValueChange = viewModel::amountChanged,
                        label = "Montant (DZD) *",
                        placeholder = "Ex : 12500,50",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(amountBringIntoView),
                        isError = validationAttempted && viewModel.amountError != null,
                        errorText = if (validationAttempted) viewModel.amountError else null,
                    )

                    ElDropdown(
                        options = ExpenseCategoryOptions.AllLabels.map {
                            ElDropdownOption(value = ExpenseCategoryOptions.codeFor(it), label = it)
                        },
                        selectedValue = category,
                        onSelected = { option -> viewModel.categoryChanged(option.value) },
                        label = "Catégorie *",
                        modifier = Modifier.fillMaxWidth(),
                    )

                    ElTextField(
                        value = payee,
                        onValueChange = viewModel::payeeChanged,
                        label = "Bénéficiaire *",
                        placeholder = "Ex : Librairie En-Nour",
                        modifier = Modifier
                            .fillMaxWidth()
                            .bringIntoViewRequester(payeeBringIntoView),
                        isError = validationAttempted && viewModel.payeeError != null,
                        errorText = if (validationAttempted) viewModel.payeeError else null,
                    )

                    ElDropdown(
                        options = listOf(
                            "Normale" to "normal",
                            "Basse" to "low",
                            "Moyenne" to "medium",
                            "Haute" to "high",
                            "Critique" to "critical",
                        ).map { ElDropdownOption(value = it.second, label = it.first) },
                        selectedValue = urgency,
                        onSelected = { option -> viewModel.urgencyChanged(option.value) },
                        label = "Urgence",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            ElCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(ElTheme.spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs),
                ) {
                    Text(
                        "Workflow d'approbation",
                        style = ElTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(ElTheme.spacing.xs))
                    Text(
                        "1. Soumise → 2. Approuvée → 3. Décaissée → 4. Justificatif téléversé.\n" +
                            "Règle de séparation des tâches : l'auto-approbation est strictement interdite (plan §08).\n" +
                            "Le justificatif est obligatoire avant clôture.",
                        style = ElTheme.typography.bodySmall,
                    )
                }
            }

            Spacer(Modifier.height(ElTheme.spacing.sm))

            // T-492 (UI-331): the button is NEVER silently disabled — it
            // stays tappable whenever it is not submitting; an invalid tap
            // surfaces the per-field errors + the banner + the scroll to
            // the first offender. The helper line under it names the
            // missing fields AT ALL TIMES (the owner filled every VISIBLE
            // field and the grey button said nothing — never again).
            ElButton(
                text = if (isSubmitting) "Soumission…" else "Soumettre la dépense",
                onClick = onAttemptSubmit,
                variant = ElButtonVariant.PRIMARY,
                enabled = !isSubmitting,
                loading = isSubmitting,
                icon = Icons.AutoMirrored.Filled.Send,
                fullWidth = true,
            )
            if (!isSubmitting && missingFields.isNotEmpty()) {
                Text(
                    "Champs obligatoires manquants : ${missingFields.joinToString(", ")}",
                    style = ElTheme.typography.labelSmall,
                    color = ElTheme.colors.warning,
                )
            }

            Spacer(Modifier.height(ElTheme.spacing.xl))
        }
    }
}
