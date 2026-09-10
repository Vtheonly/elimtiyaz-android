package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.formatDzd
import com.example.domain.model.DashboardKpi
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.theme.ElTheme

@Composable
internal fun DashboardKpiCardsRow(
    currentKpi: DashboardKpi,
) {
    val revenueToShow = if (currentKpi.totalRevenue > 0L) currentKpi.totalRevenue else currentKpi.monthlyRevenue

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        // ── Card 1: Élèves & Familles ──
        item {
            OperationalKpiCard(
                title = "ÉLÈVES",
                mainValue = "${currentKpi.totalStudents}",
                subValue = "${currentKpi.totalFamilies} familles",
                bottomLabel = "${currentKpi.totalStaff} membres du personnel",
                icon = Icons.Default.People,
                accentColor = ElTheme.colors.primary,
                modifier = Modifier.width(210.dp),
            )
        }

        // ── Card 2: Revenu Global ──
        item {
            val revenueFormatted = if (revenueToShow >= 1_000_000_00L) {
                "%.1f M DZD".format(revenueToShow / 100_000_000.0)
            } else {
                "${(revenueToShow / 100).formatDzd()} DZD"
            }
            OperationalKpiCard(
                title = "REVENU GLOBAL",
                mainValue = revenueFormatted,
                subValue = "${(revenueToShow / 100).formatDzd()} DA",
                bottomLabel = "${currentKpi.totalOperationsCount} opérations enregistrées",
                icon = Icons.Default.AccountBalance,
                accentColor = ElTheme.colors.success,
                modifier = Modifier.width(220.dp),
            )
        }

        // ── Card 3: Créances en Retard ──
        item {
            val overdueFormatted = if (currentKpi.overdueDebt >= 1_000_000_00L) {
                "%.1f M DZD".format(currentKpi.overdueDebt / 100_000_000.0)
            } else {
                "${(currentKpi.overdueDebt / 100).formatDzd()} DZD"
            }
            OperationalKpiCard(
                title = "CRÉANCES EN RETARD",
                mainValue = overdueFormatted,
                subValue = "${currentKpi.overdueFamiliesCount} fam. en retard",
                bottomLabel = "${(currentKpi.overdueDebt / 100).formatDzd()} DA d'encours",
                icon = Icons.Default.MoneyOff,
                accentColor = ElTheme.colors.danger,
                modifier = Modifier.width(220.dp),
            )
        }

        // ── Card 4: Assiduité (Aujourd'hui) ──
        item {
            val rateFormatted = "%.0f%%".format(currentKpi.attendanceRateToday)
            OperationalKpiCard(
                title = "ASSIDUITÉ DU JOUR",
                mainValue = rateFormatted,
                subValue = "${currentKpi.classesCompletedRollCall}/${currentKpi.totalClassesCount} classes validées",
                bottomLabel = "${currentKpi.todayPresentCount} présents aujourd'hui",
                icon = Icons.Default.TrendingUp,
                accentColor = ElTheme.colors.info,
                modifier = Modifier.width(210.dp),
            )
        }
    }
}

@Composable
private fun OperationalKpiCard(
    title: String,
    mainValue: String,
    subValue: String,
    bottomLabel: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    ElCard(modifier = modifier) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = ElTheme.colors.textSecondary,
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .padding(4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = mainValue,
                style = ElTheme.textStyles.numeric.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                ),
                color = accentColor,
            )

            Spacer(Modifier.height(2.dp))
            Text(
                text = subValue,
                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = ElTheme.colors.textPrimary,
            )

            Spacer(Modifier.height(6.dp))
            Text(
                text = bottomLabel,
                style = ElTheme.typography.labelSmall,
                color = ElTheme.colors.textMuted,
            )
        }
    }
}