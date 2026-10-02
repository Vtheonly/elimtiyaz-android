package com.example.ui.features.dashboard.analytics

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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.ParetoDebtor
import com.example.core.derivePareto
import com.example.core.formatDzd
import com.example.domain.model.DebtSummary
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.data.ElParetoChart
import com.example.ui.designsystem.components.data.ElParetoPoint
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.overlays.ElInfoTip
import com.example.ui.designsystem.overlays.LocalElToast
import com.example.ui.designsystem.theme.ElTheme

/**
 * DebtorsParetoCard — inventory #8 (PARITY-003).
 *
 * The native twin of the desktop's `debtors-pareto-card.tsx` (T-257):
 * the 80/20 chart (danger bars + the gold cumulative-share curve + the
 * 80% guide) over the engine's derivePareto top-8, the "X fam. = 80% de
 * l'encours affiché" verdict, and the interactive top-debtor rows
 * (clickable → parent detail; dialer preserved from the mobile surface).
 */
@Composable
internal fun DebtorsParetoCard(
    debtors: List<DebtSummary>,
    onNavigateToParent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val pareto = derivePareto(debtors.map { ParetoDebtor(it.parentName, it.outstandingAmount) }, topN = 8)
    if (pareto.isEmpty()) {
        AnalyticsEmptyCard(title = "Pareto des Débiteurs", subtitle = "Aucune créance ouverte.")
        return
    }
    // The desktop verdict: index of the first cumPercent ≥ 80 (+1 = families)
    val paretoCut = pareto.indexOfFirst { it.cumPercent >= 80 }.let { if (it == -1) pareto.size else it + 1 }

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ElSectionHeader(
                    title = "Pareto des Débiteurs",
                    subtitle = "$paretoCut fam. = 80% de l'encours affiché",
                    modifier = Modifier.weight(1f),
                )
                // T-458: the T-447 explainability glossary (presentation-only).
                ElInfoTip(tip = "pareto.card")
            }
            ElParetoChart(
                points = pareto.map {
                    ElParetoPoint(
                        label = shortDebtorName(it.name),
                        amount = (it.amount / 100).toFloat(),
                        cumPercent = it.cumPercent.toFloat(),
                    )
                },
                height = 170.dp,
            )
            // The interactive top-8 rows (click → parent; call preserved)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pareto.forEachIndexed { i, datum ->
                    val debtor = debtors.firstOrNull { it.parentName == datum.name }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .clickable(enabled = debtor != null) {
                                    debtor?.let { onNavigateToParent(it.parentId) }
                                },
                        ) {
                            Text(
                                text = "${i + 1}.",
                                color = c.textMuted,
                                style = ElTheme.textStyles.badge.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.width(18.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = datum.name,
                                    color = c.textPrimary,
                                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "cumul ${datum.cumPercent}%",
                                    color = ElChartPalette.gold,
                                    style = ElTheme.textStyles.chartMicro,
                                )
                            }
                        }
                        Text(
                            text = "${(datum.amount / 100).formatDzd()} DA",
                            color = ElChartPalette.danger,
                            style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                        )
                        if (debtor?.parentPhone?.isNotBlank() == true) {
                            Spacer(Modifier.width(4.dp))
                            val dialContext = androidx.compose.ui.platform.LocalContext.current
                            val dialToast = LocalElToast.current
                            // T-460 H2 (issue #3 F-10): the raw M3 IconButton →
                            // the DS ElIconButton (explicit contentDescription
                            // preserved — the audit's A-4 rule); F-18: the dial
                            // errors go through the DS toast layer.
                            ElIconButton(
                                icon = Icons.Default.Call,
                                onClick = {
                                    com.example.ui.util.PhoneUtils.dial(dialContext, debtor.parentPhone, dialToast)
                                },
                                contentDescription = "Appeler ${datum.name}",
                                tint = ElChartPalette.primary,
                                background = androidx.compose.ui.graphics.Color.Transparent,
                                size = 36,
                                iconSize = 18,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Famille BENALI Karim" → "F. BENALI" (the desktop short-name convention). */
internal fun shortDebtorName(name: String): String {
    val trimmed = name.removePrefix("Famille ").trim()
    if (trimmed.isEmpty()) return name.take(8)
    val parts = trimmed.split(" ")
    val last = parts.lastOrNull() ?: trimmed
    val firstInitial = parts.firstOrNull()?.firstOrNull()?.let { "$it." } ?: ""
    return (firstInitial + " " + last).trim()
}
