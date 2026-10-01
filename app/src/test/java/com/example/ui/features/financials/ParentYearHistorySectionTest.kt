package com.example.ui.features.financials

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.core.LedgerEntryType
import com.example.core.LedgerSourceType
import com.example.core.PaymentCategory
import com.example.core.PaymentMethod
import com.example.core.PaymentStatus
import com.example.core.LedgerEntry
import com.example.core.formatDzd
import com.example.domain.model.Installment
import com.example.domain.model.Payment
import com.example.ui.features.crm.ParentYearHistorySection
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T-456 (128th session) — the rendering-state test for the « Historique
 * par Année Scolaire » section (the INV-20e surface — the Android mirror
 * of the desktop's parent-year-history-section.tsx suite).
 *
 * SEMANTIC assertions, not screenshots (ARCH-012 — the documented
 * discipline): the honest empty state, the year cards + flags, the
 * prior-years banner with its per-year chips, and the expanded « Services
 * de l'année » block. The fixtures are the desktop's own owner scenario
 * (100k/80k/20k across two years) and the T-442 three-year breakdown
 * shape, reduced to the rendering-relevant rows.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ParentYearHistorySectionTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun ins(
        id: String,
        label: String,
        trancheNumber: Int,
        amountDue: Long,
        amountPaid: Long,
        dueDate: String,
        paidDate: String? = null,
        status: PaymentStatus = PaymentStatus.UNPAID,
        academicCycle: String,
        category: PaymentCategory = PaymentCategory.TUITION,
    ) = Installment(
        id = id, tenantId = "tenant-1", parentId = "p-1", studentId = "s-1",
        category = category, label = label, trancheNumber = trancheNumber,
        amountDue = amountDue, amountPaid = amountPaid, amountPending = 0,
        dueDate = dueDate, paidDate = paidDate, status = status,
        academicCycle = academicCycle,
    )

    private fun pay(id: String, amount: Long, collectedAt: String): Payment = Payment(
        id = id, tenantId = "tenant-1", receiptNumber = "REC-$id", parentId = "p-1",
        studentId = null, amount = amount, method = PaymentMethod.CASH, status = PaymentStatus.PAID,
        category = PaymentCategory.TUITION, installmentId = null, proofUrl = null, notes = null,
        collectedBy = "usr-1", collectedAt = collectedAt, createdAt = collectedAt, updatedAt = collectedAt,
    )

    private fun led(id: String, sourceId: String, amount: Long, at: String): LedgerEntry = LedgerEntry(
        id = id, tenantId = "tenant-1", accountId = "acct", parentId = "p-1", studentId = null,
        category = PaymentCategory.TUITION, amount = amount, type = LedgerEntryType.PAYMENT,
        sourceType = LedgerSourceType.PAYMENT, sourceId = sourceId, method = PaymentMethod.CASH,
        receiptNumber = "REC-$sourceId", paymentStatus = PaymentStatus.PAID, reversesId = null,
        description = "Encaissement", actorId = "usr-1", actorName = "Staff", at = at, metadata = emptyMap(),
    )

    /** The desktop owner scenario (100k/80k/20k), centimes. */
    private fun ownerFixtures(): Triple<List<Installment>, List<Payment>, List<LedgerEntry>> {
        val d = { v: Long -> v * 100 }
        return Triple(
            listOf(
                ins("ins-T1-25", "Tranche 1", 1, d(40_000), d(40_000), "2025-09-15", "2025-11-01", PaymentStatus.PAID, "2025-2026"),
                ins("ins-T2-25", "Tranche 2", 2, d(30_000), d(30_000), "2025-12-15", "2026-02-01", PaymentStatus.PAID, "2025-2026"),
                ins("ins-T3-25", "Tranche 3", 3, d(30_000), d(30_000), "2026-03-15", "2026-10-20", PaymentStatus.PAID, "2025-2026"),
                ins("ins-T1-26", "Tranche 1", 1, d(40_000), 0, "2026-09-15", academicCycle = "2026-2027"),
                ins("ins-T2-26", "Tranche 2", 2, d(40_000), 0, "2026-12-15", academicCycle = "2026-2027"),
                ins("ins-T3-26", "Tranche 3", 3, d(40_000), 0, "2027-03-15", academicCycle = "2026-2027"),
            ),
            listOf(
                pay("pay-1", d(40_000), "2025-11-01T10:00:00Z"),
                pay("pay-2", d(40_000), "2026-02-01T10:00:00Z"),
                pay("pay-3", d(20_000), "2026-10-20T10:00:00Z"),
            ),
            listOf(
                led("led-1", "pay-1", -d(40_000), "2025-11-01T10:00:00Z"),
                led("led-2", "pay-2", -d(40_000), "2026-02-01T10:00:00Z"),
                led("led-3", "pay-3", -d(20_000), "2026-10-20T10:00:00Z"),
            ),
        )
    }

    @Test
    fun `empty inputs - the honest empty state renders NOTHING`() {
        composeTestRule.setContent {
            ParentYearHistorySection(
                parentId = "p-1",
                installments = emptyList(),
                payments = emptyList(),
                ledgerEntries = emptyList(),
            )
        }
        composeTestRule.onAllNodesWithText("Historique par Année Scolaire").apply {
            assertEquals(0, fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `the owner scenario renders both year cards with the re-enrollment flag and NO prior-years banner`() {
        val (installments, payments, ledger) = ownerFixtures()
        composeTestRule.setContent {
            ParentYearHistorySection(
                parentId = "p-1",
                installments = installments,
                payments = payments,
                ledgerEntries = ledger,
            )
        }
        composeTestRule.onNodeWithText("Historique par Année Scolaire").assertIsDisplayed()
        composeTestRule.onNodeWithText("2025-2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("2026-2027").assertIsDisplayed()
        // The re-enrollment flag lives on the debt year (2025-2026).
        composeTestRule.onNodeWithText("Réinscrit avec dette").assertIsDisplayed()
        // The old debt is NOT still owed → NO prior-years banner.
        composeTestRule.onAllNodesWithText("Dettes des années précédentes").apply {
            assertEquals(0, fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `the expanded year card shows the Services de l'année block with the tranche facts`() {
        val (installments, payments, ledger) = ownerFixtures()
        composeTestRule.setContent {
            ParentYearHistorySection(
                parentId = "p-1",
                installments = installments,
                payments = payments,
                ledgerEntries = ledger,
            )
        }
        // Expand the 2025-2026 card.
        composeTestRule.onNodeWithText("2025-2026").performClick()
        composeTestRule.onNodeWithText("Services de l'année (1)").assertIsDisplayed()
        composeTestRule.onNodeWithText("Scolarité").assertIsDisplayed()
        // The tuition group: 3 charges · Dû 100 000 · Payé 100 000 (T1+T2
        // in-year, T3 via the cross-year settlement → remaining 0).
        composeTestRule.onNodeWithText("3 charge(s) · Dû ${100_000L.formatDzd()} · Payé ${100_000L.formatDzd()}").assertIsDisplayed()
        composeTestRule.onNodeWithText("Charges de l'année (3)").assertIsDisplayed()
    }

    @Test
    fun `a family with prior-years debt renders the banner with the per-year chips`() {
        val d = { v: Long -> v * 100 }
        val installments = listOf(
            ins("t1-24", "Tranche 1 — Scolarité (V1)", 1, d(60_000), d(40_000), "2024-09-15", academicCycle = "2024-2025"),
            ins("t1-25", "Tranche 1 — Scolarité (V1)", 1, d(80_000), 0, "2025-09-15", academicCycle = "2025-2026"),
            ins("t1-26", "Tranche 1 — Scolarité (V1)", 1, d(90_000), 0, "2026-09-15", academicCycle = "2026-2027"),
        )
        composeTestRule.setContent {
            ParentYearHistorySection(
                parentId = "p-1",
                installments = installments,
                payments = emptyList(),
                ledgerEntries = emptyList(),
            )
        }
        composeTestRule.onNodeWithText("Dettes des années précédentes").assertIsDisplayed()
        // formatDzd uses Locale.FRANCE grouping (a narrow no-break space,
        // U+202F), so the expected strings are BUILT with the same formatter.
        composeTestRule.onNodeWithText("${100_000L.formatDzd()} DZD").assertIsDisplayed()
        composeTestRule.onNodeWithText("2024-2025 : ${20_000L.formatDzd()}").assertIsDisplayed()
        composeTestRule.onNodeWithText("2025-2026 : ${80_000L.formatDzd()}").assertIsDisplayed()
    }
}
