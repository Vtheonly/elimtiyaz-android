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
}
