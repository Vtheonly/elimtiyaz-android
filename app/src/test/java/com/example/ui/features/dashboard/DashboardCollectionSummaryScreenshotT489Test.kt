package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.domain.model.AppNotification
import com.example.domain.model.ExecPooledWaveItem
import com.example.domain.model.ExecutiveStatsSnapshot
import com.example.domain.model.Payment
import com.example.ui.designsystem.theme.ElImtiyazTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T-489 (UI-330) — the decluttered first page's VISUAL record (the
 * TrancheWaveCardsScreenshotT462Test pattern: a committed render of the
 * production dark theme + the committed PNG it produces). Regenerate with:
 *   ./gradlew recordRoborazziDebug --tests "...DashboardCollectionSummaryScreenshotT489Test"
 *
 * The render carries the Overview tab's NEW financial block (the compact
 * DashboardCollectionSummaryCard) with the owner-screenshot-era figures
 * (T1 77% / T2 37% / T3 63% overdue-wave shapes) and the compact activity
 * feed — the before/after visual evidence for the T-489 closeout (the
 * "before" is the owner's eight `Screenshot From App/` committed renders).
 *
 * ARCH-012: carried on the release exclusion list (createComposeRule under
 * the suffixed applicationId — same commit as this test, per the rule).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class DashboardCollectionSummaryScreenshotT489Test {

    @get:Rule val composeTestRule = createComposeRule()

    private fun pooled(
        wave: Int,
        pct: Int,
        due: Long,
        paid: Long,
        pending: Long,
        remaining: Long,
        overdue: Boolean,
        settled: Int,
        count: Int,
    ) = ExecPooledWaveItem(
        wave = wave,
        installmentCount = count,
        settledCount = settled,
        familyCount = 400,
        debtorFamilyCount = 135,
        overdueDebtorFamilyCount = 120,
        dueTotal = due,
        paidTotal = paid,
        pendingTotal = pending,
        remainingTotal = remaining,
        overCoverageTotal = 0L,
        collectedPct = pct,
        dueDateMin = "2025-09-15T00:00:00Z",
        dueDateMax = "2026-09-15T00:00:00Z",
        anyUnsettledOverdue = overdue,
        anyUnsettledFuture = false,
    )

    private fun payment(receipt: String, amount: Long) = Payment(
        id = "p-$receipt", tenantId = "t1", receiptNumber = receipt, parentId = "par-1",
        amount = amount, method = PaymentMethod.CASH, status = PaymentStatus.PAID,
        category = PaymentCategory.TUITION, collectedBy = "agent",
        collectedAt = "2026-10-04T10:00:00Z", createdAt = "2026-10-04T10:00:00Z",
        updatedAt = "2026-10-04T10:00:00Z",
    )

    private fun unread(title: String) = AppNotification(
        id = "n-$title", tenantId = "t1", title = title, body = "Corps de la notification",
        type = "payment_overdue", priority = "high", source = "system", sourceLabel = "Système",
        readAt = null, createdAt = "2026-10-04T10:00:00Z", createdBy = "system",
    )

    @Test
    fun capture_the_compact_first_page_blocks() {
        composeTestRule.setContent {
            ElImtiyazTheme(darkTheme = true) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                ) {
                    DashboardCollectionSummaryCard(
                        executive = ExecutiveStatsSnapshot(
                            pooledWaves = listOf(
                                pooled(1, 77, 60_900_000_00L, 46_900_000_00L, 0L, 14_000_000_00L, overdue = true, settled = 265, count = 683),
                                pooled(2, 37, 47_770_000_00L, 17_905_000_00L, 1_000_000_00L, 28_865_000_00L, overdue = true, settled = 265, count = 683),
                                pooled(3, 63, 29_059_000_00L, 18_374_000_00L, 0L, 10_685_000_00L, overdue = true, settled = 223, count = 413),
                            ),
                        ),
                        onOpenAnalytics = {},
                    )
                    DashboardNotificationsSection(
                        notifications = listOf(
                            unread("Relance créances — 13 familles en retard"),
                            unread("Chèques à déposer"),
                        ),
                        recentPayments = listOf(
                            payment("REC-2026-0001", 25_000_000L),
                            payment("REC-2026-0002", 18_500_000L),
                        ),
                        onNavigateToFinancials = {},
                        onNavigateToAlerts = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage("t489-compact-first-page.png")
    }
}
