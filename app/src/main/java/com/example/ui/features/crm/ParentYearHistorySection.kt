package com.example.ui.features.crm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.AcademicYearFinancialRecord
import com.example.core.BalanceEvolutionEvent
import com.example.core.CoverageBasis
import com.example.core.ParentYearHistory
import com.example.core.PaymentCategory
import com.example.core.YearChargeSettlement
import com.example.core.YearServiceGroupKey
import com.example.core.computeParentYearHistory
import com.example.core.formatDzd
import com.example.domain.model.Installment
import com.example.domain.model.Payment
import com.example.core.LedgerEntry
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.designsystem.components.display.ElTag

/**
 * T-456 (128th session) — « Historique par Année Scolaire » — the Android
 * mirror of the desktop's `parent-year-history-section.tsx` (T-436/T-442).
 *
 * ONE rendering component, TWO surfaces (financial-rules §17.3 INV-20e):
 * the CRM parent screen (Finances region) and the Debt Dashboard's
 * « Par année » drawer — both mount THIS composable; neither computes
 * anything (the §15.53a rule: the ONE canonical engine
 * [computeParentYearHistory] derives; consumers render).
 *
 * Presentational vocabulary mirrors the desktop verbatim: the prior-years
 * banner (rose) with the per-year chips, the per-year collapsible cards
 * (« en cours » / « clôturée », « réinscrit avec dette », « parti avec
 * dette »), the « Services de l'année » block (the INV-20e service
 * breakdown), the charges with their settlement chips, the payments with
 * their coverage lines (the honest « couverture non enregistrée » when
 * the allocation rows are absent), and the cross-year settlements block.
 */
@Composable
fun ParentYearHistorySection(
    parentId: String,
    installments: List<Installment>,
    payments: List<Payment>,
    ledgerEntries: List<LedgerEntry>,
    modifier: Modifier = Modifier,
) {
    val history: ParentYearHistory = remember(parentId, installments, payments, ledgerEntries) {
        computeParentYearHistory(
            com.example.core.YearHistoryInput(
                parentId = parentId,
                installments = installments,
                payments = payments,
                ledgerEntries = ledgerEntries,
            ),
        )
    }
    if (history.years.isEmpty()) return // the honest empty state
    ParentYearHistoryBody(history, modifier)
}

@Composable
fun ParentYearHistoryBody(history: ParentYearHistory, modifier: Modifier = Modifier) {
    val c = ElTheme.colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = c.primary, modifier = Modifier.width(18.dp).height(18.dp))
            Text("Historique par Année Scolaire", style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
        }

        // ── The prior-years banner (the "old debt still owed" composition) ──
        if (history.priorYearOutstandingStillOwed > 0L) {
            ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.COMPACT) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dettes des années précédentes", style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.danger)
                    Text(
                        "${(history.priorYearOutstandingStillOwed / 100).formatDzd()} DZD",
                        style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = c.danger,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        history.priorYearsStillOwed.forEach { item ->
                            ElTag(text = "${item.academicYear} : ${(item.outstanding / 100).formatDzd()}", tone = ElTagTone.DANGER)
                        }
                    }
                }
            }
        }

        history.years.forEach { year ->
            YearHistoryCard(year)
        }
    }
}

/** One academic year's collapsible record card. */
@Composable
private fun YearHistoryCard(year: AcademicYearFinancialRecord) {
    val c = ElTheme.colors
    var expanded by remember(year.academicYear) { mutableStateOf(false) }
    ElCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        size = ElCardSize.COMPACT,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(year.academicYear, style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.weight(1f))
                if (year.reEnrolledOwing) ElTag(text = "Réinscrit avec dette", tone = ElTagTone.WARNING)
                if (year.leftOwing) ElTag(text = "Parti avec dette", tone = ElTagTone.DANGER)
                ElTag(text = if (year.isOpen) "En cours" else "Clôturée", tone = if (year.isOpen) ElTagTone.SUCCESS else ElTagTone.INFO)
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Réduire" else "Déplier",
                    tint = c.textSecondary,
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Facturé : ${(year.totalCharged / 100).formatDzd()} DZD", style = ElTheme.typography.bodySmall)
                    Text("Payé : ${(year.totalPaidOnCharges / 100).formatDzd()} DZD", style = ElTheme.typography.bodySmall, color = c.success)
                    if (year.totalPendingOnCharges > 0L) {
                        Text("En attente : ${(year.totalPendingOnCharges / 100).formatDzd()} DZD", style = ElTheme.typography.bodySmall, color = c.warning)
                    }
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Reste fin d'année : ${(year.yearEndOutstanding / 100).formatDzd()}",
                        style = ElTheme.typography.bodySmall,
                        color = if (year.yearEndOutstanding > 0L) c.danger else c.success,
                    )
                    // T-442: the per-year still-owed-today figure (≠ year-end
                    // when later cross-year settlements arrived).
                    if (year.outstandingStillOwedNow != year.yearEndOutstanding) {
                        Text(
                            "Reste aujourd'hui : ${(year.outstandingStillOwedNow / 100).formatDzd()}",
                            style = ElTheme.typography.bodySmall,
                            color = c.danger,
                        )
                    }
                    if (year.carriedForwardFromPriorYear > 0L) {
                        Text(
                            "Reporté : ${(year.carriedForwardFromPriorYear / 100).formatDzd()}",
                            style = ElTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = c.outlineVariant)
                    ServicesDeLanneeBlock(year)
                    ChargesDeLanneeBlock(year)
                    PaiementsDeLanneeBlock(year)
                    CrossYearSettlementsBlock(year)
                    BalanceEvolutionLine(year)
                }
            }
        }
    }
}

/** « Services de l'année (N) » — the INV-20e per-service grouping. */
@Composable
private fun ServicesDeLanneeBlock(year: AcademicYearFinancialRecord) {
    val c = ElTheme.colors
    if (year.serviceBreakdown.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Services de l'année (${year.serviceBreakdown.size})", style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
        year.serviceBreakdown.forEach { group ->
            Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        when (group.key) {
                            YearServiceGroupKey.REGISTRATION -> "Frais d'inscription (FI)"
                            YearServiceGroupKey.TUITION -> "Scolarité"
                            YearServiceGroupKey.TRANSPORT -> "Transport"
                            YearServiceGroupKey.SERVICE -> categoryLabelFr(group.category)
                        },
                        style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f),
                    )
                    // The tranche chips (T1..T3; FI carries none — a fee,
                    // not a tranche; services carry none).
                    if (group.key == YearServiceGroupKey.TUITION || group.key == YearServiceGroupKey.TRANSPORT) {
                        group.trancheNumbers.filter { it in 1..3 }.forEach { t ->
                            ElTag(text = "T$t", tone = ElTagTone.INFO)
                        }
                    }
                    Text(
                        "Reste ${(group.remaining / 100).formatDzd()}",
                        style = ElTheme.typography.bodySmall,
                        color = if (group.remaining > 0L) c.danger else c.success,
                    )
                }
                Text(
                    buildString {
                        append("${group.chargeCount} charge(s)")
                        append(" · Dû ${(group.amountDue / 100).formatDzd()}")
                        append(" · Payé ${(group.amountPaid / 100).formatDzd()}")
                        if (group.amountPending > 0L) append(" · En attente ${(group.amountPending / 100).formatDzd()}")
                    },
                    style = ElTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }
    }
}

/** « Charges de l'année (N) » — the per-charge review tuple (INV-19c). */
@Composable
private fun ChargesDeLanneeBlock(year: AcademicYearFinancialRecord) {
    val c = ElTheme.colors
    if (year.charges.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Charges de l'année (${year.charges.size})", style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
        year.charges.forEach { charge ->
            Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (charge.trancheNumber in 1..3 && (charge.category == PaymentCategory.TUITION || charge.category == PaymentCategory.TRANSPORT)) {
                        ElTag(text = "T${charge.trancheNumber}", tone = ElTagTone.INFO)
                    } else if (charge.category == PaymentCategory.TUITION && charge.trancheNumber == 0) {
                        ElTag(text = "FI", tone = ElTagTone.INFO)
                    }
                    Text(charge.label, style = ElTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                    SettlementChip(charge.settlement)
                }
                Text(
                    "Dû ${(charge.amountDue / 100).formatDzd()} · Payé ${(charge.amountPaid / 100).formatDzd()} · Reste ${(charge.remaining / 100).formatDzd()} · Échéance ${charge.dueDate.take(10)}",
                    style = ElTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }
    }
}

/** The settlement chip vocabulary (the desktop's SETTLEMENT_LABEL_FR). */
@Composable
private fun SettlementChip(settlement: YearChargeSettlement) {
    val (label, tone) = when (settlement) {
        YearChargeSettlement.FULLY_PAID -> "Réglée" to ElTagTone.SUCCESS
        YearChargeSettlement.PARTIALLY_PAID -> "Partiellement réglée" to ElTagTone.WARNING
        YearChargeSettlement.PENDING_CLEARANCE -> "En attente d'encaissement" to ElTagTone.WARNING
        YearChargeSettlement.OUTSTANDING -> "Non réglée" to ElTagTone.DANGER
    }
    ElTag(text = label, tone = tone)
}

/** « Paiements de l'année (N) — total » — with the coverage lines (T-442). */
@Composable
private fun PaiementsDeLanneeBlock(year: AcademicYearFinancialRecord) {
    val c = ElTheme.colors
    if (year.paymentsMadeInYear.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Paiements de l'année (${year.paymentsMadeInYear.size})", style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
            Text("Total ${(year.paymentsMadeInYearTotal / 100).formatDzd()} DZD", style = ElTheme.typography.labelMedium, color = c.success)
        }
        year.paymentsMadeInYear.forEach { p ->
            Column(modifier = Modifier.fillMaxWidth().padding(start = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        p.receiptNumber ?: "Paiement",
                        style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        modifier = Modifier.weight(1f),
                    )
                    Text("${(p.amount / 100).formatDzd()} DZD", style = ElTheme.typography.bodyMedium, color = c.success)
                }
                Text(
                    "${p.at.take(10)}${p.method?.let { " · $it" } ?: ""}",
                    style = ElTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
                // The coverage lines — WHAT the payment settled (T-442).
                if (p.coverageBasis == CoverageBasis.ALLOCATIONS) {
                    p.coveredCharges.forEach { line ->
                        Text(
                            "↳ ${line.chargeLabel ?: line.category?.code ?: line.installmentId} : ${(line.allocatedAmount / 100).formatDzd()}" +
                                if (line.targetYear != year.academicYear) " (dette ${line.targetYear})" else "",
                            style = ElTheme.typography.bodySmall,
                            color = c.textSecondary,
                        )
                    }
                } else {
                    // The honest degradation (INV-18d — never a guessed coverage).
                    Text(
                        "couverture non enregistrée (données antérieures sans affectations)",
                        style = ElTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                        color = c.textSecondary,
                    )
                }
            }
        }
    }
}

/** The cross-year settlements received (the amber block). */
@Composable
private fun CrossYearSettlementsBlock(year: AcademicYearFinancialRecord) {
    val c = ElTheme.colors
    if (year.settlementsReceivedFromLaterYears.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Règlements reçus des années suivantes", style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = c.warning)
            Text("${(year.settlementsReceivedFromLaterYearsTotal / 100).formatDzd()} DZD", style = ElTheme.typography.labelMedium, color = c.warning)
        }
        year.settlementsReceivedFromLaterYears.forEach { s ->
            Text(
                "↳ ${s.chargeLabel ?: s.installmentId} : ${(s.allocatedAmount / 100).formatDzd()} (payé en ${s.paymentYear}, dette ${s.targetYear})",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
    }
}

/** The balance-evolution summary line (INV-20). */
@Composable
private fun BalanceEvolutionLine(year: AcademicYearFinancialRecord) {
    val c = ElTheme.colors
    val events: List<BalanceEvolutionEvent> = year.balanceEvolution
    if (events.isEmpty()) return
    val last = events.last()
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Évolution du solde (${events.size} événements)", style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold))
        Text(
            "Solde porté à la fin : ${(last.runningOutstanding / 100).formatDzd()} DZD" +
                " (charges ${(events.count { it.kind == "charge" })} · paiements ${(events.count { it.kind == "payment" })})",
            style = ElTheme.typography.bodySmall,
            color = if (last.runningOutstanding > 0L) c.danger else c.success,
        )
    }
}

/** The FR category label (§15.3 single-wording discipline). */
internal fun categoryLabelFr(category: PaymentCategory): String = when (category) {
    PaymentCategory.TUITION -> "Scolarité"
    PaymentCategory.TRANSPORT -> "Transport"
    PaymentCategory.CANTEEN -> "Cantine"
    PaymentCategory.UNIFORM -> "Uniforme"
    PaymentCategory.BOOKS -> "Livres"
    PaymentCategory.EXTRACURRICULAR -> "Activités"
    PaymentCategory.PARENT_CREDIT -> "Crédit famille"
    PaymentCategory.THERAPY_PSYCHOLOGY -> "Psychologie"
    PaymentCategory.THERAPY_SPEECH -> "Orthophonie"
    PaymentCategory.SECOND_APRON -> "Deuxième tablier"
    PaymentCategory.OTHER -> "Autre"
}
