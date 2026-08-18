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
import androidx.compose.material.icons.filled.Receipt
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

/**
 * Section (2) — Rich Operational KPI Cards.
 * Connects directly to real daily collections, overdue balances, live attendance, and pending operations.
 */
@Composable
internal fun DashboardKpiCardsRow(
    currentKpi: DashboardKpi,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        // ── Card 1: Daily & Monthly Collections ─────────────────────────────
        item {
            OperationalKpiCard(
                title = "Recettes du Jour",
                mainValue = "${(currentKpi.todayRevenue / 100).formatDzd()} DZD",
                subValue = "${currentKpi.todayPaymentsCount} encaissement(s) aujourd'hui",
                bottomLabel = "Mois: ${(currentKpi.monthlyRevenue / 100).formatDzd()} DZD",
                icon = Icons.Default.AccountBalance,
                accentColor = ElTheme.colors.success,
                modifier = Modifier.width(220.dp),
            )
        }

        // ── Card 2: Overdue Debt & Families in Default ──────────────────────
        item {
            OperationalKpiCard(
                title = "Créances en Retard",
                mainValue = "${(currentKpi.overdueDebt / 100).formatDzd()} DZD",
                subValue = "${currentKpi.overdueFamiliesCount} famille(s) en souffrance",
                bottomLabel = "Global: ${(currentKpi.outstandingDebt / 100).formatDzd()} DZD",
                icon = Icons.Default.MoneyOff,
                accentColor = ElTheme.colors.danger,
                modifier = Modifier.width(220.dp),
            )
        }

        // ── Card 3: Today's Attendance & Active Student Roll Call ──────────
        item {
            val rateFormatted = "%.1f %%".format(currentKpi.attendanceRateToday)
            OperationalKpiCard(
                title = "Présence & Appel du Jour",
                mainValue = rateFormatted,
                subValue = "${currentKpi.classesCompletedRollCall}/${currentKpi.totalClassesCount} classes validées",
                bottomLabel = "${currentKpi.totalStudents} élèves • ${currentKpi.totalStaff} staff",
                icon = Icons.Default.TrendingUp,
                accentColor = ElTheme.colors.primary,
                modifier = Modifier.width(210.dp),
            )
        }

        // ── Card 4: Pending Operations & Checks ─────────────────────────────
        item {
            OperationalKpiCard(
                title = "Opérations en Attente",
                mainValue = "${currentKpi.pendingExpenses + currentKpi.pendingChecksCount} à traiter",
                subValue = "${currentKpi.pendingChecksCount} chèque(s) • ${currentKpi.pendingExpenses} dépense(s)",
                bottomLabel = "${(currentKpi.pendingChecksAmount / 100).formatDzd()} DZD en chèques",
                icon = Icons.Default.Receipt,
                accentColor = ElTheme.colors.warning,
                modifier = Modifier.width(220.dp),
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
                    fontSize = 20.sp,
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
