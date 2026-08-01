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
    )

    data class PaymentCrossCheck(val id: String, val amount: Long, val status: PaymentStatus)
    data class InstallmentCrossCheck(val id: String, val amountDue: Long)

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

    private fun crossCheckBalanceSum(entries: List<LedgerEntry>): List<Violation> {
        if (entries.isEmpty()) return emptyList()
        val accountIds = entries.map { it.accountId }.distinct()
        val sumOfEntries = entries.sumOf { it.amount }
        val sumOfBalances = accountIds.sumOf { LedgerEngine.computeAccountBalance(entries, it).balance }
        val drift = kotlin.math.abs(sumOfEntries - sumOfBalances)
        return if (drift > 100L) listOf(Violation(Severity.ERROR, CODE_BALANCE_SUM_MISMATCH, "Sum of entries ($sumOfEntries) does not match sum of balances ($sumOfBalances); drift = $drift centimes")) else emptyList()
    }
}
