package com.example.ui.components

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.domain.model.AuditLog
import com.example.ui.designsystem.theme.ElImtiyazTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T-297 (OFFLINE-400) — the rendering-state test for the real Android
 * audit-diff sheet (the fabricated "Inspecteur JSON" replacement).
 *
 * Mirrors the desktop drawer suite (src/tests/features/
 * t-296-audit-diff-drawer.test.tsx, commit 86dcf77) at the semantic
 * level: actor attribution (Name + Account ID + Role), the red/green
 * field rows (old struck / new), the INSERT all-green + DELETE all-red
 * honesty, the summary badges, the no-snapshot empty state, the
 * nested-array path rendering, and the raw forensic toggle.
 *
 * SEMANTIC assertions, not screenshots (ARCH-012 — same discipline as
 * ElScrollableTabRowTest): the content composable is sheet-chrome-free
 * and `verticalScroll`-based (NOT lazy), so every row is composed in the
 * semantics tree without viewport gymnastics.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AuditDiffSheetTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun auditLog(
        beforeJson: String?,
        afterJson: String?,
        actorRole: String? = "finance",
    ) = AuditLog(
        id = "aud-001",
        tenantId = "tenant-1",
        action = "payment.update",
        entityType = "payment",
        entityId = "pay-001",
        actorId = "acc-9f2a7c31",
        actorName = "Yacine Benali",
        actorRole = actorRole,
        beforeJson = beforeJson,
        afterJson = afterJson,
        note = null,
        ipAddress = null,
        userAgent = null,
        occurredAt = "2026-09-11T10:00:00Z",
    )

    private fun setContent(log: AuditLog) {
        composeTestRule.setContent {
            ElImtiyazTheme {
                AuditDiffSheetContent(log = log)
            }
        }
        composeTestRule.waitForIdle()
    }

    // ─── Actor attribution + changed-field rendering ─────────────────

    @Test
    fun `actor attribution block renders name account id and role`() {
        setContent(auditLog(beforeJson = """{"a":1}""", afterJson = """{"a":2}"""))
        composeTestRule.onNodeWithTag("audit_diff_actor_name").assertExists()
        composeTestRule.onNodeWithText("Yacine Benali").assertExists()
        composeTestRule.onNodeWithTag("audit_diff_actor_id").assertExists()
        composeTestRule.onNodeWithText("acc-9f2a7c31").assertExists()
        composeTestRule.onNodeWithTag("audit_diff_actor_role").assertExists()
        composeTestRule.onNodeWithText("finance").assertExists()
    }

    @Test
    fun `null role renders the honest placeholder`() {
        setContent(auditLog(beforeJson = """{"a":1}""", afterJson = """{"a":2}""", actorRole = null))
        composeTestRule.onNodeWithText("rôle non enregistré").assertExists()
    }

    // ─── T-308 (48th session): the red/green TABLE presentation ──────

    @Test
    fun `the diff renders as a TABLE with the Champ Avant Après header`() {
        setContent(
            auditLog(
                beforeJson = """{"id":"pay-001","status":"pending","amount":2500000}""",
                afterJson = """{"id":"pay-001","status":"paid","amount":2500000}""",
            )
        )
        // The table container + the 3 column headers (uppercased).
        composeTestRule.onNodeWithTag("audit_diff_table").assertExists()
        composeTestRule.onNodeWithText("CHAMP").assertExists()
        composeTestRule.onNodeWithText("AVANT (ANCIEN)").assertExists()
        composeTestRule.onNodeWithText("APRÈS (NOUVEAU)").assertExists()
        // The field label renders in the Champ column.
        composeTestRule.onNodeWithTag("audit_diff_field").assertExists()
    }

    @Test
    fun `nested paths show the short field label AND the full path caption`() {
        setContent(
            auditLog(
                beforeJson = """{"allocation":{"installments":[{"applied":0}]}}""",
                afterJson = """{"allocation":{"installments":[{"applied":9}]}}""",
            )
        )
        // Champ column: short label primary + dotted path caption.
        composeTestRule.onNodeWithTag("audit_diff_field").assertExists()
        composeTestRule.onNodeWithText("applied").assertExists()
        composeTestRule.onNodeWithTag("audit_diff_row_path").assertExists()
        composeTestRule.onNodeWithText("allocation.installments[0].applied").assertExists()
    }

    @Test
    fun `changed field renders old value red and new value green`() {
        setContent(
            auditLog(
                beforeJson = """{"id":"pay-001","status":"pending","amount":2500000}""",
                afterJson = """{"id":"pay-001","status":"paid","amount":2500000}""",
            )
        )
        composeTestRule.onNodeWithText("pending").assertExists()
        composeTestRule.onNodeWithText("paid").assertExists()
        composeTestRule.onNodeWithText("status").assertExists()
        // Exactly one old-value chip and one new-value chip (one changed field).
        composeTestRule.onAllNodes(hasTestTag("diff-old-value")).assertCountEquals(1)
        composeTestRule.onAllNodes(hasTestTag("diff-new-value")).assertCountEquals(1)
        // Summary: 1 modifié — and no added/removed badges.
        composeTestRule.onNodeWithText("1 modifié").assertExists()
        composeTestRule.onNodeWithText("supprimé", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("ajouté", substring = true).assertDoesNotExist()
    }

    // ─── INSERT / DELETE honesty ─────────────────────────────────────

    @Test
    fun `INSERT renders all-green added rows with no old-value chips`() {
        setContent(
            auditLog(
                beforeJson = null,
                afterJson = """{"id":"p1","name":"Ahmed","amount":1000}""",
            )
        )
        composeTestRule.onAllNodes(hasTestTag("diff-old-value")).assertCountEquals(0)
        composeTestRule.onAllNodes(hasTestTag("diff-new-value")).assertCountEquals(3)
        composeTestRule.onNodeWithText("3 ajoutés").assertExists()
        composeTestRule.onNodeWithText("modifié", substring = true).assertDoesNotExist()
    }

    @Test
    fun `DELETE renders all-red removed rows with no new-value chips`() {
        setContent(
            auditLog(
                beforeJson = """{"id":"p1","name":"Ahmed"}""",
                afterJson = null,
            )
        )
        composeTestRule.onAllNodes(hasTestTag("diff-new-value")).assertCountEquals(0)
        composeTestRule.onAllNodes(hasTestTag("diff-old-value")).assertCountEquals(2)
        composeTestRule.onNodeWithText("2 supprimés").assertExists()
    }

    // ─── Nested / array paths ────────────────────────────────────────

    @Test
    fun `nested array paths render with bracketed index notation`() {
        setContent(
            auditLog(
                beforeJson = """{"id":"pay-001","status":"pending","amount":2500000,
                  "allocation":{"installments":[{"id":"i1","applied":0}]}}""",
                afterJson = """{"id":"pay-001","status":"paid","amount":2500000,
                  "allocation":{"installments":[{"id":"i1","applied":2500000}]}}""",
            )
        )
        composeTestRule.onNodeWithText("allocation.installments[0].applied").assertExists()
        composeTestRule.onNodeWithText("0").assertExists()
        composeTestRule.onNodeWithText("2500000").assertExists()
        composeTestRule.onNodeWithText("2 modifiés").assertExists()
    }

    // ─── Empty-state honesty ─────────────────────────────────────────

    @Test
    fun `no snapshots renders the honest empty state`() {
        setContent(auditLog(beforeJson = null, afterJson = null))
        composeTestRule.onNodeWithText(
            "Aucun instantané avant/après enregistré pour cette entrée.",
        ).assertExists()
        composeTestRule.onAllNodes(hasTestTag("diff-old-value")).assertCountEquals(0)
        composeTestRule.onAllNodes(hasTestTag("diff-new-value")).assertCountEquals(0)
    }

    @Test
    fun `structurally equal snapshots render no rows`() {
        val same = """{"id":"pay-001","status":"paid","amount":2500000}"""
        setContent(auditLog(beforeJson = same, afterJson = same))
        composeTestRule.onNodeWithText("aucune différence structurelle").assertExists()
        composeTestRule.onNodeWithText(
            "Avant et après sont structurellement identiques (aucun champ modifié).",
        ).assertExists()
    }

    // ─── Raw forensic toggle ─────────────────────────────────────────

    @Test
    fun `raw forensic view expands on toggle and shows the snapshots`() {
        setContent(
            auditLog(
                beforeJson = """{"id":"pay-001","status":"pending"}""",
                afterJson = """{"id":"pay-001","status":"paid"}""",
            )
        )
        // Collapsed by default: the raw payload with quotes is NOT rendered.
        composeTestRule.onNodeWithText("\"status\":\"pending\"", substring = true)
            .assertDoesNotExist()
        composeTestRule.onNodeWithText("JSON brut (forensique)").performClick()
        composeTestRule.waitForIdle()
        // Expanded: the raw before payload is now visible (the quoted form
        // distinguishes it from the chip's plain "pending").
        composeTestRule.onNodeWithText("\"status\":\"pending\"", substring = true)
            .assertExists()
    }
}
