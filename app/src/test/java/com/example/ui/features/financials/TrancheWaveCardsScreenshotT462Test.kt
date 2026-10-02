package com.example.ui.features.financials

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.domain.model.ExecNonWaveItem
import com.example.domain.model.ExecNonWaveKindItem
import com.example.domain.model.ExecPooledWaveItem
import com.example.domain.model.ExecWaveItem
import com.example.domain.model.ExecWaveStatsItem
import com.example.domain.model.TrancheStripTotalsItem
import com.example.domain.model.TrancheWaveItem
import com.example.ui.designsystem.theme.ElImtiyazTheme
import com.example.ui.features.dashboard.analytics.WaveVelocityCard
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T-462 (UI-327) — the tranche cards' VISUAL record (the GreetingScreenshotTest
 * pattern: a committed render of the production dark theme + the committed PNG
 * it produces). Regenerate with:
 *   ./gradlew recordRoborazziDebug --tests "...TrancheWaveCardsScreenshotT462Test"
 *
 * The render carries both tranche surfaces with realistic fixture data: the
 * pooled wave meter (the dashboard's "Tranche" card — the 2×3 metric tiles,
 * the display-grade rate, the DS meter, the per-category chips) and the
 * Finance strip (the wave meters + the 4-cell totals tiles). The captured
 * PNG is the before/after visual evidence for the T-462 verification doc.
 *
 * ARCH-012: carried on the release exclusion list (createComposeRule under
 * the suffixed applicationId — same commit as this test, per the rule).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class TrancheWaveCardsScreenshotT462Test {

    @get:Rule val composeTestRule = createComposeRule()

    private fun stats(category: String, label: String, remaining: Long) = ExecWaveStatsItem(
        category = category, categoryLabel = label, wave = 1,
        installmentCount = 683, settledCount = 265, familyCount = 400,
        debtorFamilyCount = 135, overdueDebtorFamilyCount = 120,
        dueTotal = 30_000_000_00L, paidTotal = 23_000_000_00L,
        pendingTotal = 1_000_000_00L, remainingTotal = remaining,
        dueDateMin = "2025-09-15T00:00:00Z", dueDateMax = "2026-09-15T00:00:00Z",
        anyUnsettledOverdue = true,
    )

    @Test
    fun capture_both_cards() {
        composeTestRule.setContent {
            ElImtiyazTheme(darkTheme = true) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    WaveVelocityCard(
                        waves = listOf(
                            ExecWaveItem(
                                key = "tuition#1", category = "tuition", categoryLabel = "Scolarité",
                                wave = 1, installmentCount = 640, paidCount = 260,
                                familyCount = 380, debtorFamilyCount = 120, overdueDebtorFamilyCount = 118,
                                dueTotal = 29_000_000_00L, paidTotal = 22_400_000_00L,
                                remainingTotal = 22_400_000_00L, collectedPct = 77, clearedPct = 77,
                                dueDate = "2025-09-15T00:00:00Z", dueDateMax = "2026-09-15T00:00:00Z",
                            ),
                        ),
                        pooledWaves = listOf(
                            ExecPooledWaveItem(
                                wave = 1, installmentCount = 683, settledCount = 265,
                                familyCount = 400, debtorFamilyCount = 135, overdueDebtorFamilyCount = 120,
                                dueTotal = 30_000_000_00L, paidTotal = 23_000_000_00L,
                                pendingTotal = 1_000_000_00L, remainingTotal = 23_000_000_00L,
                                overCoverageTotal = 0L, collectedPct = 77,
                                dueDateMin = "2025-09-15T00:00:00Z", dueDateMax = "2026-09-15T00:00:00Z",
                                anyUnsettledOverdue = true,
                                perCategory = listOf(
                                    stats("tuition", "Scolarité", 23_000_000_00L),
                                    stats("transport", "Transport", 616_000_00L),
                                ),
                            ),
                        ),
                        nonWave = listOf(
                            ExecNonWaveItem(
                                kind = ExecNonWaveKindItem.FI, kindLabel = "fi", category = "fi",
                                categoryLabel = "Frais d'inscription", installmentCount = 191,
                                settledCount = 185, familyCount = 191, debtorFamilyCount = 6,
                                overdueDebtorFamilyCount = 6, dueTotal = 4_000_000_00L,
                                paidTotal = 3_876_000_00L, pendingTotal = 0L,
                                remainingTotal = 124_000_00L,
                                dueDateMin = "2026-09-15T00:00:00Z", dueDateMax = "2026-09-15T00:00:00Z",
                            ),
                        ),
                        variant = "full",
                    )
                    TrancheWaveCard(
                        waves = listOf(
                            TrancheWaveItem(
                                index = 1, label = "Tranche 1 (Septembre)",
                                hint = "Échéance officielle : 15 sept.",
                                due = 10_000_000_00L, paid = 7_700_000_00L, pending = 50_000_00L,
                                remaining = 1_800_000_00L, pct = 77, tuitionPct = 64,
                                isNextTarget = true, isOverdue = true,
                                dueDate = "2025-09-15T00:00:00Z", dueDateMax = "2026-09-15T00:00:00Z",
                            ),
                            TrancheWaveItem(
                                index = 2, label = "Tranche 2 (Décembre)",
                                hint = "Échéance officielle : 15 déc.",
                                due = 10_000_000_00L, paid = 3_700_000_00L, pending = 0L,
                                remaining = 6_300_000_00L, pct = 37, tuitionPct = 32,
                                isOverdue = true,
                                dueDate = "2025-12-15T00:00:00Z", dueDateMax = "2026-12-15T00:00:00Z",
                            ),
                        ),
                        totals = TrancheStripTotalsItem(
                            totalDue = 30_000_000_00L, totalPaid = 23_100_000_00L,
                            totalRemaining = 5_400_000_00L, overdueCount = 507,
                        ),
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/t462-tranche-cards.png")
    }
}
