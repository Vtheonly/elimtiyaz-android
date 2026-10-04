package com.example.infrastructure.supabase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T-492 (SYNC-302) — the expense_tickets translation corpus: the Android
 * port of the desktop SupabaseExpenseRepository's mapping layer (T-093 /
 * DRIFT-013), pinned so both clients agree on the same rows.
 *
 * The desktop's documented divergences under test:
 *  1. STATUS — the DB vocabulary (pending_approval / approved_funds_released
 *     / settled_and_closed) vs the domain's (submitted / approved / settled);
 *  2. CATEGORY — the DB's expense_categories codes vs the domain's codes,
 *     with the documented lossy buckets (educational_material → supplies,
 *     it/medical → other, facilities → rent);
 *  3. MONEY — the server's NUMERIC DZD vs the entity's Long centimes (×100);
 *  4. URGENCY — the DB's `critical` reads as the domain's `high`;
 *  5. JUSTIFICATION/NOTES — approval_note ?? rejected_reason → notes;
 *  6. THE PROOF ATTRIBUTION — receipt_uploaded_by/at → the entity's new
 *     v20 columns (the 4-stage timeline finally renders for pulled rows).
 */
class ExpenseTicketTranslationT492Test {

    private fun dto(
        id: String = "srv-1",
        ticketNumber: String = "EXP-2026-A1B2C3",
        status: String = "pending_approval",
        categoryCode: String? = "office_supplies",
        requestedAmount: Double = 12500.50,
        finalSpentAmount: Double? = null,
        urgency: String = "medium",
        payee: String? = "Librairie En-Nour",
        approvedBy: String? = null,
        approvedAt: String? = null,
        approvalNote: String? = null,
        rejectedReason: String? = null,
        disbursedAt: String? = null,
        settledAt: String? = null,
        receiptPath: String? = null,
        receiptUploadedBy: String? = null,
        receiptUploadedAt: String? = null,
        anomalyScore: Double? = null,
        anomalyFlagsJson: kotlinx.serialization.json.JsonElement? = null,
        submittedAt: String = "2026-10-01T10:00:00Z",
    ) = ExpenseTicketDto(
        id = id, tenantId = "tenant-1", ticketNumber = ticketNumber, title = "Fournitures",
        description = "Classe 4AP", categoryId = "cat-uuid", requestedAmount = requestedAmount,
        finalSpentAmount = finalSpentAmount, justification = "Classe 4AP", urgency = urgency,
        status = status, submittedBy = "usr-2", submittedAt = submittedAt,
        approvedBy = approvedBy, receiptPath = receiptPath, createdAt = submittedAt,
        updatedAt = submittedAt, payee = payee, approvedAt = approvedAt,
        approvalNote = approvalNote, rejectedReason = rejectedReason, disbursedAt = disbursedAt,
        settledAt = settledAt, receiptUploadedBy = receiptUploadedBy,
        receiptUploadedAt = receiptUploadedAt, anomalyScore = anomalyScore,
        anomalyFlagsJson = anomalyFlagsJson,
        expenseCategories = ExpenseCategoryRefDto(code = categoryCode),
    )

    // ─── 1. the status vocabulary, every value both directions ───────────

    @Test
    fun `every DB status maps to the domain status`() {
        assertEquals("draft", expenseStatusFromDb("draft"))
        assertEquals("submitted", expenseStatusFromDb("pending_approval"))
        assertEquals("approved", expenseStatusFromDb("approved_funds_released"))
        assertEquals("rejected", expenseStatusFromDb("rejected"))
        assertEquals("disbursed", expenseStatusFromDb("disbursed"))
        assertEquals("settled", expenseStatusFromDb("settled_and_closed"))
        // Unknown codes degrade to draft (the desktop's ?? "draft").
        assertEquals("draft", expenseStatusFromDb("something_new"))
    }

    @Test
    fun `every domain status maps to the DB status`() {
        assertEquals("draft", expenseStatusToDb("draft"))
        assertEquals("pending_approval", expenseStatusToDb("submitted"))
        assertEquals("approved_funds_released", expenseStatusToDb("approved"))
        assertEquals("rejected", expenseStatusToDb("rejected"))
        assertEquals("disbursed", expenseStatusToDb("disbursed"))
        assertEquals("settled_and_closed", expenseStatusToDb("settled"))
    }

    // ─── 2. the category codes, both directions ──────────────────────────

    @Test
    fun `DB category codes map to the domain with the documented lossy buckets`() {
        assertEquals("maintenance", expenseCategoryFromDb("maintenance"))
        assertEquals("supplies", expenseCategoryFromDb("office_supplies"))
        assertEquals("supplies", expenseCategoryFromDb("educational_material")) // lossy
        assertEquals("utilities", expenseCategoryFromDb("utilities"))
        assertEquals("transport", expenseCategoryFromDb("transport"))
        assertEquals("other", expenseCategoryFromDb("it")) // lossy
        assertEquals("rent", expenseCategoryFromDb("facilities"))
        assertEquals("other", expenseCategoryFromDb("medical")) // lossy
        assertEquals("other", expenseCategoryFromDb("other"))
        assertEquals("other", expenseCategoryFromDb(null))
    }

    @Test
    fun `domain categories map to the DB codes`() {
        assertEquals("utilities", expenseCategoryToDb("utilities"))
        assertEquals("office_supplies", expenseCategoryToDb("supplies"))
        assertEquals("maintenance", expenseCategoryToDb("maintenance"))
        assertEquals("transport", expenseCategoryToDb("transport"))
        assertEquals("other", expenseCategoryToDb("event")) // no DB equivalent
        assertEquals("other", expenseCategoryToDb("salary")) // payroll is not a category
        assertEquals("other", expenseCategoryToDb("tax")) // no DB equivalent
        assertEquals("facilities", expenseCategoryToDb("rent"))
        assertEquals("other", expenseCategoryToDb("other"))
    }

    // ─── 3. the row mapping: money, urgency, notes, attribution ──────────

    @Test
    fun `the row mapping converts DZD to centimes and keeps the identity fields`() {
        val e = dto().toEntity()
        assertEquals("srv-1", e.id)
        assertEquals("EXP-2026-A1B2C3", e.requestCode)
        // 12 500,50 DZD → 1 250 050 centimes (the ×100 convention).
        assertEquals(1_250_050L, e.amount)
        assertEquals("supplies", e.category)
        assertEquals("Librairie En-Nour", e.payee)
        assertEquals("submitted", e.status)
        assertEquals("2026-10-01T10:00:00Z", e.submittedAt)
    }

    @Test
    fun `the critical urgency reads as high`() {
        assertEquals("high", dto(urgency = "critical").toEntity().urgency)
        assertEquals("low", dto(urgency = "low").toEntity().urgency)
    }

    @Test
    fun `approval_note wins over rejected_reason and lands in notes`() {
        assertEquals("Approuvé", dto(status = "approved", approvalNote = "Approuvé").toEntity().notes)
        assertEquals("Trop cher", dto(status = "rejected", rejectedReason = "Trop cher").toEntity().notes)
        // The desktop's approvalNote ?? rejected_reason precedence.
        assertEquals(
            "Note",
            dto(approvalNote = "Note", rejectedReason = "Reason").toEntity().notes,
        )
    }

    @Test
    fun `the settled row carries the proof attribution and the final amount`() {
        val e = dto(
            status = "settled_and_closed",
            settledAt = "2026-10-03T09:00:00Z",
            receiptPath = "t1/receipts/123.pdf",
            receiptUploadedBy = "usr-1",
            receiptUploadedAt = "2026-10-03T09:00:00Z",
            finalSpentAmount = 12345.67,
        ).toEntity()
        assertEquals("settled", e.status)
        assertEquals("t1/receipts/123.pdf", e.proofUrl)
        assertEquals("usr-1", e.proofUploadedBy)
        assertEquals("2026-10-03T09:00:00Z", e.proofUploadedAt)
        // 12 345,67 DZD → 1 234 567 centimes.
        assertEquals(1_234_567L, e.finalSpentAmount)
    }

    @Test
    fun `the anomaly flags decode to the joined note text`() {
        val flags = kotlinx.serialization.json.Json.parseToJsonElement(
            """["Montant élevé", {"explanation": "Fournisseur inconnu"}]""",
        )
        val e = dto(anomalyScore = 0.8, anomalyFlagsJson = flags).toEntity()
        assertEquals(0.8, e.anomalyScore, 0.0001)
        assertEquals("Montant élevé\nFournisseur inconnu", e.anomalyNote)
    }

    @Test
    fun `a null anomaly flags payload reads as no note`() {
        assertNull(dto().toEntity().anomalyNote)
        val emptyArray = kotlinx.serialization.json.Json.parseToJsonElement("[]")
        assertNull(dto(anomalyFlagsJson = emptyArray).toEntity().anomalyNote)
    }

    @Test
    fun `a null proof upload falls back to settled_at for the timeline`() {
        // The desktop's shape: settled rows without an explicit upload
        // timestamp still render the 4th timeline stage (settled_at).
        val e = dto(status = "settled_and_closed", settledAt = "2026-10-03T09:00:00Z").toEntity()
        assertEquals("2026-10-03T09:00:00Z", e.proofUploadedAt)
        assertNull(e.proofUploadedBy)
    }

    @Test
    fun `a null payee reads as the empty string - never a crash`() {
        assertEquals("", dto(payee = null).toEntity().payee)
    }
}
