package com.example.core

/**
 * Billing Breakdown — Kotlin mirror of the desktop canonical engine
 * `src/domain/calc/payment/billing-breakdown.ts` (T-164 / T-167).
 *
 * Derives (PURE — no IO, mirrors the desktop semantics in centimes):
 *   1. The itemized "Prestations facturées" charge list per child and the
 *      consolidated per-service totals.
 *   2. The tranche coverage schedule — where the family's cleared payments
 *      landed. REAL installment rows are authoritative (their amountPaid /
 *      amountPending come from the server-side waterfall,
 *      `collect_and_allocate_payment` / the local canonical allocator).
 *      Only when a child has charges but NO physical tranche rows does this
 *      engine synthesize the official 40/30/30 schedule
 *      (`splitNetTuitionByOfficialSchedule`) and run the canonical
 *      `allocatePaymentToInstallments` for DISPLAY — flagged
 *      `isSynthetic = true` so no surface mistakes it for stored data.
 *   3. Adjustment diagnostics (`describeAdjustment`) — credit/debit badge +
 *      reason fallback, same wording as the desktop drawer and the website
 *      portal so every platform labels the same row identically.
 *
 * INVARIANTS (docs/domain/financial-rules.md):
 *   - INV-4: remaining = (amountDue − amountPaid − amountPending) ≥ 0.
 *   - Conservation: Σ synthetic tranche amounts === childBilledTotal.
 *   - Real installments are never re-allocated client-side.
 *   - Cleared payments already reflected on real rows are reserved: only
 *     the residual feeds the synthetic display waterfall (no double count).
 *
 * Cross-platform parity contract:
 *   - Desktop: domain/calc/payment/billing-breakdown.ts
 *   - Website: src/lib/canonical/billing-breakdown.ts
 *   All three are verified against the same vectors
 *   (285 000 DZD / 125 000 DZD headline scenario).
 */

/** Minimal child descriptor (mapped from domain Student by the caller). */
data class BillingChildInfo(
    val id: String,
    val displayName: String,
    val gradeLevelLabel: String,
)

/** One billed line item (a charge ledger entry, human-labelled). */
data class BillingLineItem(
    val id: String,
    val label: String,
    val category: PaymentCategory,
    val amount: Long,
)

/** Display status of a tranche coverage node. */
enum class TrancheDisplayStatus { PAID, PARTIAL, PENDING, UNPAID }

/** A tranche coverage node — real row or synthesized display schedule. */
data class TrancheCoverageNode(
    val key: String,
    /** Real installment id, or "syn-{childId}-{n}" for synthesized tranches. */
    val isSynthetic: Boolean,
    val label: String,
    val dueDate: String?,
    val amountDue: Long,
    val amountPaid: Long,
    val amountPending: Long,
    /** INV-4 remaining. */
    val remaining: Long,
    val coveragePct: Int,
    val status: TrancheDisplayStatus,
)

/** Per-child billing breakdown. */
data class ChildBillingBreakdown(
    val child: BillingChildInfo,
    /** Σ charges attributed to this child (single-child → family). */
    val billedTotal: Long,
    val lineItems: List<BillingLineItem>,
    val tranches: List<TrancheCoverageNode>,
    val tranchesRemaining: Long,
    /** True when the child had charges but no physical tranche rows. */
    val isSyntheticSchedule: Boolean,
)

/** Consolidated per-service total. */
data class ServiceTotalNode(
    val category: PaymentCategory,
    val label: String,
    val amount: Long,
    val count: Int,
)

/** Full parent billing breakdown view model. */
data class ParentBillingBreakdown(
    val academicYear: String,
    val totalBilled: Long,
    /** Cleared payments only (status == paid), matching sumPaidPayments. */
    val totalClearedPaid: Long,
    val hasSyntheticTranches: Boolean,
    val byChild: List<ChildBillingBreakdown>,
    val byService: List<ServiceTotalNode>,
)

/** Adjustment diagnostic shared by every platform's adjustments view. */
data class AdjustmentDiagnostic(
    val kind: String, // "credit" | "debit"
    val badgeLabel: String,
    val reasonLabel: String,
    val isDiagnosticFallback: Boolean,
)

/** FR service labels — canonical wording shared across platforms. */
val SERVICE_LABELS_FR: Map<PaymentCategory, String> = mapOf(
    PaymentCategory.TUITION to "Scolarité",
    PaymentCategory.TRANSPORT to "Transport",
    PaymentCategory.CANTEEN to "Cantine",
    PaymentCategory.UNIFORM to "Tenue / Uniforme",
    PaymentCategory.BOOKS to "Fournitures & Livres",
    PaymentCategory.EXTRACURRICULAR to "Activités parascolaires",
    PaymentCategory.THERAPY_PSYCHOLOGY to "Accompagnement psychologique",
    PaymentCategory.THERAPY_SPEECH to "Orthophonie",
    PaymentCategory.SECOND_APRON to "Deuxième tablier",
    PaymentCategory.PARENT_CREDIT to "Crédit parent",
    PaymentCategory.OTHER to "Autres prestations",
)

private const val DEFAULT_ACADEMIC_YEAR = "2025-2026"
private val ACADEMIC_YEAR_PATTERN = Regex("""20\d{2}[-/]20\d{2}""")

/**
 * Resolve the academic year a family's billing belongs to: charge metadata →
 * charge description → [fallback].
 */
fun resolveBillingAcademicYear(
    chargeEntries: List<LedgerEntry>,
    fallback: String? = null,
): String {
    for (c in chargeEntries) {
        (c.metadata["academicYear"] as? String)?.let { return it }
        ACADEMIC_YEAR_PATTERN.find(c.description)?.let { return it.value }
    }
    return fallback ?: DEFAULT_ACADEMIC_YEAR
}

/** Map an installment-shaped row to a coverage node (INV-4 remaining). */
private fun toTrancheNode(
    id: String,
    label: String,
    dueDate: String?,
    amountDue: Long,
    amountPaid: Long,
    amountPending: Long,
    status: String,
    isSynthetic: Boolean,
): TrancheCoverageNode {
    val remaining = (amountDue - amountPaid - amountPending).coerceAtLeast(0L)
    val coverage = if (amountDue > 0L) {
        (((amountPaid + amountPending) * 100L / amountDue).toInt()).coerceAtMost(100)
    } else 0
    val display = when {
        amountDue > 0L && amountPaid >= amountDue -> TrancheDisplayStatus.PAID
        amountPaid > 0L -> TrancheDisplayStatus.PARTIAL
        amountPending > 0L -> TrancheDisplayStatus.PENDING
        else -> TrancheDisplayStatus.UNPAID
    }
    return TrancheCoverageNode(
        key = id,
        isSynthetic = isSynthetic,
        label = label,
        dueDate = dueDate,
        amountDue = amountDue,
        amountPaid = amountPaid,
        amountPending = amountPending,
        remaining = remaining,
        coveragePct = coverage,
        status = display,
    )
}

/** A real installment row projected to the minimal engine shape. */
data class BillingInstallmentRow(
    val id: String,
    val studentId: String?,
    val category: PaymentCategory,
    val label: String,
    val amountDue: Long,
    val amountPaid: Long,
    val amountPending: Long,
    val dueDate: String,
    val status: String,
)

/**
 * Compute the full parent billing breakdown view model.
 *
 * @param ledgerEntries the family's ledger entries (charge rows are filtered
 *   internally).
 * @param installments the family's REAL installment rows.
 * @param clearedPaidTotal Σ cleared payments (status == paid), family-level.
 * @param children the family's children (minimal descriptors).
 * @param fallbackTotalDue profile-level total due, used only when the ledger
 *   has no charge rows.
 */
fun parentBillingBreakdown(
    ledgerEntries: List<LedgerEntry>,
    installments: List<BillingInstallmentRow>,
    clearedPaidTotal: Long,
    children: List<BillingChildInfo>,
    fallbackTotalDue: Long = 0L,
    academicYearOverride: String? = null,
): ParentBillingBreakdown {
    val chargeEntries = ledgerEntries.filter { it.type == LedgerEntryType.CHARGE }
    val academicYear = academicYearOverride ?: resolveBillingAcademicYear(chargeEntries)

    val rawChargeTotal = chargeEntries.sumOf { it.amount }
    val totalBilled = if (rawChargeTotal > 0L) rawChargeTotal else fallbackTotalDue

    // Reserve money already reflected on real rows — only the residual may
    // feed the synthetic display waterfall (prevents double counting).
    val realPaidOnInstallments = installments.sumOf { it.amountPaid }
    val syntheticPool = (clearedPaidTotal - realPaidOnInstallments).coerceAtLeast(0L)

    // Synthetic tranche batches, collected per child during pass 1.
    data class SynthBatch(val childId: String, val tranches: List<WaterfallInstallment>)

    val synthBatches = mutableListOf<SynthBatch>()

    // -------- Pass 1: per-child derivation --------
    val pass1 = children.map { child ->
        var childCharges = chargeEntries.filter { it.studentId == child.id }
        if (childCharges.isEmpty() && children.size == 1 && chargeEntries.isNotEmpty()) {
            childCharges = chargeEntries
        }
        val billedTotal = when {
            childCharges.isNotEmpty() -> childCharges.sumOf { it.amount }
            children.size == 1 -> totalBilled
            else -> Math.round(totalBilled.toDouble() / children.size.coerceAtLeast(1))
        }
        val lineItems = childCharges.map { c ->
            BillingLineItem(
                id = c.id,
                label = c.description.ifBlank { SERVICE_LABELS_FR[c.category] ?: "Scolarité" },
                category = c.category,
                amount = c.amount,
            )
        }

        // Real tranches for this child (direct; family-level rows only for a
        // single-child family — legacy/mock compat, mirrors the desktop rule).
        var real = installments.filter { it.studentId == child.id }
        if (real.isEmpty() && children.size == 1) {
            val familyLevel = installments.filter { it.studentId == null }
            if (familyLevel.isNotEmpty()) real = familyLevel
        }

        if (real.isNotEmpty() || billedTotal <= 0L) {
            val tranches = real
                .sortedBy { it.dueDate }
                .map {
                    toTrancheNode(
                        it.id, it.label, it.dueDate,
                        it.amountDue, it.amountPaid, it.amountPending, it.status,
                        isSynthetic = false,
                    )
                }
            ChildBillingBreakdown(
                child = child,
                billedTotal = billedTotal,
                lineItems = lineItems,
                tranches = tranches,
                tranchesRemaining = tranches.sumOf { it.remaining },
                isSyntheticSchedule = false,
            )
        } else {
            // Synthesis path — display-only 40/30/30 schedule.
            val (t1, t2, t3) = splitNetTuitionByOfficialSchedule(billedTotal)
            val (d1, d2, d3) = officialTuitionDueDates(startYearOf(academicYear))
            val amounts = listOf(t1, t2, t3)
            val dates = listOf(d1, d2, d3)
            val batch = amounts.mapIndexed { i, amount ->
                WaterfallInstallment(
                    id = "syn-${child.id}-${i + 1}",
                    category = PaymentCategory.TUITION,
                    amountDue = amount,
                    amountPaid = 0L,
                    amountPending = 0L,
                    dueDate = dates[i],
                    status = "unpaid",
                )
            }
            synthBatches += SynthBatch(child.id, batch)
            ChildBillingBreakdown(
                child = child,
                billedTotal = billedTotal,
                lineItems = lineItems,
                tranches = emptyList(), // filled by pass 2
                tranchesRemaining = 0L,
                isSyntheticSchedule = true,
            )
        }
    }

    // -------- Pass 2: display waterfall over ALL synthetic tranches at once --------
    val byChild = if (synthBatches.isNotEmpty()) {
        val allSynthetic = synthBatches.flatMap { it.tranches }
        val allocation = allocatePaymentToInstallments(allSynthetic, syntheticPool)
        val paidById = allocation.allocations.associate { it.installmentId to it.newAmountPaid }
        pass1.map { child ->
            if (!child.isSyntheticSchedule) {
                child
            } else {
                val batch = synthBatches.firstOrNull { it.childId == child.child.id }?.tranches ?: emptyList()
                val tranches = batch.map { ins ->
                    toTrancheNode(
                        ins.id, "Tranche ${ins.id.substringAfterLast('-')}", ins.dueDate,
                        ins.amountDue, paidById[ins.id] ?: 0L, ins.amountPending, ins.status,
                        isSynthetic = true,
                    )
                }
                child.copy(tranches = tranches, tranchesRemaining = tranches.sumOf { it.remaining })
            }
        }
    } else {
        pass1
    }

    // -------- Per-service consolidation --------
    val byService = chargeEntries
        .groupBy { it.category }
        .map { (category, rows) ->
            ServiceTotalNode(
                category = category,
                label = SERVICE_LABELS_FR[category] ?: "Scolarité",
                amount = rows.sumOf { it.amount },
                count = rows.size,
            )
        }
        .sortedByDescending { it.amount }
        .ifEmpty {
            if (totalBilled > 0L) {
                listOf(
                    ServiceTotalNode(
                        category = PaymentCategory.TUITION,
                        label = "Scolarité Annuelle",
                        amount = totalBilled,
                        count = children.size,
                    )
                )
            } else emptyList()
        }

    return ParentBillingBreakdown(
        academicYear = academicYear,
        totalBilled = totalBilled,
        totalClearedPaid = clearedPaidTotal,
        hasSyntheticTranches = byChild.any { it.isSyntheticSchedule },
        byChild = byChild,
        byService = byService,
    )
}

/** Parse the start calendar year out of a "YYYY-YYYY" code. */
private fun startYearOf(academicYear: String): Int =
    academicYear.take(4).toIntOrNull() ?: java.time.Year.now().value

/**
 * Badge + reason diagnostics for a ledger adjustment entry.
 *
 * Ledger convention: negative = credit/remise, positive = debit/majoration.
 * Wording is identical to the desktop `describeAdjustment` and the website
 * port so the same row renders the same way on every platform.
 */
fun describeAdjustment(amount: Long, reason: String?): AdjustmentDiagnostic {
    val isCredit = amount < 0L
    val stored = reason?.trim().orEmpty()
    val hasReason = stored.isNotEmpty()
    return AdjustmentDiagnostic(
        kind = if (isCredit) "credit" else "debit",
        badgeLabel = if (isCredit) "Crédit / Déduction" else "Débit / Majoration",
        reasonLabel = when {
            hasReason -> stored
            isCredit -> "Déduction / remise enregistrée automatiquement par le système (motif non documenté)"
            else -> "Régularisation / rétablissement de dette (contrepassation automatique, motif non documenté)"
        },
        isDiagnosticFallback = !hasReason,
    )
}
