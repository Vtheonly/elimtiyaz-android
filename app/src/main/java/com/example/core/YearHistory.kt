package com.example.core

import com.example.core.LedgerEntryType.PAYMENT
import com.example.domain.model.Installment
import com.example.domain.model.Payment

/**
 * Year Tracking — the canonical year-by-year financial history engine,
 * the Kotlin mirror of the desktop's
 * `src/domain/calc/ledger/year-history.ts` (T-436/T-442; financial-rules
 * §17; ADR-030). Ported for T-456 (128th session).
 *
 * THE canonical read-side derivation of "who owed what, for what, in
 * WHICH academic year, and how their financial state changed from one
 * year to the next". It is an ANALYSIS layer on TOP of the existing
 * finance system (the §15.53a rule): it creates NO second ledger, NO
 * second balance formula, NO second allocation engine, NO second pricing
 * system. Every input is an existing canonical fact:
 *
 *   - the year a charge BELONGS to   = the installment's persisted
 *     `academicCycle` (the year code — see DebtAging.kt for the platform
 *     adaptation note), else the INV-14 date rule — the ONE precedence
 *     (INV-18a);
 *   - the year a payment was MADE in = the INV-14 rule on `collectedAt`
 *     (Android payments carry no persisted year column — the desktop's
 *     documented fallback path);
 *   - the year a payment SETTLES     = the ALLOCATED INSTALLMENT's
 *     attributed year — settlement is read from allocation rows, never
 *     inferred from amounts or dates (INV-18d);
 *   - every amount = a stored column (`amountDue`, `amountPaid`,
 *     `amountPending`, an allocation's `allocatedAmount`) or the INV-4
 *     remaining — the SAME numbers the Créances surfaces show (INV-20a).
 *
 * T-442 (UI-323) extensions included verbatim: the per-year service
 * breakdown (INV-20e), the per-payment coverage lines (honestly
 * basis-flagged when allocation rows are absent), and the per-year
 * still-owed-now figure + its enumeration on the parent record.
 *
 * Pure and deterministic: same inputs + same clock → same records
 * (INV-20b). Amounts are Long centimes (the Android convention).
 */

// ============================================================================
// The public record contract (the desktop's types, verbatim)
// ============================================================================

/** INV-4 epsilon — outstanding at or below this is settled. The desktop
 *  works in DZD doubles (0.001 DZD); Android's Long centimes make the
 *  degenerate-but-equivalent check `<= 0` (0.001 DZD = 0.1 centime). */
const val YEAR_HISTORY_EPSILON_CENTIMES: Long = 0L

enum class YearServiceGroupKey {
    REGISTRATION, TUITION, TRANSPORT, SERVICE;

    /** §15.3-style single-wording rule: the canonical FR labels. */
    val labelFr: String
        get() = when (this) {
            REGISTRATION -> "Frais d'inscription (FI)"
            TUITION -> "Scolarité"
            TRANSPORT -> "Transport"
            SERVICE -> "Prestation"
        }
}

/**
 * T-442 (UI-323) — the year's charges grouped by billable service.
 * Amounts are Σ stored columns / Σ INV-4 remaining over the group's
 * charges (INV-20a — no new numbers). `charges` carries the indexes of
 * the year record's charge items (one fact, one object — on Kotlin the
 * data class is small and immutable, so the list holds the same values).
 */
data class YearServiceGroup(
    val key: YearServiceGroupKey,
    /** The canonical category the group renders (services: the concrete one). */
    val category: com.example.core.PaymentCategory,
    val chargeCount: Int,
    /** The tranche waves present in the group (tuition/transport: 1..3;
     *  registration: 0; the T-425 vocabulary). */
    val trancheNumbers: List<Int>,
    val amountDue: Long,        // centimes
    val amountPaid: Long,
    val amountPending: Long,
    val remaining: Long,
)

/** T-442 (UI-323) — WHAT one payment covered (an allocation row). */
data class PaymentCoverageLine(
    val installmentId: String,
    val chargeLabel: String?,
    val category: com.example.core.PaymentCategory?,
    /** The year whose debt the allocation settled (the charge's year). */
    val targetYear: String,
    val allocatedAmount: Long,  // centimes
)

/** T-442 — one prior year's still-owed-now composition entry. */
data class PriorYearOutstandingItem(
    val academicYear: String,
    val outstanding: Long,      // centimes
)

/** The settlement status of one charge (the desktop's vocabulary). */
enum class YearChargeSettlement {
    FULLY_PAID, PARTIALLY_PAID, PENDING_CLEARANCE, OUTSTANDING;

    val labelFr: String
        get() = when (this) {
            FULLY_PAID -> "Réglée"
            PARTIALLY_PAID -> "Partiellement réglée"
            PENDING_CLEARANCE -> "En attente d'encaissement"
            OUTSTANDING -> "Non réglée"
        }
}

/** One charge attributed to a year (INV-19c — the review tuple). */
data class YearChargeItem(
    val installmentId: String,
    val studentId: String?,
    val category: com.example.core.PaymentCategory,
    val label: String,
    val trancheNumber: Int,
    val amountDue: Long,        // stored column (INV-19a — the price applied)
    val amountPaid: Long,
    val amountPending: Long,
    val remaining: Long,        // INV-4: clampNonNegative(due − paid − pending)
    val dueDate: String,
    val paidDate: String?,
    val status: com.example.core.PaymentStatus,
    val attribution: AcademicYearAttribution,
    val settlement: YearChargeSettlement,
    /** The completing payment's date when derivable (allocations' last
     *  in-window cleared moment), else the stored paidDate. */
    val settledAt: String?,
)

/** The honest coverage basis of a payment's lines (INV-18d). */
enum class CoverageBasis { ALLOCATIONS, UNAVAILABLE }

/** One payment made in a year (with its coverage — T-442). */
data class YearPaymentItem(
    val paymentId: String?,
    val ledgerEntryId: String,
    val amount: Long,           // centimes (absolute)
    val at: String,
    val method: String?,
    val receiptNumber: String?,
    val attribution: AcademicYearAttribution,
    val coveredCharges: List<PaymentCoverageLine>,
    val coverageBasis: CoverageBasis,
)

/** A later-year payment settling THIS year's debt (INV-18c/18d). */
data class CrossYearSettlementItem(
    val paymentId: String?,
    val ledgerEntryId: String?,
    val paymentYear: String,    // strictly later than the target
    val at: String?,
    val installmentId: String,
    val chargeLabel: String?,
    val category: com.example.core.PaymentCategory?,
    val allocatedAmount: Long,  // centimes
    val targetYear: String,
)

/** One event in the year's balance evolution (INV-20). */
data class BalanceEvolutionEvent(
    val at: String,
    val kind: String,           // "charge" | "payment"
    val amount: Long,           // centimes
    val label: String,
    /** Running outstanding AFTER the event (NEGATIVE = credit position —
     *  ADR-010's raw-balance rule, presented, never clamped). */
    val runningOutstanding: Long,
)

enum class YearEndBasis { ALLOCATIONS, PAID_DATE_HEURISTIC, MIXED }

/** The ADR-025 pricing-config reference of a year (the UI-relevant
 *  subset of the desktop's PricingConfigSummary — the config is
 *  REFERENCED, never re-derived). Android's pricing model is not yet
 *  year-keyed; repositories pass null (the honest empty state). */
data class YearPricingConfigRef(
    val academicYearCode: String,
    val label: String,
    val isActive: Boolean,
)

/** One academic year's financial record (§17.3). */
data class AcademicYearFinancialRecord(
    val academicYear: String,           // "2025-2026"
    val academicYearId: String?,
    val startDate: String?,
    val endDate: String?,
    val isOpen: Boolean,
    val charges: List<YearChargeItem>,
    val totalCharged: Long,             // centimes
    val totalPaidOnCharges: Long,
    val totalPendingOnCharges: Long,
    val yearEndOutstanding: Long,
    val yearEndBasis: YearEndBasis,
    val carriedForwardFromPriorYear: Long,
    val serviceBreakdown: List<YearServiceGroup>,
    val outstandingStillOwedNow: Long,
    val paymentsMadeInYear: List<YearPaymentItem>,
    val paymentsMadeInYearTotal: Long,
    val settlementsReceivedFromLaterYears: List<CrossYearSettlementItem>,
    val settlementsReceivedFromLaterYearsTotal: Long,
    val pricingConfig: YearPricingConfigRef?,
    val affectedStudentIds: List<String>,
    val leftOwing: Boolean,
    val reEnrolledOwing: Boolean,
    val balanceEvolution: List<BalanceEvolutionEvent>,
)

/** The parent-level history (§17.3). */
data class ParentYearHistory(
    val parentId: String,
    val years: List<AcademicYearFinancialRecord>,   // ordered by start
    val totalOutstandingNow: Long,
    val priorYearOutstandingStillOwed: Long,
    val priorYearsStillOwed: List<PriorYearOutstandingItem>,
    val computedAt: String,
)

/** The engine input — canonical collections + the tenant year windows. */
data class YearHistoryInput(
    val parentId: String,
    val installments: List<Installment>,
    val payments: List<Payment> = emptyList(),
    /** The family's payment allocations (settlement truth — INV-18d).
     *  Optional: rows without allocations keep honest per-charge facts via
     *  the paid-date heuristic; the exact cross-year settlement detail
     *  requires them. */
    val allocations: List<PaymentAllocation> = emptyList(),
    val ledgerEntries: List<LedgerEntry>,
    val academicYears: List<AcademicYearWindow> = emptyList(),
    val pricingConfigs: Map<String, YearPricingConfigRef> = emptyMap(),
    /** The evaluation clock (determinism / as-of reports — INV-20b). */
    val now: java.time.Instant = java.time.Instant.now(),
)

/**
 * The allocation row (the desktop's `PaymentAllocation`, reduced to the
 * engine's fields). Android has no `payment_allocations` persistence yet
 * (a registered follow-up) — the engine takes them optionally, exactly as
 * the desktop does for legacy rows; the coverage basis stays honest.
 */
data class PaymentAllocation(
    val id: String,
    val paymentId: String,
    val installmentId: String?,
    val category: com.example.core.PaymentCategory?,
    val allocatedAmount: Long,   // centimes
    val label: String?,
    val createdAt: String,
)

// ============================================================================
// Internal helpers
// ============================================================================

/** INV-4 family remaining — the same clamp every surface applies. */
private fun inv4Remaining(due: Long, paid: Long, pending: Long): Long =
    (due - paid - pending).coerceAtLeast(0L)

private fun parseMillis(iso: String): Long? {
    runCatching { return java.time.Instant.parse(iso).toEpochMilli() }
    return runCatching { java.time.LocalDate.parse(iso).atStartOfDay().toInstant(java.time.ZoneOffset.UTC).toEpochMilli() }.getOrNull()
}

private fun isoString(millis: Long): String =
    java.time.Instant.ofEpochMilli(millis).toString()

/**
 * T-442 (UI-323): the service-group key of a charge — registration =
 * tuition/T0 per the T-425 official model (the FI is a FEE, not a
 * tranche); tuition = T1..T3; transport; everything else = a per-category
 * service group.
 */
private fun serviceGroupKeyOf(
    category: com.example.core.PaymentCategory,
    trancheNumber: Int,
): YearServiceGroupKey = when (category) {
    com.example.core.PaymentCategory.TUITION ->
        if (trancheNumber == 0) YearServiceGroupKey.REGISTRATION else YearServiceGroupKey.TUITION
    com.example.core.PaymentCategory.TRANSPORT -> YearServiceGroupKey.TRANSPORT
    else -> YearServiceGroupKey.SERVICE
}

/**
 * T-442 (UI-323): group a year's charge items by billable service.
 * Order: registration → tuition → transport → services (alphabetical by
 * category). Amounts are Σ stored columns / Σ INV-4 remaining (INV-20a).
 */
private fun buildServiceBreakdown(chargeItems: List<YearChargeItem>): List<YearServiceGroup> {
    val structural = LinkedHashMap<YearServiceGroupKey, MutableList<YearChargeItem>>()
    val services = HashMap<String, MutableList<YearChargeItem>>()
    for (c in chargeItems) {
        val key = serviceGroupKeyOf(c.category, c.trancheNumber)
        if (key == YearServiceGroupKey.SERVICE) {
            services.getOrPut(c.category.code) { mutableListOf() }.add(c)
        } else {
            structural.getOrPut(key) { mutableListOf() }.add(c)
        }
    }
    fun group(key: YearServiceGroupKey, category: com.example.core.PaymentCategory, items: List<YearChargeItem>): YearServiceGroup =
        YearServiceGroup(
            key = key,
            category = category,
            chargeCount = items.size,
            trancheNumbers = items.map { it.trancheNumber }.distinct().sorted(),
            amountDue = items.sumOf { it.amountDue },
            amountPaid = items.sumOf { it.amountPaid },
            amountPending = items.sumOf { it.amountPending },
            remaining = items.sumOf { it.remaining },
        )
    val groups = mutableListOf<YearServiceGroup>()
    structural[YearServiceGroupKey.REGISTRATION]?.let {
        groups.add(group(YearServiceGroupKey.REGISTRATION, com.example.core.PaymentCategory.TUITION, it))
    }
    structural[YearServiceGroupKey.TUITION]?.let {
        groups.add(group(YearServiceGroupKey.TUITION, com.example.core.PaymentCategory.TUITION, it))
    }
    structural[YearServiceGroupKey.TRANSPORT]?.let {
        groups.add(group(YearServiceGroupKey.TRANSPORT, com.example.core.PaymentCategory.TRANSPORT, it))
    }
    for (catCode in services.keys.sorted()) {
        val items = services.getValue(catCode)
        groups.add(group(YearServiceGroupKey.SERVICE, items.first().category, items))
    }
    return groups
}

private data class YearMeta(
    val start: Int,
    val startDate: String?,
    val endDate: String?,
    val id: String?,
)

private fun yearMetaOf(code: String, years: List<AcademicYearWindow>): YearMeta {
    for (y in years) {
        if (y.code == code) {
            val start = parseMillis(y.startDate)
            val end = parseMillis(y.endDate)
            return YearMeta(
                start = start?.let { millisToStartYear(it) } ?: academicYearStart(code),
                startDate = start?.let { y.startDate },
                endDate = end?.let { y.endDate },
                id = y.id,
            )
        }
    }
    return YearMeta(academicYearStart(code), null, null, null)
}

private fun millisToStartYear(millis: Long): Int =
    java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).year

/**
 * T-439 (CALC-003): the canonical payment-status classification for
 * allocation replays — paid → cleared; pending/pending_clearance →
 * committed-but-uncleared; unpaid (bounced per 0039)/refunded/cancelled →
 * NEITHER (not funds against the charge). A missing payment record keeps
 * the replay's legacy behavior (counts as cleared, clocked by the
 * allocation's own createdAt).
 */
private fun allocationFundClass(p: Payment?): String = when (p?.status) {
    null -> "paid"
    com.example.core.PaymentStatus.PAID -> "paid"
    com.example.core.PaymentStatus.PENDING, com.example.core.PaymentStatus.PENDING_CLEARANCE -> "pending"
    else -> "none"
}

/**
 * The as-of paid/pending split of ONE charge at a clock — the allocation
 * replay when rows exist (exact), the paid-date heuristic otherwise.
 */
private fun paidUpToClock(
    due: Long, paid: Long, pending: Long, paidDate: String?,
    clockMillis: Long,
    chargeAllocations: List<PaymentAllocation>,
    paymentById: Map<String, Payment>,
): Pair<Long, Long> { // paid, pending (exact when allocations exist)
    if (chargeAllocations.isNotEmpty()) {
        var paidSum = 0L
        var pendingSum = 0L
        for (a in chargeAllocations) {
            val p = paymentById[a.paymentId]
            val at = p?.let { parseMillis(it.collectedAt) } ?: parseMillis(a.createdAt)
            if (at == null || at > clockMillis) continue
            val funds = allocationFundClass(p)
            if (funds == "none") continue
            if (funds == "pending") pendingSum += a.allocatedAmount else paidSum += a.allocatedAmount
        }
        return paidSum to pendingSum
    }
    // Legacy heuristic: the stored settlement state placed at paid_date.
    val settledByThen = paidDate != null && (parseMillis(paidDate) ?: Long.MAX_VALUE) <= clockMillis
    return (if (settledByThen) paid else 0L) to 0L
}

// ============================================================================
// The engine
// ============================================================================

fun computeParentYearHistory(input: YearHistoryInput): ParentYearHistory {
    val now = input.now
    val nowMillis = now.toEpochMilli()
    val years = input.academicYears

    // ── Charge attribution (INV-18a) ──
    val parentInstallments = input.installments.filter { it.parentId == input.parentId }
    val chargeAttributions = HashMap<String, AcademicYearAttribution>()
    for (ins in parentInstallments) {
        chargeAttributions[ins.id] = attributeInstallmentAcademicYear(ins.dueDate, ins.academicCycle, years)
    }

    // ── Payment attribution (payment-made year) ──
    val parentPayments = input.payments.filter { it.parentId == input.parentId }
    val paymentById = parentPayments.associateBy { it.id }
    val paymentAttributions = HashMap<String, AcademicYearAttribution>()
    for (p in parentPayments) {
        paymentAttributions[p.id] = attributePaymentAcademicYear(p.collectedAt, years)
    }

    // ── Ledger payment replay (the §15 source — non-reversed payments) ──
    val parentEntries = input.ledgerEntries.filter { it.parentId == input.parentId }
    val reversedIds = parentEntries.mapNotNull { it.reversesId }.toHashSet()
    val paymentEntries = parentEntries
        .filter { it.type == PAYMENT && it.id !in reversedIds }
        .sortedWith(compareBy({ parseMillis(it.at) ?: 0L }, { it.id }))

    // ── Allocation indexes (settlement truth — INV-18d) ──
    val parentInstallmentIds = parentInstallments.map { it.id }.toHashSet()
    val parentAllocations = input.allocations.filter { a ->
        (a.installmentId != null && a.installmentId in parentInstallmentIds) ||
            (paymentById.containsKey(a.paymentId))
    }
    val allocationsByInstallment = HashMap<String, MutableList<PaymentAllocation>>()
    for (a in parentAllocations) {
        val iid = a.installmentId ?: continue
        allocationsByInstallment.getOrPut(iid) { mutableListOf() }.add(a)
    }
    // T-442 (UI-323): the per-payment index — WHAT each payment covered.
    val allocationsByPayment = HashMap<String, MutableList<PaymentAllocation>>()
    for (a in parentAllocations) {
        allocationsByPayment.getOrPut(a.paymentId) { mutableListOf() }.add(a)
    }

    // ── Group charges by attributed year ──
    val chargesByYear = LinkedHashMap<String, MutableList<Installment>>()
    for (ins in parentInstallments) {
        val attr = chargeAttributions.getValue(ins.id)
        chargesByYear.getOrPut(attr.code) { mutableListOf() }.add(ins)
    }

    // ── Payments made per year (ledger replay + attribution) ──
    val paymentsMadeByYear = LinkedHashMap<String, MutableList<YearPaymentItem>>()
    for (e in paymentEntries) {
        val sourcePayment = e.sourceId.let { paymentById[it] }
        val attr: AcademicYearAttribution = sourcePayment
            ?.let { paymentAttributions.getValue(it.id) }
            ?: AcademicYearAttribution(
                code = resolveAcademicYearForDate(e.at, years),
                id = null,
                source = AttributionSource.PAYMENT_DATE,
            )
        // T-442 (UI-323): the payment's COVERAGE — its allocation rows,
        // one line per settled charge, target year resolved through the
        // allocated installment's attributed year (INV-18c). CALC-003:
        // bounced/refunded/cancelled rows are NOT coverage; no rows → the
        // honest "unavailable" basis (INV-18d).
        val coveredCharges = mutableListOf<PaymentCoverageLine>()
        if (sourcePayment != null) {
            val fundClass = allocationFundClass(sourcePayment)
            for (a in allocationsByPayment[sourcePayment.id] ?: emptyList()) {
                if (fundClass == "none") break
                val iid = a.installmentId ?: continue
                val chargeAttr = chargeAttributions[iid] ?: continue
                coveredCharges.add(
                    PaymentCoverageLine(
                        installmentId = iid,
                        chargeLabel = a.label,
                        category = a.category,
                        targetYear = chargeAttr.code,
                        allocatedAmount = a.allocatedAmount,
                    ),
                )
            }
        }
        val item = YearPaymentItem(
            paymentId = sourcePayment?.id,
            ledgerEntryId = e.id,
            amount = kotlin.math.abs(e.amount),
            at = e.at,
            method = e.method?.code,
            receiptNumber = e.receiptNumber,
            attribution = attr,
            coveredCharges = coveredCharges,
            coverageBasis = if (coveredCharges.isNotEmpty()) CoverageBasis.ALLOCATIONS else CoverageBasis.UNAVAILABLE,
        )
        paymentsMadeByYear.getOrPut(attr.code) { mutableListOf() }.add(item)
    }

    // ── Cross-year settlements (INV-18d — allocations only) ──
    val settlementsReceivedByYear = HashMap<String, MutableList<CrossYearSettlementItem>>()
    for (a in parentAllocations) {
        val iid = a.installmentId ?: continue
        val chargeAttr = chargeAttributions[iid] ?: continue
        val paymentAttr = paymentAttributions[a.paymentId] ?: continue
        val targetStart = academicYearStart(chargeAttr.code)
        val paidStart = academicYearStart(paymentAttr.code)
        if (targetStart == Int.MIN_VALUE || paidStart == Int.MIN_VALUE) continue
        if (paidStart <= targetStart) continue   // same-year — not cross-year
        val sourcePayment = paymentById[a.paymentId]
        // T-439 (CALC-003): only CLEARED money settles old debt (INV-4).
        if (allocationFundClass(sourcePayment) != "paid") continue
        val sourceEntry = paymentEntries.firstOrNull { it.sourceId == a.paymentId }
        settlementsReceivedByYear.getOrPut(chargeAttr.code) { mutableListOf() }.add(
            CrossYearSettlementItem(
                paymentId = a.paymentId,
                ledgerEntryId = sourceEntry?.id,
                paymentYear = paymentAttr.code,
                at = sourcePayment?.collectedAt ?: sourceEntry?.at,
                installmentId = iid,
                chargeLabel = a.label,
                category = a.category,
                allocatedAmount = a.allocatedAmount,
                targetYear = chargeAttr.code,
            ),
        )
    }

    // ── The ordered year codes (charges + payments; unknown codes by start) ──
    val allYearCodes = (chargesByYear.keys + paymentsMadeByYear.keys).toSortedSet(
        compareBy { code -> yearMetaOf(code, years).start },
    )
    val orderedCodes = allYearCodes.toList()

    // ── Build the per-year records ──
    val records = mutableListOf<AcademicYearFinancialRecord>()
    var priorYearEndOutstanding = 0L
    for ((i, code) in orderedCodes.withIndex()) {
        val meta = yearMetaOf(code, years)
        val endMillis = meta.endDate?.let { parseMillis(it) }
        val isOpen = endMillis == null || endMillis >= nowMillis
        // INV-20b: closed years are evaluated at their END date; the open
        // year (and unknown-window years) at the caller's clock.
        val yearClockMillis = endMillis?.takeIf { !isOpen } ?: nowMillis

        val yearCharges = (chargesByYear[code] ?: emptyList())
            .sortedWith(compareBy({ parseMillis(it.dueDate) ?: 0L }, { it.id }))

        // The year-end as-of split per charge (exact when allocations exist).
        var anyExact = false
        var anyHeuristic = false
        var yearEndOutstanding = 0L
        val chargeItems = yearCharges.map { ins ->
            val attr = chargeAttributions.getValue(ins.id)
            val chargeAllocations = allocationsByInstallment[ins.id] ?: emptyList()
            val (asOfPaid, asOfPending) = paidUpToClock(
                ins.amountDue, ins.amountPaid, ins.amountPending, ins.paidDate,
                yearClockMillis, chargeAllocations, paymentById,
            )
            if (chargeAllocations.isNotEmpty()) anyExact = true else anyHeuristic = true
            yearEndOutstanding += (ins.amountDue - asOfPaid - asOfPending).coerceAtLeast(0L)

            // Current settlement (the stored truth at the caller's clock).
            // INV-4: uncleared funds are NOT a settlement — a pending-only
            // charge stays outstanding until clearance.
            val remaining = inv4Remaining(ins.amountDue, ins.amountPaid, ins.amountPending)
            var settlement: YearChargeSettlement
            var settledAt: String? = ins.paidDate
            if (remaining <= YEAR_HISTORY_EPSILON_CENTIMES && (ins.amountPaid > 0 || ins.amountDue <= YEAR_HISTORY_EPSILON_CENTIMES)) {
                settlement = YearChargeSettlement.FULLY_PAID
                // The exact completing moment when allocation records exist:
                // the LAST in-window cleared allocation's payment date.
                if (chargeAllocations.isNotEmpty()) {
                    val times = chargeAllocations
                        .mapNotNull { a ->
                            val p = paymentById[a.paymentId]
                            if (allocationFundClass(p) == "none") return@mapNotNull null
                            p?.let { parseMillis(it.collectedAt) } ?: parseMillis(a.createdAt)
                        }
                        .sorted()
                    if (times.isNotEmpty()) settledAt = isoString(times.last())
                }
            } else if (ins.amountPaid > 0) {
                settlement = YearChargeSettlement.PARTIALLY_PAID
            } else if (ins.amountPending > 0) {
                settlement = YearChargeSettlement.PENDING_CLEARANCE
            } else {
                settlement = YearChargeSettlement.OUTSTANDING
            }

            YearChargeItem(
                installmentId = ins.id,
                studentId = ins.studentId,
                category = ins.category,
                label = ins.label,
                trancheNumber = ins.trancheNumber,
                amountDue = ins.amountDue,
                amountPaid = ins.amountPaid,
                amountPending = ins.amountPending,
                remaining = remaining,
                dueDate = ins.dueDate,
                paidDate = ins.paidDate,
                status = ins.status,
                attribution = attr,
                settlement = settlement,
                settledAt = if (settlement == YearChargeSettlement.FULLY_PAID) settledAt else null,
            )
        }

        val chargedInYear = yearCharges.sumOf { it.amountDue }

        val paymentsMade = (paymentsMadeByYear[code] ?: emptyList())
            .sortedWith(compareBy({ parseMillis(it.at) ?: 0L }, { it.ledgerEntryId }))

        val settlementsReceived = (settlementsReceivedByYear[code] ?: emptyList())
            .sortedWith(compareBy({ parseMillis(it.at ?: "") ?: 0L }, { it.installmentId }))

        // Balance evolution (INV-20): the year's charges + payments made in
        // the year, chronological, running outstanding starting from the
        // carried-forward balance. NOT clamped (ADR-010).
        val rawEvents = mutableListOf<BalanceEvolutionEvent>()
        for (c in chargeItems) {
            rawEvents.add(BalanceEvolutionEvent(c.dueDate, "charge", c.amountDue, c.label, 0L))
        }
        for (p in paymentsMade) {
            rawEvents.add(BalanceEvolutionEvent(p.at, "payment", p.amount, p.receiptNumber ?: "Paiement", 0L))
        }
        rawEvents.sortWith(compareBy({ parseMillis(it.at) ?: 0L }, { if (it.kind == "charge") 0 else 1 }))
        val events = mutableListOf<BalanceEvolutionEvent>()
        var running = priorYearEndOutstanding
        for (ev in rawEvents) {
            running = if (ev.kind == "charge") running + ev.amount else running - ev.amount
            events.add(ev.copy(runningOutstanding = running))
        }

        val affectedStudentIds = chargeItems.mapNotNull { it.studentId }.distinct()

        // The derived flags (INV-20): does a FOLLOWING year exist with
        // charges? "left owing" requires a CLOSED year; an open final year
        // with debt carries neither flag — the honest presentation.
        val nextCode = orderedCodes.getOrNull(i + 1)
        val nextYearHasCharges = nextCode != null && (chargesByYear[nextCode]?.size ?: 0) > 0
        val stillOwedAtEnd = yearEndOutstanding > YEAR_HISTORY_EPSILON_CENTIMES

        val yearEndBasis = when {
            anyExact && anyHeuristic -> YearEndBasis.MIXED
            anyExact -> YearEndBasis.ALLOCATIONS
            else -> YearEndBasis.PAID_DATE_HEURISTIC
        }

        // T-442 (UI-323): the per-year service grouping (INV-20e) and the
        // per-year still-owed-today figure — Σ current INV-4 remaining.
        val serviceBreakdown = buildServiceBreakdown(chargeItems)
        val outstandingStillOwedNow = chargeItems.sumOf { it.remaining }

        records.add(
            AcademicYearFinancialRecord(
                academicYear = code,
                academicYearId = meta.id ?: yearCharges.firstOrNull()?.let { chargeAttributions[it.id]?.id },
                startDate = meta.startDate,
                endDate = meta.endDate,
                isOpen = isOpen,
                charges = chargeItems,
                totalCharged = chargedInYear,
                totalPaidOnCharges = yearCharges.sumOf { it.amountPaid },
                totalPendingOnCharges = yearCharges.sumOf { it.amountPending },
                yearEndOutstanding = yearEndOutstanding,
                yearEndBasis = yearEndBasis,
                carriedForwardFromPriorYear = priorYearEndOutstanding,
                serviceBreakdown = serviceBreakdown,
                outstandingStillOwedNow = outstandingStillOwedNow,
                paymentsMadeInYear = paymentsMade,
                paymentsMadeInYearTotal = paymentsMade.sumOf { it.amount },
                settlementsReceivedFromLaterYears = settlementsReceived,
                settlementsReceivedFromLaterYearsTotal = settlementsReceived.sumOf { it.allocatedAmount },
                pricingConfig = input.pricingConfigs[code],
                affectedStudentIds = affectedStudentIds,
                leftOwing = stillOwedAtEnd && !nextYearHasCharges && !isOpen,
                reEnrolledOwing = stillOwedAtEnd && nextYearHasCharges,
                balanceEvolution = events,
            ),
        )

        // Carry forward: this year's unpaid balance at ITS year end becomes
        // the next year's carried-in debt.
        priorYearEndOutstanding = if (stillOwedAtEnd) yearEndOutstanding else 0L
    }

    val totalOutstandingNow = parentInstallments.sumOf { inv4Remaining(it.amountDue, it.amountPaid, it.amountPending) }
    // Prior-year outstanding still owed TODAY: every year EXCEPT the latest
    // one that has charges (the current year is not "prior").
    var lastYearWithChargesIdx = -1
    for (i in records.indices.reversed()) {
        if (records[i].charges.isNotEmpty()) {
            lastYearWithChargesIdx = i
            break
        }
    }
    // T-442 (UI-323): the per-year enumeration of that prior-years debt —
    // Σ entries === the aggregate (the same clamped sums, INV-20a).
    val priorYearsStillOwed = records
        .filterIndexed { idx, _ -> idx < lastYearWithChargesIdx }
        .map { PriorYearOutstandingItem(it.academicYear, it.outstandingStillOwedNow) }
        .filter { it.outstanding > YEAR_HISTORY_EPSILON_CENTIMES }
    val priorYearOutstandingStillOwed = records
        .filterIndexed { idx, _ -> idx < lastYearWithChargesIdx }
        .sumOf { r -> r.charges.sumOf { it.remaining } }

    return ParentYearHistory(
        parentId = input.parentId,
        years = records,
        totalOutstandingNow = totalOutstandingNow,
        priorYearOutstandingStillOwed = priorYearOutstandingStillOwed,
        priorYearsStillOwed = priorYearsStillOwed,
        computedAt = now.toString(),
    )
}
