package com.example.core

/**
 * Reconciliation engine — pure function. Mirrors desktop `src/domain/reconcile.ts`.
 * All violation CODE strings are wire-protocol — preserved verbatim for
 * cross-platform audit log compatibility.
 */
object Reconcile {
    const val CODE_DUPLICATE_ENTRY_ID              = "DUPLICATE_ENTRY_ID"
    const val CODE_MISSING_ID                      = "MISSING_ID"
    const val CODE_MISSING_TENANT_ID               = "MISSING_TENANT_ID"
    const val CODE_MISSING_ACCOUNT_ID              = "MISSING_ACCOUNT_ID"
    const val CODE_MISSING_PARENT_ID               = "MISSING_PARENT_ID"
    const val CODE_INVALID_AMOUNT                  = "INVALID_AMOUNT"
    const val CODE_MISSING_TYPE                    = "MISSING_TYPE"
    const val CODE_MISSING_SOURCE_TYPE             = "MISSING_SOURCE_TYPE"
    const val CODE_MISSING_SOURCE_ID               = "MISSING_SOURCE_ID"
    const val CODE_MISSING_DESCRIPTION             = "MISSING_DESCRIPTION"
    const val CODE_MISSING_ACTOR_ID                = "MISSING_ACTOR_ID"
    const val CODE_MISSING_ACTOR_NAME              = "MISSING_ACTOR_NAME"
    const val CODE_MISSING_TIMESTAMP               = "MISSING_TIMESTAMP"
    const val CODE_CHARGE_NOT_POSITIVE             = "CHARGE_NOT_POSITIVE"
    const val CODE_PAYMENT_NOT_NEGATIVE            = "PAYMENT_NOT_NEGATIVE"
    const val CODE_REFUND_NOT_NEGATIVE             = "REFUND_NOT_NEGATIVE"
    const val CODE_ADJUSTMENT_ZERO                 = "ADJUSTMENT_ZERO"
    const val CODE_ACCOUNT_ID_MISMATCH             = "ACCOUNT_ID_MISMATCH"
    const val CODE_ORPHAN_REVERSAL                 = "ORPHAN_REVERSAL"
    const val CODE_DOUBLE_REVERSAL                 = "DOUBLE_REVERSAL"
    const val CODE_REVERSAL_AMOUNT_MISMATCH        = "REVERSAL_AMOUNT_MISMATCH"
    const val CODE_REVERSAL_ACCOUNT_MISMATCH       = "REVERSAL_ACCOUNT_MISMATCH"
    const val CODE_DUPLICATE_RECEIPT_NUMBER        = "DUPLICATE_RECEIPT_NUMBER"
    const val CODE_TENANT_MISMATCH                 = "TENANT_MISMATCH"
    const val CODE_PAYMENT_WITHOUT_LEDGER_ENTRY    = "PAYMENT_WITHOUT_LEDGER_ENTRY"
    const val CODE_PAYMENT_AMOUNT_MISMATCH         = "PAYMENT_AMOUNT_MISMATCH"
    const val CODE_PAYMENT_STATUS_MISMATCH         = "PAYMENT_STATUS_MISMATCH"
    const val CODE_INSTALLMENT_WITHOUT_LEDGER_ENTRY = "INSTALLMENT_WITHOUT_LEDGER_ENTRY"
    const val CODE_INSTALLMENT_AMOUNT_MISMATCH     = "INSTALLMENT_AMOUNT_MISMATCH"
    const val CODE_BALANCE_SUM_MISMATCH            = "BALANCE_SUM_MISMATCH"
    // TIER 2 R10 — unified-architecture cross-check codes (canonical)
    const val CODE_UNBACKED_TRANCHE_SATISFACTION   = "UNBACKED_TRANCHE_SATISFACTION"
    const val CODE_PAYMENT_LEDGER_MISMATCH          = "PAYMENT_LEDGER_MISMATCH"
    const val CODE_UNBACKED_PARENT_CREDIT          = "UNBACKED_PARENT_CREDIT"

    enum class Severity { ERROR, WARNING, INFO }

    data class Violation(
        val severity: Severity, val code: String, val message: String,
        val entryId: String? = null, val accountId: String? = null,
        val details: Map<String, Any?> = emptyMap(),
    )

    data class Report(
        val checkedAt: String, val entryCount: Int, val accountCount: Int,
        val violations: List<Violation>,
    ) {
        val passed: Boolean get() = violations.none { it.severity == Severity.ERROR }
        val errorCount: Int get() = violations.count { it.severity == Severity.ERROR }
        val warningCount: Int get() = violations.count { it.severity == Severity.WARNING }
        val infoCount: Int get() = violations.count { it.severity == Severity.INFO }
    }

    data class CrossCheckInputs(
        val payments: List<PaymentCrossCheck>? = null,
        val installments: List<InstallmentCrossCheck>? = null,
        /**
         * TIER 2 R10 — inputs for `crossCheckParentCredit`.
         *
         * Optional list of parent summaries (one per parent). When non-null,
         * the cross-check verifies that every parent account with a negative
         * balance strictly corresponds to a `parent_credit` adjustment entry.
         */
        val parentSummaries: List<ParentSummaryCrossCheck>? = null,
        /**
         * TIER 2 R10 — inputs for `crossCheckInstallmentPayments` (precise mode).
         *
         * Optional map: paymentId → installmentId. When non-empty, the
         * cross-check verifies each installment's `amountPaid` is fully
         * backed by cleared payment entries mapped to it. When empty,
         * the cross-check falls back to per-account aggregate check.
         */
        val paymentToInstallmentId: Map<String, String>? = null,
    )

    data class PaymentCrossCheck(val id: String, val amount: Long, val status: PaymentStatus)

    data class InstallmentCrossCheck(
        val id: String,
        val parentId: String,
        val studentId: String?,
        val category: String,
        val amountDue: Long,
        val label: String,
        val status: String = "",
        val amountPaid: Long = 0L,
    )

    /**
     * TIER 2 R10 — summary view for `crossCheckParentCredit`.
     * Mirrors the desktop's `parentSummaries` parameter shape.
     */
    data class ParentSummaryCrossCheck(
        val parentId: String,
        val parentName: String,
        val totalOutstanding: Long,
        val accounts: List<ParentAccountCrossCheck> = emptyList(),
    )

    data class ParentAccountCrossCheck(
        val accountId: String,
        val category: String,
        val studentId: String?,
        val balance: Long,
        val unallocatedCredit: Long = 0L,
    )

    fun reconcileLedger(entries: List<LedgerEntry>, crossCheckInputs: CrossCheckInputs = CrossCheckInputs()): Report {
        val violations = mutableListOf<Violation>()
        violations += checkDuplicateIds(entries)
        violations += checkRequiredFields(entries)
        violations += checkSignedAmountConvention(entries)
        violations += checkAccountIdsMatch(entries)
        violations += checkReversalIntegrity(entries)
        violations += checkDuplicateReceiptNumbers(entries)
        violations += checkTenantConsistency(entries)
        violations += crossCheckPayments(entries, crossCheckInputs)
        violations += crossCheckInstallments(entries, crossCheckInputs)
        violations += crossCheckBalanceSum(entries)
        // TIER 2 R10 — the 3 unified-architecture cross-checks.
        // These close the gap with the desktop's 6-check reconciler.
        violations += crossCheckInstallmentPayments(entries, crossCheckInputs)
        violations += crossCheckClearedBalance(entries, crossCheckInputs)
        violations += crossCheckParentCredit(entries, crossCheckInputs)

        return Report(
            checkedAt = java.time.Instant.now().toString(),
            entryCount = entries.size,
            accountCount = entries.map { it.accountId }.distinct().size,
            violations = violations,
        )
    }

    private fun checkDuplicateIds(entries: List<LedgerEntry>): List<Violation> {
        val dups = entries.groupBy { it.id }.filter { it.value.size > 1 }
        return dups.flatMap { (id, dupEntries) ->
            dupEntries.map { Violation(Severity.ERROR, CODE_DUPLICATE_ENTRY_ID, "Entry ID '$id' appears ${dupEntries.size} times", entryId = id) }
        }
    }

    private fun checkRequiredFields(entries: List<LedgerEntry>): List<Violation> {
        val out = mutableListOf<Violation>()
        for (e in entries) {
            if (e.id.isBlank()) out += Violation(Severity.ERROR, CODE_MISSING_ID, "Missing id", entryId = e.id)
            if (e.tenantId.isBlank()) out += Violation(Severity.ERROR, CODE_MISSING_TENANT_ID, "Missing tenantId", entryId = e.id)
            if (e.accountId.isBlank()) out += Violation(Severity.ERROR, CODE_MISSING_ACCOUNT_ID, "Missing accountId", entryId = e.id)
            if (e.parentId.isBlank()) out += Violation(Severity.ERROR, CODE_MISSING_PARENT_ID, "Missing parentId", entryId = e.id)
            if (e.amount == 0L && e.type != LedgerEntryType.ADJUSTMENT) out += Violation(Severity.ERROR, CODE_INVALID_AMOUNT, "Amount is 0 or invalid", entryId = e.id)
            if (e.at.isBlank()) out += Violation(Severity.ERROR, CODE_MISSING_TIMESTAMP, "Missing at", entryId = e.id)
            if (e.description.isBlank()) out += Violation(Severity.ERROR, CODE_MISSING_DESCRIPTION, "Missing description", entryId = e.id)
            if (e.actorId.isBlank()) out += Violation(Severity.WARNING, CODE_MISSING_ACTOR_ID, "Missing actorId (anonymous forbidden)", entryId = e.id)
            if (e.actorName.isBlank()) out += Violation(Severity.WARNING, CODE_MISSING_ACTOR_NAME, "Missing actorName", entryId = e.id)
        }
        return out
    }

    private fun checkSignedAmountConvention(entries: List<LedgerEntry>): List<Violation> {
        val out = mutableListOf<Violation>()
        for (e in entries) {
            when (e.type) {
                LedgerEntryType.CHARGE -> if (e.amount <= 0) out += Violation(Severity.ERROR, CODE_CHARGE_NOT_POSITIVE, "Charge amount must be > 0 (got ${e.amount})", entryId = e.id)
                LedgerEntryType.PAYMENT -> if (e.amount >= 0) out += Violation(Severity.ERROR, CODE_PAYMENT_NOT_NEGATIVE, "Payment amount must be < 0 (got ${e.amount})", entryId = e.id)
                LedgerEntryType.REFUND -> if (e.amount >= 0) out += Violation(Severity.ERROR, CODE_REFUND_NOT_NEGATIVE, "Refund amount must be < 0 (got ${e.amount})", entryId = e.id)
                LedgerEntryType.ADJUSTMENT -> if (e.amount == 0L) out += Violation(Severity.ERROR, CODE_ADJUSTMENT_ZERO, "Adjustment amount must be != 0", entryId = e.id)
                LedgerEntryType.REVERSAL, LedgerEntryType.TRANSFER -> {}
            }
        }
        return out
    }

    private fun checkAccountIdsMatch(entries: List<LedgerEntry>): List<Violation> =
        entries.mapNotNull { e ->
            val expected = deriveAccountId(e.parentId, e.category, e.studentId)
            if (e.accountId != expected) Violation(Severity.ERROR, CODE_ACCOUNT_ID_MISMATCH, "accountId '${e.accountId}' does not match derived '$expected'", entryId = e.id, accountId = e.accountId) else null
        }

    private fun checkReversalIntegrity(entries: List<LedgerEntry>): List<Violation> {
        val out = mutableListOf<Violation>()
        val byId = entries.associateBy { it.id }
        val reversedOriginals = mutableMapOf<String, Int>()
        for (e in entries) {
            val revId = e.reversesId ?: continue
            val original = byId[revId]
            if (original == null) {
                out += Violation(Severity.ERROR, CODE_ORPHAN_REVERSAL, "Reversal ${e.id} references non-existent entry $revId", entryId = e.id)
                continue
            }
            reversedOriginals[revId] = (reversedOriginals[revId] ?: 0) + 1
            if (e.amount != -original.amount) out += Violation(Severity.ERROR, CODE_REVERSAL_AMOUNT_MISMATCH, "Reversal ${e.id} amount ${e.amount} does not equal -original.amount ${-original.amount}", entryId = e.id)
            if (e.accountId != original.accountId) out += Violation(Severity.ERROR, CODE_REVERSAL_ACCOUNT_MISMATCH, "Reversal ${e.id} accountId does not match original", entryId = e.id)
        }
        for ((origId, count) in reversedOriginals) {
            if (count > 1) out += Violation(Severity.ERROR, CODE_DOUBLE_REVERSAL, "Entry $origId reversed $count times", entryId = origId)
        }
        return out
    }

    private fun checkDuplicateReceiptNumbers(entries: List<LedgerEntry>): List<Violation> {
        val out = mutableListOf<Violation>()
        for ((tenantId, tenantEntries) in entries.groupBy { it.tenantId }) {
            for ((receipt, dupEntries) in tenantEntries.filter { !it.receiptNumber.isNullOrBlank() }.groupBy { it.receiptNumber }.filter { it.value.size > 1 }) {
                out += Violation(Severity.ERROR, CODE_DUPLICATE_RECEIPT_NUMBER, "Receipt number '$receipt' appears ${dupEntries.size} times in tenant $tenantId", entryId = dupEntries.first().id)
            }
        }
        return out
    }

    private fun checkTenantConsistency(entries: List<LedgerEntry>): List<Violation> {
        val tenants = entries.map { it.tenantId }.distinct()
        if (tenants.size <= 1) return emptyList()
        return listOf(Violation(Severity.ERROR, CODE_TENANT_MISMATCH, "Ledger contains entries from ${tenants.size} different tenants: $tenants"))
    }

    private fun crossCheckPayments(entries: List<LedgerEntry>, inputs: CrossCheckInputs): List<Violation> {
        val payments = inputs.payments ?: return emptyList()
        val out = mutableListOf<Violation>()
        val paymentEntriesBySourceId = entries.filter { it.sourceType == LedgerSourceType.PAYMENT }.associateBy { it.sourceId }
        for (payment in payments) {
            val matchingEntry = paymentEntriesBySourceId[payment.id]
            if (matchingEntry == null) {
                out += Violation(Severity.WARNING, CODE_PAYMENT_WITHOUT_LEDGER_ENTRY, "Payment ${payment.id} has no matching ledger entry", entryId = payment.id)
                continue
            }
            if (kotlin.math.abs(matchingEntry.amount) != payment.amount) out += Violation(Severity.ERROR, CODE_PAYMENT_AMOUNT_MISMATCH, "Payment ${payment.id} amount ${payment.amount} does not match ledger entry amount ${matchingEntry.amount}", entryId = matchingEntry.id)
            if (matchingEntry.paymentStatus != payment.status) out += Violation(Severity.WARNING, CODE_PAYMENT_STATUS_MISMATCH, "Payment ${payment.id} status does not match ledger entry", entryId = matchingEntry.id)
        }
        return out
    }

    private fun crossCheckInstallments(entries: List<LedgerEntry>, inputs: CrossCheckInputs): List<Violation> {
        val installments = inputs.installments ?: return emptyList()
        val out = mutableListOf<Violation>()
        val chargeEntriesBySourceId = entries.filter { it.sourceType == LedgerSourceType.INSTALLMENT }.associateBy { it.sourceId }
        for (inst in installments) {
            val matchingEntry = chargeEntriesBySourceId[inst.id]
            if (matchingEntry == null) {
                out += Violation(Severity.WARNING, CODE_INSTALLMENT_WITHOUT_LEDGER_ENTRY, "Installment ${inst.id} has no matching charge entry", entryId = inst.id)
                continue
            }
            if (matchingEntry.amount != inst.amountDue) out += Violation(Severity.ERROR, CODE_INSTALLMENT_AMOUNT_MISMATCH, "Installment ${inst.id} amountDue ${inst.amountDue} does not match ledger entry amount ${matchingEntry.amount}", entryId = matchingEntry.id)
        }
        return out
    }

    /**
     * TIER 2 R10 — verify that every installment's `amountPaid` is fully
     * backed by cleared payment ledger entries allocated to it (plus credit
     * adjustments). Emits `UNBACKED_TRANCHE_SATISFACTION` when a tranche
     * is marked `paid` (or has `amountPaid > 0`) but the ledger has
     * insufficient cleared funds to back it — the canonical signature of
     * the "pending check marked tranche as paid" bug.
     *
     * Mirrors the desktop's `crossCheckInstallmentPayments` in
     * `domain/calc/reconcile/cross-checks.ts`.
     *
     * Two modes:
     *  - **precise**: when `paymentToInstallmentId` is non-empty, each
     *    installment's amountPaid is compared to the exact cleared payments
     *    mapped to it via the lookup table.
     *  - **aggregate**: otherwise, Σ installment.amountDue per account is
     *    compared to Σ cleared payments + credit adjustments on that
     *    account. Avoids false positives when a parent has multiple
     *    installments on the same account.
     */
    private fun crossCheckInstallmentPayments(entries: List<LedgerEntry>, inputs: CrossCheckInputs): List<Violation> {
        val installments = inputs.installments ?: return emptyList()
        val out = mutableListOf<Violation>()
        val tolerance = 1L // 0.01 DZD (centime)

        val accountKey = { parentId: String, category: String, studentId: String? ->
            "$parentId|$category|${studentId ?: ""}"
        }

        // Aggregate cleared payments + credit adjustments per account.
        val clearedByAccount = mutableMapOf<String, Long>()
        val adjustmentsByAccount = mutableMapOf<String, Long>()
        for (e in entries) {
            if (e.reversesId != null) continue // skip reversals
            val key = accountKey(e.parentId, e.category.code, e.studentId)
            if (e.type == LedgerEntryType.PAYMENT && e.paymentStatus == PaymentStatus.PAID) {
                clearedByAccount[key] = (clearedByAccount[key] ?: 0L) + kotlin.math.abs(e.amount)
            } else if (e.type == LedgerEntryType.ADJUSTMENT && e.amount < 0) {
                // Negative adjustments are credits that can satisfy tranches.
                adjustmentsByAccount[key] = (adjustmentsByAccount[key] ?: 0L) + kotlin.math.abs(e.amount)
            }
        }

        // Precise mode: per-installment attribution.
        val preciseClearedByInstallment = mutableMapOf<String, Long>()
        val payToInst = inputs.paymentToInstallmentId
        if (payToInst != null && payToInst.isNotEmpty()) {
            for (e in entries) {
                if (e.type != LedgerEntryType.PAYMENT || e.paymentStatus != PaymentStatus.PAID) continue
                if (e.reversesId != null) continue
                val installmentId = payToInst[e.sourceId] ?: continue
                preciseClearedByInstallment[installmentId] =
                    (preciseClearedByInstallment[installmentId] ?: 0L) + kotlin.math.abs(e.amount)
            }
        }

        // Per-installment precise check (when paymentToInstallmentId provided).
        for (inst in installments) {
            val precise = preciseClearedByInstallment[inst.id] ?: continue
            val diff = inst.amountPaid - precise
            if (kotlin.math.abs(diff) > tolerance) {
                out += Violation(
                    Severity.ERROR, CODE_UNBACKED_TRANCHE_SATISFACTION,
                    "Installment ${inst.id} (${inst.label}) has amountPaid=${inst.amountPaid} " +
                        "but precise cleared ledger backing=$precise (diff=$diff). Status=\"${inst.status}\".",
                    details = mapOf(
                        "installmentId" to inst.id,
                        "amountPaid" to inst.amountPaid,
                        "clearedBacking" to precise,
                        "diff" to diff,
                        "status" to inst.status,
                        "mode" to "precise",
                    ),
                )
            }
        }

        // Account-level aggregate check (fallback when no precise map).
        if (payToInst == null || payToInst.isEmpty()) {
            val amountPaidByAccount = mutableMapOf<String, Long>()
            val installmentCountByAccount = mutableMapOf<String, Int>()
            for (inst in installments) {
                val key = accountKey(inst.parentId, inst.category, inst.studentId)
                amountPaidByAccount[key] = (amountPaidByAccount[key] ?: 0L) + inst.amountPaid
                installmentCountByAccount[key] = (installmentCountByAccount[key] ?: 0) + 1
            }
            for ((key, totalAmountPaid) in amountPaidByAccount) {
                val cleared = clearedByAccount[key] ?: 0L
                val adjustments = adjustmentsByAccount[key] ?: 0L
                val backing = cleared + adjustments
                val diff = totalAmountPaid - backing
                if (kotlin.math.abs(diff) > tolerance) {
                    val count = installmentCountByAccount[key] ?: 1
                    val parts = key.split("|")
                    val parentId = parts.getOrNull(0) ?: ""
                    val category = parts.getOrNull(1) ?: ""
                    val studentId = parts.getOrNull(2)?.ifEmpty { null }
                    val isOverbacked = diff < 0
                    out += Violation(
                        if (isOverbacked) Severity.WARNING else Severity.ERROR,
                        CODE_UNBACKED_TRANCHE_SATISFACTION,
                        "Account parent=$parentId category=$category student=${studentId ?: "—"} " +
                            "has $count installment(s) with Σ amountPaid=$totalAmountPaid " +
                            "but cleared ledger backing=$backing (diff=$diff)." +
                            (if (isOverbacked) " [over-backed — excess should be parent_credit]" else ""),
                        details = mapOf(
                            "accountKey" to key,
                            "parentId" to parentId,
                            "category" to category,
                            "studentId" to (studentId ?: null),
                            "installmentCount" to count,
                            "totalAmountPaid" to totalAmountPaid,
                            "clearedBacking" to backing,
                            "diff" to diff,
                            "mode" to "account_aggregate",
                            "overbacked" to isOverbacked,
                        ),
                    )
                }
            }
        }
        return out
    }

    /**
     * TIER 2 R10 — verify that the sum of cleared payments in the payments
     * table equals the sum of cleared payment credits on the ledger.
     *
     * Invariant: Σ payments.amount where status='paid'
     *            ≡ Σ |ledger_entries.amount| where type='payment' AND paymentStatus='paid'
     *                             AND id NOT IN (set of reversed entry ids)
     *
     * Reversed entries are excluded from the ledger sum — their contribution
     * is canceled out by the reversal, so they should not be counted as
     * "cleared".
     *
     * Emits `PAYMENT_LEDGER_MISMATCH` (error) on discrepancy > 0.01 DZD.
     */
    private fun crossCheckClearedBalance(entries: List<LedgerEntry>, inputs: CrossCheckInputs): List<Violation> {
        val payments = inputs.payments ?: return emptyList()
        val tolerance = 1L // 0.01 DZD (centime)
        val out = mutableListOf<Violation>()

        val reversedIds = entries.filter { it.reversesId != null }.map { it.reversesId!! }.toSet()
        val paymentsCleared = payments.filter { it.status == PaymentStatus.PAID }.sumOf { it.amount }
        val ledgerCleared = entries
            .filter { it.type == LedgerEntryType.PAYMENT && it.paymentStatus == PaymentStatus.PAID }
            .filter { it.id !in reversedIds }
            .sumOf { kotlin.math.abs(it.amount) }
        if (kotlin.math.abs(paymentsCleared - ledgerCleared) > tolerance) {
            out += Violation(
                Severity.ERROR, CODE_PAYMENT_LEDGER_MISMATCH,
                "Sum of cleared payments ($paymentsCleared) does not equal sum of cleared " +
                    "payment ledger entries ($ledgerCleared).",
                details = mapOf(
                    "paymentsCleared" to paymentsCleared,
                    "ledgerCleared" to ledgerCleared,
                    "diff" to (paymentsCleared - ledgerCleared),
                ),
            )
        }
        return out
    }

    /**
     * TIER 2 R10 — verify that every parent account with a negative balance
     * (school owes the parent) strictly corresponds to a `parent_credit`
     * adjustment entry.
     *
     * Emits `UNBACKED_PARENT_CREDIT` (warning) when a negative balance exists
     * without a corresponding `parent_credit` entry — this typically indicates
     * an overpayment that was logged as a regular payment without generating
     * the explicit credit adjustment, which breaks auto-absorption on future
     * invoices.
     *
     * Also checks per-account: a non-`parent_credit` account with negative
     * balance is a bug — overpayments should always land on the
     * `parent:X:category:parent_credit` account (INV-7).
     */
    private fun crossCheckParentCredit(entries: List<LedgerEntry>, inputs: CrossCheckInputs): List<Violation> {
        val parentSummaries = inputs.parentSummaries ?: return emptyList()
        val out = mutableListOf<Violation>()
        val tolerance = 1L // 0.01 DZD (centime)

        // Build a set of parentIds that have at least one parent_credit
        // adjustment entry (not a reversal of one).
        val parentsWithCreditEntry = mutableSetOf<String>()
        for (e in entries) {
            if (e.category == PaymentCategory.PARENT_CREDIT
                && e.type == LedgerEntryType.ADJUSTMENT
                && e.reversesId == null
            ) {
                parentsWithCreditEntry.add(e.parentId)
            }
        }

        for (p in parentSummaries) {
            // Negative outstanding balance — school owes parent.
            if (p.totalOutstanding < -tolerance) {
                if (p.parentId !in parentsWithCreditEntry) {
                    out += Violation(
                        Severity.WARNING, CODE_UNBACKED_PARENT_CREDIT,
                        "Parent ${p.parentId} (${p.parentName}) has negative outstanding balance " +
                            "${p.totalOutstanding} but no parent_credit adjustment entry exists " +
                            "on the ledger. Auto-absorption on future invoices will not work.",
                        details = mapOf(
                            "parentId" to p.parentId,
                            "outstanding" to p.totalOutstanding,
                        ),
                    )
                }
            }
            // Per-account negative balances.
            for (acc in p.accounts) {
                if (acc.balance < -tolerance && acc.category != PaymentCategory.PARENT_CREDIT.code) {
                    out += Violation(
                        Severity.WARNING, CODE_UNBACKED_PARENT_CREDIT,
                        "Account ${acc.accountId} (parent ${p.parentId}, category ${acc.category}) " +
                            "has negative balance ${acc.balance} but is not a parent_credit account. " +
                            "Overpayments should be stored as explicit parent_credit adjustments.",
                        accountId = acc.accountId,
                        details = mapOf(
                            "accountId" to acc.accountId,
                            "balance" to acc.balance,
                            "category" to acc.category,
                        ),
                    )
                }
            }
        }
        return out
    }

    private fun crossCheckBalanceSum(entries: List<LedgerEntry>): List<Violation> {
        if (entries.isEmpty()) return emptyList()
        val accountIds = entries.map { it.accountId }.distinct()
        val sumOfEntries = entries.sumOf { it.amount }
        val sumOfBalances = accountIds.sumOf { LedgerEngine.computeAccountBalance(entries, it).balance }
        val drift = kotlin.math.abs(sumOfEntries - sumOfBalances)
        return if (drift > 100L) listOf(Violation(Severity.ERROR, CODE_BALANCE_SUM_MISMATCH, "Sum of entries ($sumOfEntries) does not match sum of balances ($sumOfBalances); drift = $drift centimes")) else emptyList()
    }
}
