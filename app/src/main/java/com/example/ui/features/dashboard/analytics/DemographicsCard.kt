package com.example.ui.features.dashboard.analytics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.ClassDemographicsSnapshot
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElBarChart
import com.example.ui.designsystem.components.data.ElBarChartItem
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.components.data.ElDonutChart
import com.example.ui.designsystem.components.data.ElDonutSegment
import com.example.ui.designsystem.components.data.ElGaugeArc
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme

/**
 * DemographicsCard — inventory #13 (PARITY-003).
 *
 * The native twin of the desktop's `see-details-modal.tsx` demographics
 * block: the LEVEL bar chart (grade distribution), the GENDER donut
 * (Garçons/Filles/Non spécifié; primary/gold/slate cell cycle; center =
 * total élèves), the AGE histogram (cyan bars), and the CAPACITY gauges
 * (semi-circle arcs; tone: danger ≥100 / gold ≥80 / success). Values from
 * the engine's deriveDemographics (repository contract).
 */
@Composable
internal fun DemographicsCard(
    demographics: ClassDemographicsSnapshot,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val totalStudents = demographics.gender.sumOf { it.count }
    if (totalStudents == 0 && demographics.capacity.isEmpty()) {
        AnalyticsEmptyCard(title = "Démographie & Capacité", subtitle = "Aucun élève actif.")
        return
    }

    ElCard(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ElSectionHeader(
                title = "Démographie & Capacité",
                subtitle = "$totalStudents élèves actifs",
            )

            // ── Gender donut (desktop dual-ring layout: center total) ──
            if (demographics.gender.isNotEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Répartition par Sexe",
                        color = c.textSecondary,
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(6.dp))
                    Box(contentAlignment = Alignment.Center) {
                        ElDonutChart(
                            segments = demographics.gender.mapIndexed { i, g ->
                                ElDonutSegment(
                                    label = "${g.label} ${g.percent}%",
                                    value = g.count.toFloat(),
                                    color = ElChartPalette.genderCycle[i % ElChartPalette.genderCycle.size],
                                )
                            },
                            size = 130.dp,
                            centerValue = "$totalStudents",
                            centerLabel = "élèves",
                        )
                    }
                }
            }

            // ── Level (grade) distribution bars ──
            if (demographics.grade.isNotEmpty()) {
                Column {
                    Text(
                        text = "Répartition par Niveau",
                        color = c.textSecondary,
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(4.dp))
                    ElBarChart(
                        data = demographics.grade.take(14).map {
                            ElBarChartItem(label = it.label, value = it.count.toFloat(), color = ElChartPalette.primary)
                        },
                        height = 120.dp,
                    )
                }
            }

            // ── Age histogram (cyan bars — the desktop convention) ──
            if (demographics.age.isNotEmpty()) {
                Column {
                    Text(
                        text = "Répartition par Âge",
                        color = c.textSecondary,
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(4.dp))
                    ElBarChart(
                        data = demographics.age.map {
                            ElBarChartItem(label = it.label, value = it.count.toFloat(), color = ElChartPalette.cyan)
                        },
                        height = 100.dp,
                    )
                }
            }

            // ── Capacity gauges (top classes; scroll handled by the caller) ──
            if (demographics.capacity.isNotEmpty()) {
                Column {
                    Text(
                        text = "Taux de Remplissage",
                        color = c.textSecondary,
                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        demographics.capacity.take(4).forEach { cap ->
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                ElGaugeArc(
                                    percent = cap.percent,
                                    caption = "${cap.label} (${cap.count})",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
