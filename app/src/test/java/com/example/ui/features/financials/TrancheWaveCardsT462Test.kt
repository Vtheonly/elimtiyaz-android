package com.example.ui.features.financials

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import com.example.domain.model.ExecNonWaveItem
import com.example.domain.model.ExecNonWaveKindItem
import com.example.domain.model.ExecPooledWaveItem
import com.example.domain.model.ExecWaveItem
import com.example.domain.model.ExecWaveStatsItem
import com.example.domain.model.TrancheStripTotalsItem
import com.example.domain.model.TrancheWaveItem
import com.example.ui.designsystem.theme.ElImtiyazTheme
import com.example.ui.features.dashboard.analytics.WaveVelocityCard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * T-462 (UI-327) — the Tranche-card modernization contract.
 *
 * TWO verification layers (both required by the task's definition of done —
 * "keep the exact same data and functionality, fix only the presentation"):
 *
 * 1. RENDERING pins (createComposeRule, the AuditDiffSheetTest discipline):
 *    both tranche surfaces render with the SAME content set as before the
 *    restyle — every key figure, label, badge, échéance line, identity line
 *    and per-category chip that the owner's screenshots show must still be
 *    present. What the restyle changed is WHERE and HOW they render (tiles,
 *    semantic colors, the DS meter) — the CONTENT is pinned byte-for-byte.
 *
 * 2. SOURCE-pattern pins (the SpacingTokenGateT460F19aTest discipline —
 *    these guard the PRESENTATION invariants so a future sweep cannot
 *    silently regress them): the pooled meter consumes the DS
 *    ElLinearProgress (never a hand-rolled Box pair), the status text of
 *    both surfaces resolves through the theme's SEMANTIC layer (never the
 *    chart palette as bare text color), and the app typeface is ElInter
 *    (Typography + ElTextStyles + the six bundled cuts).
 *
 * ARCH-012: the release exclusion list carries this class (createComposeRule
 * under the suffixed applicationId — the 4th-recurrence prevention rule,
 * added in the SAME commit as this test).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class TrancheWaveCardsT462Test {

    @get:Rule val composeTestRule = createComposeRule()

    // ── Test data (centimes; the amounts are display-only fixtures) ──────

    private fun stripWave(
        index: Int,
        pct: Int,
        overdue: Boolean,
        nextTarget: Boolean = false,
    ) = TrancheWaveItem(
        index = index,
        label = "Tranche $index (Septembre)",
        hint = "Échéance officielle : 15 sept.",
        due = 100_000_000_00L,
        paid = 77_000_000_00L,
        pending = 5_000_000_00L,
        remaining = 18_000_000_00L,
        pct = pct,
        tuitionPct = 64,
        isNextTarget = nextTarget,
        isOverdue = overdue,
        dueDate = "2025-09-15T00:00:00Z",
        dueDateMax = "2026-09-15T00:00:00Z",
    )

    private val stripTotals = TrancheStripTotalsItem(
        totalDue = 300_000_000_00L,
        totalPaid = 231_000_000_00L,
        totalRemaining = 54_000_000_00L,
        overdueCount = 507,
    )

    private fun pooledStats(category: String, label: String, remaining: Long = 23_000_000_00L) = ExecWaveStatsItem(
        category = category,
        categoryLabel = label,
        wave = 1,
        installmentCount = 683,
        settledCount = 265,
        familyCount = 400,
        debtorFamilyCount = 135,
        overdueDebtorFamilyCount = 120,
        dueTotal = 30_000_000_00L,
        paidTotal = 23_000_000_00L,
        pendingTotal = 1_000_000_00L,
        remainingTotal = remaining,
        dueDateMin = "2025-09-15T00:00:00Z",
        dueDateMax = "2026-09-15T00:00:00Z",
        anyUnsettledOverdue = true,
        anyUnsettledFuture = false,
    )

    private val pooledWave = ExecPooledWaveItem(
        wave = 1,
        installmentCount = 683,
        settledCount = 265,
        familyCount = 400,
        debtorFamilyCount = 135,
        overdueDebtorFamilyCount = 120,
        dueTotal = 30_000_000_00L,
        paidTotal = 23_000_000_00L,
        pendingTotal = 1_000_000_00L,
        remainingTotal = 23_000_000_00L,
        overCoverageTotal = 0L,
        collectedPct = 77,
        dueDateMin = "2025-09-15T00:00:00Z",
        dueDateMax = "2026-09-15T00:00:00Z",
        anyUnsettledOverdue = true,
        anyUnsettledFuture = false,
        perCategory = listOf(
            pooledStats("tuition", "Scolarité"),
            pooledStats("transport", "Transport", remaining = 616_000_00L),
        ),
    )

    private fun execWave() = ExecWaveItem(
        key = "tuition#1",
        category = "tuition",
        categoryLabel = "Scolarité",
        wave = 1,
        installmentCount = 640,
        paidCount = 260,
        familyCount = 380,
        debtorFamilyCount = 120,
        overdueDebtorFamilyCount = 118,
        dueTotal = 29_000_000_00L,
        paidTotal = 22_400_000_00L,
        remainingTotal = 22_400_000_00L,
        collectedPct = 77,
        clearedPct = 77,
        dueDate = "2025-09-15T00:00:00Z",
        dueDateMax = "2026-09-15T00:00:00Z",
    )

    private fun nonWave() = ExecNonWaveItem(
        kind = ExecNonWaveKindItem.FI,
        kindLabel = "fi",
        category = "fi",
        categoryLabel = "Frais d'inscription",
        installmentCount = 191,
        settledCount = 185,
        familyCount = 191,
        debtorFamilyCount = 6,
        overdueDebtorFamilyCount = 6,
        dueTotal = 4_000_000_00L,
        paidTotal = 3_876_000_00L,
        pendingTotal = 0L,
        remainingTotal = 124_000_00L,
        dueDateMin = "2026-09-15T00:00:00Z",
        dueDateMax = "2026-09-15T00:00:00Z",
        anyUnsettledOverdue = false,
    )

    // ── 1. Rendering pins — the Finance Tranches strip ────────────────────

    @Test
    fun `strip card renders every wave line and the totals tiles`() {
        composeTestRule.setContent {
            ElImtiyazTheme {
                TrancheWaveCard(
                    waves = listOf(
                        stripWave(1, pct = 77, overdue = true, nextTarget = true),
                        stripWave(2, pct = 37, overdue = true),
                    ),
                    totals = stripTotals,
                )
            }
        }
        composeTestRule.waitForIdle()

        // The header + basis line (T-427's explicit pooling basis).
        composeTestRule.onNodeWithText("Vagues de Tranches (T1 / T2 / T3)").assertExists()
        composeTestRule.onNodeWithText("cible").assertExists()

        // Per-wave: the label, the UNCLAMPED rate, the échéance line with
        // its days-late suffix, the Encaissé/Dû pair (both waves render it),
        // the tuition-isolated rate and the pending line — the full
        // pre-T-462 content contract.
        composeTestRule.onNodeWithText("Tranche 1 (Septembre)").assertExists()
        composeTestRule.onNodeWithText("77%").assertExists()
        composeTestRule.onNodeWithText("37%").assertExists()
        composeTestRule.onAllNodesWithText("Encaissé :", substring = true).assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Dû :", substring = true).assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Échéance : ", substring = true).assertCountEquals(2)
        composeTestRule.onAllNodesWithText(" j de retard", substring = true).assertCountEquals(2)
        composeTestRule.onAllNodesWithText("dont scolarité : 64%", substring = true).assertCountEquals(2)
        composeTestRule.onAllNodesWithText("Dont en attente (chèque / virement) : ", substring = true).assertCountEquals(2)

        // The 4-cell closing block (T-462: now the tile treatment — the
        // LABELS are the content contract).
        composeTestRule.onNodeWithText("Total dû").assertExists()
        composeTestRule.onNodeWithText("Payé").assertExists()
        composeTestRule.onNodeWithText("Reste").assertExists()
        composeTestRule.onNodeWithText("En retard").assertExists()
        composeTestRule.onNodeWithText("507").assertExists()
    }

    @Test
    fun `strip card keeps the honest zero state when no wave has rows`() {
        composeTestRule.setContent {
            ElImtiyazTheme {
                TrancheWaveCard(waves = emptyList(), totals = null)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Aucune tranche T1 / T2 / T3 dans la sélection courante", substring = true)
            .assertExists()
    }

    // ── 2. Rendering pins — the pooled wave meter (the dashboard hero) ────

    @Test
    fun `pooled meter renders the 2x3 grid identity chips and badge`() {
        composeTestRule.setContent {
            ElImtiyazTheme {
                WaveVelocityCard(
                    waves = listOf(execWave()),
                    pooledWaves = listOf(pooledWave),
                    nonWave = listOf(nonWave()),
                    variant = "full",
                )
            }
        }
        composeTestRule.waitForIdle()

        // The header + the global badges row (the "DA restant" substring
        // also matches the Hors-Tranches row and the detail grid row — one
        // global badge + one non-wave row + one detail row = three).
        composeTestRule.onNodeWithText("Vélocité de Recouvrement par Vague Saisonnière").assertExists()
        composeTestRule.onNodeWithText("% collecté global", substring = true).assertExists()
        composeTestRule.onNodeWithText(" DA en cours", substring = true).assertExists()
        composeTestRule.onAllNodesWithText(" DA restant", substring = true).assertCountEquals(3)

        // The pooled meter's title/subtitle/status badge.
        composeTestRule.onNodeWithText("Tranche 1 (T1)").assertExists()
        composeTestRule.onNodeWithText("Toutes catégories — 1er versement (Sept)").assertExists()
        composeTestRule.onNodeWithText("En retard").assertExists()

        // The big rate + the dossiers count (the T-462 display-grade rate).
        // The global badges row also renders "77% collecté global", so the
        // bare rate matches exactly two nodes (the badge + the meter's rate).
        composeTestRule.onAllNodesWithText("77%").assertCountEquals(2)
        composeTestRule.onNodeWithText("265/683 dossiers").assertExists()

        // The 2×3 metric grid labels — the reconciliation identity's cells.
        composeTestRule.onNodeWithText("FACTURÉ").assertExists()
        composeTestRule.onNodeWithText("ENCAISSÉ").assertExists()
        composeTestRule.onNodeWithText("EN COURS").assertExists()
        composeTestRule.onNodeWithText("RESTE DÛ").assertExists()
        composeTestRule.onNodeWithText("FAMILLES EN RETARD").assertExists()
        composeTestRule.onNodeWithText("CATÉGORIES").assertExists()
        composeTestRule.onNodeWithText("120 / 400").assertExists()

        // The reconciliation identity line (the full-variant's only
        // "Total dû" carrier) + the per-category chips (the Scolarité /
        // Transport breakdown): "Scolarité" appears in the subtitle, the
        // per-category chip and the detail grid's "Scolarité · T1" row
        // (three); "Transport" in the subtitle and the chip (two — the
        // test's wave list carries no transport detail row).
        composeTestRule.onAllNodesWithText("Total dû ", substring = true).assertCountEquals(1)
        composeTestRule.onAllNodesWithText("Scolarité", substring = true).assertCountEquals(3)
        composeTestRule.onAllNodesWithText("Transport", substring = true).assertCountEquals(2)

        // The « Hors Tranches » section (the FI disclosure).
        composeTestRule.onNodeWithText("Frais d'inscription (FI)", substring = true).assertExists()

        // The per-category detail grid header.
        composeTestRule.onNodeWithText("DÉTAIL PAR CATÉGORIE DE FACTURATION").assertExists()
    }

    // ── 3. Source-pattern pins — the presentation invariants ─────────────

    /** The app module dir — Robolectric's cwd (the SpacingTokenGate pattern). */
    private fun moduleDir(): File {
        val candidates = listOf(
            File("."),
            File("app"),
            File(".."),
            File("../app"),
        )
        return candidates.firstOrNull { File(it, "src/main/java/com/example/ui/features/financials/TrancheWaveCard.kt").isFile }
            ?: error("Cannot locate the app module root (cwd=${File(".").absolutePath})")
    }

    private fun source(relPath: String): String {
        val f = File(moduleDir(), relPath)
        assertTrue("missing source file: $relPath", f.isFile)
        return f.readText()
    }

    @Test
    fun `pooled meter consumes the DS ElLinearProgress not a hand-rolled bar`() {
        val src = source("src/main/java/com/example/ui/features/dashboard/analytics/ExecutiveCards.kt")
        assertTrue(
            "PooledWaveMeter must route its meter through ElLinearProgress (the DS reuse rule)",
            Regex("ElLinearProgress\\(").containsMatchIn(src),
        )
        // The hand-rolled meter's signature (a bare background(meterColor)
        // Box) is gone — the pre-T-462 construction.
        assertFalse(
            "the hand-rolled meter Box (background(meterColor)) must not come back",
            Regex("background\\(meterColor\\)").containsMatchIn(src),
        )
    }

    @Test
    fun `wave surfaces never use chart-palette status hues as bare text colors`() {
        val files = listOf(
            "src/main/java/com/example/ui/features/dashboard/analytics/ExecutiveCards.kt",
            "src/main/java/com/example/ui/features/financials/TrancheWaveCard.kt",
        )
        val pattern = Regex("color\\s*=\\s*ElChartPalette\\.(danger|success|info|warning)")
        files.forEach { rel ->
            val src = source(rel)
            assertEquals(
                "status TEXT must resolve through ElTheme.colors semantics, not the chart palette ($rel)",
                0,
                pattern.findAll(src).count(),
            )
        }
    }

    @Test
    fun `metric tiles carry the soft container treatment`() {
        val exec = source("src/main/java/com/example/ui/features/dashboard/analytics/ExecutiveCards.kt")
        assertTrue(
            "PooledMetric must render the tile (tinted background + hairline border)",
            Regex("private fun PooledMetric[\\s\\S]*?background\\(c\\.surfaceVariant\\.copy\\(alpha = 0\\.4f\\)\\)[\\s\\S]*?border\\(")
                .containsMatchIn(exec),
        )
        val strip = source("src/main/java/com/example/ui/features/financials/TrancheWaveCard.kt")
        assertTrue(
            "TrancheTotal must render the tile (tinted background + hairline border)",
            Regex("private fun TrancheTotal[\\s\\S]*?background\\(c\\.surfaceVariant\\.copy\\(alpha = 0\\.4f\\)\\)[\\s\\S]*?border\\(")
                .containsMatchIn(strip),
        )
    }

    @Test
    fun `the app typeface is Inter across the typography scale`() {
        val typography = source("src/main/java/com/example/ui/designsystem/theme/Typography.kt")
        val styles = source("src/main/java/com/example/ui/designsystem/theme/ElTextStyles.kt")
        // Zero remaining Default-family references in either scale…
        assertEquals(
            "Typography.kt must not reference FontFamily.Default (the Inter mandate)",
            0,
            Regex("FontFamily\\.Default").findAll(typography).count(),
        )
        assertEquals(
            "ElTextStyles.kt must not reference FontFamily.Default (the Inter mandate)",
            0,
            Regex("FontFamily\\.Default").findAll(styles).count(),
        )
        // …and the Inter family resolves over the six bundled cuts.
        assertTrue("Typography.kt must set ElInter", typography.contains("fontFamily = ElInter"))
        assertTrue("ElTextStyles.kt must set ElInter", styles.contains("ElInter"))
        val fonts = source("src/main/java/com/example/ui/designsystem/theme/ElFonts.kt")
        listOf(
            "R.font.inter_regular", "R.font.inter_medium", "R.font.inter_semibold",
            "R.font.inter_bold", "R.font.inter_extrabold", "R.font.inter_black",
        ).forEach { ref ->
            assertTrue("ElFonts.kt must bundle $ref", fonts.contains(ref))
            val ttf = File(moduleDir(), "src/main/res/font/${ref.removePrefix("R.font.")}.ttf")
            assertTrue("the bundled cut must exist on disk: ${ttf.path}", ttf.isFile)
        }
    }
}
