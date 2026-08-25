package com.example.domain.repository

import com.example.core.Result
import java.io.File

/**
 * PDF document generation contract.
 *
 * Renders French-language PDFs (A4) for the financial module:
 *  - payment receipts (single transaction) — parity with the desktop's
 *    `src/infrastructure/receipt-pdf/payment-receipt.ts`
 *  - account statements (parent-level ledger summary) — parity with the
 *    desktop's `src/infrastructure/receipt-pdf/account-statement.ts`
 *
 * Generated files live in the app cache directory and are shared via
 * FileProvider + ACTION_SEND; they are never persisted as domain state.
 */
interface PdfRepository {
    /** Render the receipt PDF for a single payment. Returns the written file. */
    suspend fun generatePaymentReceipt(paymentId: String): Result<File>

    /** Render the account-statement PDF for a parent (balances + recent movements). */
    suspend fun generateAccountStatement(parentId: String): Result<File>

    /**
     * Render one of the five macro-reports (Rapports tab) as a paginated A4
     * PDF assembled from REAL Room data:
     *   `revenu-mensuel`   — current-month collections by method + category
     *   `creances-agees`   — outstanding balances per family with aging bucket
     *   `effectifs-niveau` — active students per level + per class
     *   `depenses-categorie` — expenses per category with status breakdown
     *   `annuaire-personnel` — staff directory (salary column requires
     *                          VIEW_SALARY — enforced by the calling UI)
     *
     * @return the written PDF file, ready to share via FileProvider.
     */
    suspend fun generateMacroReport(reportId: String): Result<File>

    /**
     * Render the student's term bulletin ("bulletin de notes") — per desktop
     * spec §5.1, entity-specific reports live in their profile drawer, which
     * is exactly the Notes & Bulletins tab.
     *
     * Contents (all computed with the canonical engines):
     *  - Per-subject table: coefficients, D1 / D2 / Examen, subject averages.
     *  - Overall GPA via `computeOverallGpa` (extracurricular excluded,
     *    incomplete averages skipped) + the standard French mention.
     *  - Class rank and class average for context when available.
     *
     * @return the written PDF file, ready to share via FileProvider.
     */
    suspend fun generateStudentBulletin(studentId: String, term: String, academicYear: String): Result<File>
}
