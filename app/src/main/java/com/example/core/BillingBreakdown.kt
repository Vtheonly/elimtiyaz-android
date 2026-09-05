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

/** T-168 — per-child attribution inside one service ("shopping list"). */
data class ServiceChildAttribution(
    val studentId: String?,
    val studentName: String,
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
    /** T-168: share of totalBilled, 0–100 rounded (display-only). */
    val sharePct: Int,
    /** T-168: per-child attribution inside this service. */
    val childAttribution: List<ServiceChildAttribution>,
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
    /** T-168: family-level charges with no child attribution (multi-child). */
    val unattributedItems: List<BillingLineItem>,
    val unattributedTotal: Long,
    /** T-168: adjustment-aware reconciliation (every balance explained). */
    val reconciliation: BillingReconciliation,
)

/**
 * T-168 — adjustment-aware reconciliation (identical equation on every
 * platform):
 *
 *   grossBilled − adjustmentsCredit + adjustmentsDebit = netDue
 *   netDue − clearedPaid − pendingPaid               = derivedRemaining
 *   derivedRemaining + bridge                        = serverOutstanding
 */
data class BillingReconciliation(
    val grossBilled: Long,
    val adjustmentsCredit: Long,
    val adjustmentsDebit: Long,
    val adjustmentsCount: Int,
    val netDue: Long,
    val clearedPaid: Long,
    val pendingPaid: Long,
    val derivedRemaining: Long,
    val serverOutstanding: Long?,
    val bridge: Long,
    val hasBridge: Boolean,
)

/** Adjustment diagnostic shared by every platform's adjustments view. */
data class AdjustmentDiagnostic(
    val kind: String, // "credit" | "debit"
    val badgeLabel: String,
    val reasonLabel: String,
    val isDiagnosticFallback: Boolean,
)

/* ============================================================ */
/*  T-168 — adjustment provenance classification                  */
/* ============================================================ */

/**
 * What an adjustment entry actually IS — same classes as the desktop and
 * website engines ("actual content / trap / mistake"):
 *   DOCUMENTED    → actual content (operator decision, motive kept)
 *   REVERSAL_PAIR → net-zero +X/−X pair (re-import / error correction)
 *   UNDOCUMENTED  → legacy blank row (audit required)
 */
enum class AdjustmentProvenance {
    DOCUMENTED, REVERSAL_PAIR, UNDOCUMENTED;
}

/** Canonical FR provenance badge labels (same wording as the TS engines). */
val ADJUSTMENT_PROVENANCE_LABELS_FR: Map<AdjustmentProvenance, String> = mapOf(
    AdjustmentProvenance.DOCUMENTED to "Documenté",
    AdjustmentProvenance.REVERSAL_PAIR to "Contrepassation",
    AdjustmentProvenance.UNDOCUMENTED to "Non documenté",
)

/** Minimal adjustment projection fed by the caller (repository row). */
data class BillingAdjustment(
    val id: String,
    /** Signed centimes: negative = credit/remise, positive = debit. */
    val amount: Long,
    val reason: String?,
    val at: String,
    val approvedBy: String?,
    val receiptRef: String? = null,
)

/** One classified adjustment row (view model for the history list). */
data class ClassifiedAdjustment(
    val id: String,
    val amount: Long,
    val at: String,
    val approvedBy: String,
    val kind: String, // "credit" | "debit"
    val badgeLabel: String,
    val reasonLabel: String,
    val isDiagnosticFallback: Boolean,
    val provenance: AdjustmentProvenance,
    val provenanceLabel: String,
    val meaningLabel: String,
    val pairedWithId: String?,
    val receiptRef: String?,
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
 * @param academicYearOverride pre-resolved academic year (skips the heuristic).
 * @param adjustments T-168: the family's account adjustments feeding the
 *   reconciliation (signed centimes).
 * @param pendingPaidTotal T-168: Σ uncleared cheques / transfers.
 * @param serverOutstanding T-168: server-replayed balance for the bridge line.
 */
fun parentBillingBreakdown(
    ledgerEntries: List<LedgerEntry>,
    installments: List<BillingInstallmentRow>,
    clearedPaidTotal: Long,
    children: List<BillingChildInfo>,
    fallbackTotalDue: Long = 0L,
    academicYearOverride: String? = null,
    adjustments: List<BillingAdjustment> = emptyList(),
    pendingPaidTotal: Long = 0L,
    serverOutstanding: Long? = null,
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
        if (children.size == 1) {
            // T-168: a single-child family OWNS the family-level (null
            // studentId) rows as well — mirrors the desktop fix so the
            // itemized breakdown stays exhaustive (no mystery money).
            childCharges = childCharges + chargeEntries.filter { it.studentId == null }
            if (childCharges.isEmpty() && chargeEntries.isNotEmpty()) {
                childCharges = chargeEntries // legacy unknown attribution
            }
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

    // -------- T-168: family-level charges with no child attribution --------
    val unattributedItems =
        if (children.size > 1) {
            chargeEntries
                .filter { it.studentId == null || children.none { c -> c.id == it.studentId } }
                .map { c ->
                    BillingLineItem(
                        id = c.id,
                        label = c.description.ifBlank { SERVICE_LABELS_FR[c.category] ?: "Scolarité" },
                        category = c.category,
                        amount = c.amount,
                    )
                }
        } else {
            emptyList()
        }
    val unattributedTotal = unattributedItems.sumOf { it.amount }

    // -------- Per-service consolidation (T-168: share % + attribution) --------
    val displayNameOf = { studentId: String? ->
        if (studentId == null) {
            "Famille"
        } else {
            children.firstOrNull { it.id == studentId }?.displayName ?: "Famille"
        }
    }
    val byService = chargeEntries
        .groupBy { it.category }
        .map { (category, rows) ->
            val amount = rows.sumOf { it.amount }
            val attribution = rows
                .groupBy { it.studentId ?: "__family__" }
        .map { (key, groupRows) ->
                    ServiceChildAttribution(
                        studentId = if (key == "__family__") null else key,
                        studentName = displayNameOf(if (key == "__family__") null else key),
                        amount = groupRows.sumOf { it.amount },
                    )
                }
                .sortedByDescending { it.amount }
            ServiceTotalNode(
                category = category,
                label = SERVICE_LABELS_FR[category] ?: "Scolarité",
                amount = amount,
                count = rows.size,
                // Round like the TS engines (Math.round) so 90 000/700 000
                // = 12.857 → 13 on every platform (integer division → 12).
                sharePct = if (totalBilled > 0L) {
                    Math.round(amount.toDouble() * 100.0 / totalBilled.toDouble())
                        .toInt()
                        .coerceIn(0, 100)
                } else 0,
                childAttribution = attribution,
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
                        sharePct = 100,
                        childAttribution = listOf(
                            ServiceChildAttribution(null, "Famille", totalBilled),
                        ),
                    )
                )
            } else emptyList()
        }

    // -------- T-168: adjustment-aware reconciliation --------
    var adjustmentsCredit = 0L
    var adjustmentsDebit = 0L
    for (a in adjustments) {
        if (a.amount < 0L) adjustmentsCredit += -a.amount
        else adjustmentsDebit += a.amount
    }
    val netDue = totalBilled + adjustmentsDebit - adjustmentsCredit
    val derivedRemaining = netDue - clearedPaidTotal - pendingPaidTotal
    val bridge = serverOutstanding?.let { it - derivedRemaining } ?: 0L
    val reconciliation = BillingReconciliation(
        grossBilled = totalBilled,
        adjustmentsCredit = adjustmentsCredit,
        adjustmentsDebit = adjustmentsDebit,
        adjustmentsCount = adjustments.size,
        netDue = netDue,
        clearedPaid = clearedPaidTotal,
        pendingPaid = pendingPaidTotal,
        derivedRemaining = derivedRemaining,
        serverOutstanding = serverOutstanding,
        bridge = bridge,
        hasBridge = kotlin.math.abs(bridge) > 1L,
    )

    return ParentBillingBreakdown(
        academicYear = academicYear,
        totalBilled = totalBilled,
        totalClearedPaid = clearedPaidTotal,
        hasSyntheticTranches = byChild.any { it.isSyntheticSchedule },
        byChild = byChild,
        byService = byService,
        unattributedItems = unattributedItems,
        unattributedTotal = unattributedTotal,
        reconciliation = reconciliation,
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

/* ============================================================ */
/*  T-168 — adjustment provenance classification (Kotlin mirror)  */
/* ============================================================ */

/** Meaning sentence for a provenance class + direction (identical FR
 *  wording to the TS `meaningLabelOf` helpers). */
private fun meaningLabelOf(provenance: AdjustmentProvenance, isCredit: Boolean): String =
    when (provenance) {
        AdjustmentProvenance.REVERSAL_PAIR ->
            "Écriture annulée par une écriture inverse du même montant (probable ré-import ou correction d'erreur). Effet net sur le solde : nul."
        AdjustmentProvenance.UNDOCUMENTED ->
            if (isCredit) {
                "Entrée héritée sans motif (import système antérieur à la contrainte 0069) : déduction au motif inconnu — à auditer."
            } else {
                "Entrée héritée sans motif (import système antérieur à la contrainte 0069) : rétablissement de dette au motif inconnu — à auditer."
            }
        AdjustmentProvenance.DOCUMENTED ->
            if (isCredit) {
                "Contenu réel : remise ou déduction appliquée par un opérateur, motif documenté — réduit le solde dû."
            } else {
                "Contenu réel : majoration ou annulation de remise appliquée par un opérateur, motif documenté — augmente le solde dû."
            }
    }

/**
 * Classify the family's adjustment history (T-168).
 *
 * Reversal-pair detection — IDENTICAL algorithm on every platform
 * (desktop TS / website TS / Android Kotlin): chronological order
 * (at, then id), a pool per |amount| of unmatched entries, FIFO pairing
 * ONLY across opposite signs (two same-sign entries never pair), zero
 * amounts skip pairing. Paired → REVERSAL_PAIR; blank reason →
 * UNDOCUMENTED; everything else → DOCUMENTED. Pure function; the caller's
 * order is preserved in the returned list.
 */
fun classifyAdjustmentHistory(
    adjustments: List<BillingAdjustment>,
): List<ClassifiedAdjustment> {
    val chronological = adjustments
        .filter { it.amount != 0L }
        .sortedWith(compareBy({ it.at }, { it.id }))

    // |amount| → FIFO queue of unmatched entries (opposite-sign pairing only).
    data class PoolEntry(val id: String, val isCredit: Boolean)

    val pool = mutableMapOf<Long, MutableList<PoolEntry>>()
    val pairedWith = mutableMapOf<String, String>()
    for (entry in chronological) {
        val magnitude = kotlin.math.abs(entry.amount)
        val isCredit = entry.amount < 0L
        val queue = pool.getOrPut(magnitude) { mutableListOf() }
        val siblingIndex = queue.indexOfFirst { it.isCredit != isCredit }
        if (siblingIndex >= 0) {
            val siblingId = queue.removeAt(siblingIndex).id
            pairedWith[entry.id] = siblingId
            pairedWith[siblingId] = entry.id
        } else {
            queue.add(PoolEntry(entry.id, isCredit))
        }
    }

    return adjustments.map { a ->
        val diagnostic = describeAdjustment(a.amount, a.reason)
        val hasReason = !a.reason.isNullOrBlank()
        val pairedWithId = pairedWith[a.id]
        val provenance = when {
            pairedWithId != null -> AdjustmentProvenance.REVERSAL_PAIR
            hasReason -> AdjustmentProvenance.DOCUMENTED
            else -> AdjustmentProvenance.UNDOCUMENTED
        }
        ClassifiedAdjustment(
            id = a.id,
            amount = a.amount,
            at = a.at,
            approvedBy = a.approvedBy ?: "system",
            kind = diagnostic.kind,
            badgeLabel = diagnostic.badgeLabel,
            reasonLabel = diagnostic.reasonLabel,
            isDiagnosticFallback = diagnostic.isDiagnosticFallback,
            provenance = provenance,
            provenanceLabel = ADJUSTMENT_PROVENANCE_LABELS_FR[provenance] ?: "—",
            meaningLabel = meaningLabelOf(provenance, diagnostic.kind == "credit"),
            pairedWithId = pairedWithId,
            receiptRef = a.receiptRef,
        )
    }
}
