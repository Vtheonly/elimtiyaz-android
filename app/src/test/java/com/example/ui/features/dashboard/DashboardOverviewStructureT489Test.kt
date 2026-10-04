package com.example.ui.features.dashboard

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.domain.model.AppNotification
import com.example.domain.model.ExecPooledWaveItem
import com.example.domain.model.ExecutiveStatsSnapshot
import com.example.domain.model.Payment
import com.example.ui.designsystem.theme.ElImtiyazTheme
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
 * T-489 (UI-330) — the first-page decluttering contract.
 *
 * TWO verification layers (the TrancheWaveCardsT462Test discipline):
 *
 * 1. RENDERING pins (createComposeRule): the compact
 *    DashboardCollectionSummaryCard renders the at-a-glance layer — the
 *    global %, the ENCAISSÉ/EN COURS/RESTE DÛ trio, ONE line per wave with
 *    its status tag + days-late, and the "Analyse détaillée" quick access
 *    whose click OPENS the Analytique tab; the compact activity feed
 *    renders at most 2 single-line payment rows + the unread summary row
 *    with its "Tout voir" inbox path.
 *
 * 2. SOURCE-pattern pins (the ChatWiringScanTest discipline): the heavy
 *    analytics block is GONE from the Overview tab (no
 *    DashboardRevenueChart reference, the file deleted) but EVERY
 *    relocated surface stays reachable — the Analytique tab still renders
 *    ExecutiveDashboard (the full WaveVelocityCard hero + the stat strip),
 *    the feed compacts to take(2), the alerts to take(2) + "Voir tout",
 *    and the quick actions + KPI row survive untouched.
 *
 * ARCH-012: the release exclusion list carries this class (createComposeRule
 * under the suffixed applicationId — the §15.86/T-460 same-commit rule).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class DashboardOverviewStructureT489Test {

    @get:Rule val composeTestRule = createComposeRule()

    // ── Test data (centimes; display-only fixtures, the T-462 convention) ──

    private fun pooledWave(
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

    private val snapshot = ExecutiveStatsSnapshot(
        pooledWaves = listOf(
            pooledWave(1, 77, 60_900_000_00L, 46_900_000_00L, 0L, 14_000_000_00L, overdue = true, settled = 265, count = 683),
            pooledWave(2, 37, 47_770_000_00L, 17_905_000_00L, 1_000_000_00L, 28_865_000_00L, overdue = true, settled = 265, count = 683),
            // T-427 (DATA-048): remainingTotal == 0 + rows > 0 → "Clôturée".
            pooledWave(3, 100, 29_059_000_00L, 29_059_000_00L, 0L, 0L, overdue = false, settled = 413, count = 413),
        ),
    )

    private fun renderCard(exec: ExecutiveStatsSnapshot = snapshot, onOpenAnalytics: () -> Unit = {}) {
        composeTestRule.setContent {
            ElImtiyazTheme {
                DashboardCollectionSummaryCard(executive = exec, onOpenAnalytics = onOpenAnalytics)
            }
        }
        composeTestRule.waitForIdle()
    }

    // ── 1. RENDERING pins — the compact collection summary ────────────────

    @Test
    fun `the compact card renders the global verdict, the trio and one line per wave`() {
        renderCard()

        // The global verdict — derived through the SAME canonical
        // execSharePct the renderer and the desktop hero use (PARITY-002:
        // the fixtures are independent; the derivation is the pinned
        // corpus-proven mirror — 68% on these fixtures).
        val expectedPct = com.example.core.execSharePct(
            snapshot.pooledWaves.sumOf { it.paidTotal },
            snapshot.pooledWaves.sumOf { it.dueTotal },
        )
        composeTestRule.onNodeWithText("$expectedPct%").assertExists()
        composeTestRule.onNodeWithText("collecté global").assertExists()

        // The trio in the shared PooledMetric tile language.
        composeTestRule.onNodeWithText("ENCAISSÉ").assertExists()
        composeTestRule.onNodeWithText("EN COURS").assertExists()
        composeTestRule.onNodeWithText("RESTE DÛ").assertExists()

        // ONE line per wave — title, settled/dossiers, %, status tag.
        composeTestRule.onNodeWithText("Tranche 1 (T1)").assertExists()
        composeTestRule.onNodeWithText("77%").assertExists()
        composeTestRule.onNodeWithText("Tranche 2 (T2)").assertExists()
        composeTestRule.onNodeWithText("37%").assertExists()
        composeTestRule.onNodeWithText("Tranche 3 (T3)").assertExists()
        composeTestRule.onNodeWithText("100%").assertExists()

        // The status tags — the SAME T-427 derivation as the full meter.
        composeTestRule.onAllNodesWithText("En retard").assertCountEquals(2)
        composeTestRule.onNodeWithText("Clôturée").assertExists()

        // The days-late chip derives through the SAME waveDaysLate the full
        // meter uses (live clock — expected computed, never a literal).
        val expectedDaysLate = com.example.ui.features.dashboard.analytics.waveDaysLate(
            "2025-09-15T00:00:00Z", System.currentTimeMillis(),
        )
        if (expectedDaysLate > 0) {
            composeTestRule.onAllNodesWithText("$expectedDaysLate j").assertCountEquals(2)
        }
    }

    @Test
    fun `the compact card renders the honest empty state when no tranche is billed`() {
        renderCard(exec = ExecutiveStatsSnapshot())

        composeTestRule
            .onNodeWithText("Aucune tranche facturée sur la période sélectionnée.")
            .assertExists()
        // No fabricated verdict — the global % node is absent.
        composeTestRule.onAllNodesWithText("collecté global").assertCountEquals(0)
    }

    @Test
    fun `the analysis quick access switches to the Analytique tab`() {
        var opened = 0
        renderCard(onOpenAnalytics = { opened++ })

        composeTestRule
            .onNodeWithText("Analyse détaillée (T1 / T2 / T3, catégories, funnel)")
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals("the Analyse détaillée action opens the Analytique tab", 1, opened)
    }

    // ── 2. RENDERING pins — the compact activity feed ─────────────────────

    private fun payment(receipt: String, amount: Long) = Payment(
        id = "p-$receipt",
        tenantId = "t1",
        receiptNumber = receipt,
        parentId = "par-1",
        amount = amount,
        method = PaymentMethod.CASH,
        status = PaymentStatus.PAID,
        category = PaymentCategory.TUITION,
        collectedBy = "agent",
        collectedAt = "2026-10-04T10:00:00Z",
        createdAt = "2026-10-04T10:00:00Z",
        updatedAt = "2026-10-04T10:00:00Z",
    )

    private fun unread(title: String) = AppNotification(
        id = "n-$title",
        tenantId = "t1",
        title = title,
        body = "Corps de la notification",
        type = "payment_overdue",
        priority = "high",
        source = "system",
        sourceLabel = "Système",
        readAt = null,
        createdAt = "2026-10-04T10:00:00Z",
        createdBy = "system",
    )

    @Test
    fun `the compact feed renders 2 payment rows and the unread summary row`() {
        var alertsOpened = 0
        composeTestRule.setContent {
            ElImtiyazTheme {
                DashboardNotificationsSection(
                    notifications = listOf(unread("Relance famille ZIANI"), unread("Chèque à déposer")),
                    recentPayments = listOf(
                        payment("REC-2026-001", 25_000_000L),
                        payment("REC-2026-002", 18_500_000L),
                        payment("REC-2026-003", 7_000_000L),
                    ),
                    onNavigateToFinancials = {},
                    onNavigateToAlerts = { alertsOpened++ },
                )
            }
        }
        composeTestRule.waitForIdle()

        // The two compact single-line rows.
        composeTestRule.onNodeWithText("REC-2026-001").assertExists()
        composeTestRule.onNodeWithText("REC-2026-002").assertExists()
        // take(2) — the third payment is NOT on the first page.
        composeTestRule.onNodeWithText("REC-2026-003").assertDoesNotExist()

        // The unread summary row: the most recent title + the count tag +
        // the "Tout voir" inbox path.
        composeTestRule.onNodeWithText("Relance famille ZIANI").assertExists()
        composeTestRule.onNodeWithText("Tout voir").performClick()
        composeTestRule.waitForIdle()
        assertEquals("Tout voir opens the Alerts inbox", 1, alertsOpened)
    }

    // ── 3. SOURCE-pattern pins — the decluttered Overview, the intact Analytique ──

    private fun read(relative: String): String {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(cwd, "src/main/java/com/example/$relative"),
            File(cwd.parentFile ?: cwd, "src/main/java/com/example/$relative"),
        )
        return candidates.firstOrNull { it.exists() }?.readText()
            ?: error("source file not found: $relative (cwd=${cwd.absolutePath})")
    }

    @Test
    fun `the Overview tab no longer stacks the heavy analytics block`() {
        val hub = read("ui/features/dashboard/DashboardHubScreen.kt")
        assertFalse(
            "the heavy analytics block (DashboardRevenueChart) is GONE from the hub",
            hub.contains("DashboardRevenueChart"),
        )
        assertTrue(
            "the compact collection summary is the Overview's financial block",
            hub.contains("DashboardCollectionSummaryCard"),
        )
        assertTrue(
            "the compact card opens the Analytique tab (every relocated surface reachable)",
            hub.contains("onOpenAnalytics = { selectedViewTab = 1 }"),
        )
        // The dead file is gone (the T-062 dead-code discipline).
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val gone = listOf(cwd, cwd.parentFile ?: cwd)
            .map { File(it, "src/main/java/com/example/ui/features/dashboard/DashboardRevenueChart.kt") }
            .any { it.exists() }
        assertFalse("DashboardRevenueChart.kt is deleted (unreachable after the rewiring)", gone)
    }

    @Test
    fun `the Analytique tab still renders the FULL executive analysis`() {
        val hub = read("ui/features/dashboard/DashboardHubScreen.kt")
        assertTrue(
            "the Executive Command Center (full WaveVelocityCard hero) stays on Analytique",
            hub.contains("ExecutiveDashboard"),
        )
        assertTrue(
            "the cross-filtering slicers stay on Analytique",
            hub.contains("AnalyticsSlicersBar"),
        )
        assertTrue(
            "the 6-card statistics strip stays on Analytique",
            hub.contains("AnalyticsStatStrip"),
        )
        assertTrue(
            "the method + category mixes stay on Analytique",
            hub.contains("MethodMixCard") && hub.contains("CategoryMixCard"),
        )
        assertTrue(
            "the YoY + aging composition stay on Analytique",
            hub.contains("YoYComparisonCard") && hub.contains("AgingCompositionCard"),
        )
        assertTrue(
            "the funnel + debt donut + Pareto stay on Analytique",
            hub.contains("DashboardCollectionAndDebtRow"),
        )
        assertTrue(
            "the demographics stay on Analytique",
            hub.contains("DemographicsCard"),
        )
    }

    @Test
    fun `the at-a-glance layer survives untouched`() {
        val hub = read("ui/features/dashboard/DashboardHubScreen.kt")
        assertTrue("the KPI cards row stays", hub.contains("DashboardKpiCardsRow"))
        assertTrue("the quick actions row stays", hub.contains("DashboardQuickActionsRow"))
        assertTrue("the weekly rhythm stays", hub.contains("WeeklyOperatingRhythmCard"))
        assertTrue("the attendance block stays", hub.contains("DashboardAttendanceChart"))
        assertTrue("the approvals pair stays", hub.contains("DashboardApprovalsRow"))
        // The ChatWiringScanTest contract (Messagerie badge) must survive.
        assertTrue(hub.contains("onNavigateToChat"))
        assertTrue(hub.contains("unreadMessages = unreadMessages"))
    }

    @Test
    fun `the feed and the alerts compact to 2 rows with a full-list path`() {
        val feed = read("ui/features/dashboard/DashboardNotificationsSection.kt")
        assertTrue("the feed's quick access to the inbox", feed.contains("onNavigateToAlerts"))
        assertTrue("the feed renders at most 2 payments", feed.contains("take(2)"))
        assertFalse("the feed no longer renders 3 payments", feed.contains("take(3)"))

        val alerts = read("ui/features/dashboard/DashboardAlertsSection.kt")
        assertTrue("the alerts render at most 2 rows", alerts.contains("take(2)"))
        assertFalse("the alerts no longer render 3 rows", alerts.contains("take(3)"))
        assertTrue("the alerts' full list path", alerts.contains("Voir tout"))
    }
}
