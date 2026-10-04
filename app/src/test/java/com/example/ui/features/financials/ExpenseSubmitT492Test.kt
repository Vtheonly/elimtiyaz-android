package com.example.ui.features.financials

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.example.core.Errors
import com.example.core.Permission
import com.example.core.Result
import com.example.core.Role
import com.example.core.Session
import com.example.domain.model.Expense
import com.example.domain.repository.AuthRepository
import com.example.domain.repository.ExpenseRepository
import com.example.domain.repository.SubmitExpenseInput
import com.example.session.SessionManager
import com.example.ui.designsystem.theme.ElImtiyazTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T-492 (UI-331) — the honest submit form, against the OWNER's exact
 * scenario. Their report: "The expenses section does not work (button grayed
 * out)" — the screenshot showed Description/Montant/Catégorie/Bénéficiaire/
 * Urgence filled, the required Titre scrolled OUT of the viewport, and a
 * silently-disabled submit button. The Robolectric render probe proved the
 * form's semantics tree complete — the defect was the interaction design.
 *
 * The contract under test (the desktop's expense-submit-modal parity):
 *  1. the button is ENABLED whenever not submitting (NEVER silently grey);
 *  2. the under-button helper names the missing required fields AT ALL TIMES;
 *  3. an invalid TAP surfaces the per-field errors + the banner naming the
 *     fields (the exact desktop zod messages);
 *  4. the owner's scenario completed with the title → the submit fires with
 *     the desktop-parity payload.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ExpenseSubmitT492Test {

    @get:Rule val composeTestRule = createComposeRule()

    private class FakeAuthRepository : AuthRepository {
        val session = MutableStateFlow<Session?>(null)
        override suspend fun signIn(email: String, password: String): Result<Session> =
            Result.Err(Errors.unknown("not needed"))
        override suspend fun signOut(): Result<Unit> = Result.Ok(Unit)
        override suspend fun refreshSession(): Result<Session?> = Result.Ok(null)
        override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = Result.Ok(Unit)
        override fun observeSession(): Flow<Session?> = session
    }

    private class RecordingExpenseRepository : ExpenseRepository {
        val submitted = mutableListOf<SubmitExpenseInput>()
        override fun observe(): Flow<List<Expense>> = flowOf(emptyList())
        override fun observeByStatus(status: String): Flow<List<Expense>> = flowOf(emptyList())
        override fun observeById(id: String): Flow<Expense?> = flowOf(null)
        override suspend fun submit(input: SubmitExpenseInput, actorId: String, actorName: String): Result<Expense> {
            submitted.add(input)
            return Result.Err(Errors.unknown("offline test")) // blocks onSuccess — the payload is what we pin
        }
        override suspend fun approve(id: String, note: String, actorId: String, actorName: String): Result<Expense> =
            Result.Err(Errors.unknown("offline test"))
        override suspend fun reject(id: String, reason: String, actorId: String, actorName: String): Result<Expense> =
            Result.Err(Errors.unknown("offline test"))
        override suspend fun disburse(id: String, actorId: String, actorName: String): Result<Expense> =
            Result.Err(Errors.unknown("offline test"))
        override suspend fun settleProof(id: String, proofPath: String, finalAmount: Long, actorId: String, actorName: String): Result<Expense> =
            Result.Err(Errors.unknown("offline test"))
    }

    private val recordingRepo = RecordingExpenseRepository()

    private fun buildViewModel(): ExpenseSubmitViewModel {
        val auth = FakeAuthRepository()
        val sm = SessionManager(auth)
        sm.setSession(
            Session(
                userId = "usr-t492", tenantId = "tenant-1", email = "t@elimtiyaz.dz",
                displayName = "Test Owner", avatarUrl = null, role = Role.SUPER_ADMIN,
                permissions = Permission.entries.toSet(), accessToken = "tok",
                refreshToken = null, expiresAt = System.currentTimeMillis() + 3_600_000L,
                locale = "fr",
            ),
        )
        return ExpenseSubmitViewModel(recordingRepo, sm)
    }

    /** The editable text fields in FORM ORDER: Titre, Description, Montant, Bénéficiaire. */
    private fun editableFields() = composeTestRule.onAllNodes(hasSetTextAction())

    private fun setContent(vm: ExpenseSubmitViewModel = buildViewModel()) {
        composeTestRule.setContent {
            ElImtiyazTheme(darkTheme = true) {
                ExpenseSubmitScreen(onBack = {}, viewModel = vm)
            }
        }
        composeTestRule.waitForIdle()
    }

    /** Fill the form EXACTLY as the owner did (Titre left blank — out of their viewport). */
    private fun fillTheOwnersScenario() {
        // Field 1 = Titre (left BLANK — the owner's trap), field 2 = Description,
        // field 3 = Montant, field 4 = Bénéficiaire.
        editableFields()[1].performScrollTo()
        editableFields()[1].performTextInput("dxxxxxxxxx")
        editableFields()[2].performScrollTo()
        editableFields()[2].performTextInput("5000000")
        editableFields()[3].performScrollTo()
        editableFields()[3].performTextInput("dxxxxxxxxx")
        composeTestRule.waitForIdle()
    }

    // ─── 1. the button is NEVER silently disabled ────────────────────────

    @Test
    fun `the owners scenario - the button stays tappable`() {
        setContent()
        fillTheOwnersScenario()

        // THE FIX: before T-492 the button read the untracked canSubmit
        // getter in a scope that never invalidates on typing — it computed
        // disabled at first composition and STAYED that way forever (the
        // reported "grayed out"). Now it is ENABLED (only submitting
        // disables it) and the state-driven helper says WHY the form is
        // incomplete — it RECOMPOSES on every keystroke (the old untracked
        // read would still say all three fields here).
        composeTestRule.onNodeWithText("Soumettre la dépense")
            .performScrollTo()
            .assertIsEnabled()
        composeTestRule.onAllNodesWithText("Champs obligatoires manquants : Titre")[0].assertExists()
    }

    // ─── 2. the invalid tap surfaces the desktop-parity errors ───────────

    @Test
    fun `an invalid tap shows the field errors and the banner`() {
        val vm = buildViewModel()
        setContent(vm)
        fillTheOwnersScenario()

        composeTestRule.onNodeWithText("Soumettre la dépense").performScrollTo()
        composeTestRule.onNodeWithText("Soumettre la dépense").performClick()
        composeTestRule.waitForIdle()

        // The banner names the missing fields (the owner's screen said nothing).
        // NOTE: the banner AND the under-button helper render the same line —
        // exactly TWO nodes carry it after the tap (the banner's message +
        // the helper), which is the intended redundancy (visible at the top
        // AND at the button).
        assertEquals(2, composeTestRule.onAllNodesWithText("Champs obligatoires manquants : Titre").fetchSemanticsNodes().size)
        // The per-field error renders the DESKTOP's exact zod message.
        composeTestRule.onNodeWithText("Titre requis (min. 3 caractères)").assertExists()
        // The repository was NOT called (the validation held the submit).
        assertTrue(recordingRepo.submitted.isEmpty())
        // The validation state flipped.
        assertTrue(vm.validationAttempted.value)
    }

    @Test
    fun `every missing field is named in form order`() {
        setContent()
        // NOTHING filled: the helper names all three.
        composeTestRule.onNodeWithText("Soumettre la dépense").performScrollTo()
        composeTestRule.onNodeWithText("Champs obligatoires manquants : Titre, Montant, Bénéficiaire")
            .assertExists()
    }

    @Test
    fun `the desktop zod rules - short title, single-char payee, zero amount`() {
        val vm = buildViewModel()
        setContent(vm)

        editableFields()[0].performScrollTo()
        editableFields()[0].performTextInput("ab") // 2 chars < min 3
        editableFields()[2].performScrollTo()
        editableFields()[2].performTextInput("0")
        editableFields()[3].performScrollTo()
        editableFields()[3].performTextInput("x") // 1 char < min 2
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Soumettre la dépense").performScrollTo()
        composeTestRule.onNodeWithText("Soumettre la dépense").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Titre requis (min. 3 caractères)").assertExists()
        composeTestRule.onNodeWithText("Montant supérieur à 0 requis").assertExists()
        composeTestRule.onNodeWithText("Bénéficiaire requis").assertExists()
        composeTestRule.onAllNodesWithText("Champs obligatoires manquants : Titre, Montant, Bénéficiaire")[0].assertExists()
        assertTrue(recordingRepo.submitted.isEmpty())
    }

    // ─── 3. the completed owner scenario submits ─────────────────────────

    @Test
    fun `completing the title fires the submit with the desktop-parity payload`() {
        setContent()
        fillTheOwnersScenario()
        // The owner completes the ONE missing field.
        editableFields()[0].performScrollTo()
        editableFields()[0].performTextInput("Réparation climatisation")
        composeTestRule.waitForIdle()

        // The helper line is gone.
        composeTestRule.onAllNodesWithText("Champs obligatoires manquants : Titre")
            .fetchSemanticsNodes().isEmpty()

        composeTestRule.onNodeWithText("Soumettre la dépense").performScrollTo()
        composeTestRule.onNodeWithText("Soumettre la dépense").performClick()
        composeTestRule.waitForIdle()

        // The repository got the call with the EXACT owner values (the amount
        // in centimes via elMoneyParse — 5 000 000 DZD = 500 000 000 c).
        assertEquals(1, recordingRepo.submitted.size)
        val input = recordingRepo.submitted.first()
        assertEquals("Réparation climatisation", input.title)
        assertEquals("dxxxxxxxxx", input.description)
        assertEquals(500_000_000L, input.amount)
        assertEquals("dxxxxxxxxx", input.payee)
    }

    // ─── 4. the form's structure (the probe's pinned finding) ────────────

    @Test
    fun `the full form renders with every field and the section header`() {
        setContent()
        composeTestRule.onNodeWithText("Informations de la dépense").assertExists()
        composeTestRule.onNodeWithText("Titre *").assertExists()
        composeTestRule.onNodeWithText("Description").assertExists()
        composeTestRule.onNodeWithText("Montant (DZD) *").assertExists()
        composeTestRule.onNodeWithText("Catégorie *").assertExists()
        composeTestRule.onNodeWithText("Bénéficiaire *").assertExists()
        composeTestRule.onNodeWithText("Urgence").assertExists()
    }
}
