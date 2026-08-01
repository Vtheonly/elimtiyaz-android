package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MoneyOff
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.DashboardKpi
import com.example.ui.designsystem.components.card.ElGradientStatCard
import com.example.ui.designsystem.components.display.ElGradient
import com.example.ui.designsystem.foundation.elMoneyFormat
import com.example.ui.designsystem.foundation.elPercentFormat

/**
 * Section (b) — horizontally scrollable row of 4 KPI gradient stat cards:
 * active students, monthly revenue, outstanding debt, today's attendance.
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
        item {
            ElGradientStatCard(
                title = "Élèves actifs",
                value = currentKpi.totalStudents.toString(),
                gradient = ElGradient.BRAND,
                icon = Icons.Default.Groups,
                subtitle = "${currentKpi.totalParents} familles",
                modifier = Modifier.width(200.dp),
            )
        }
        item {
            ElGradientStatCard(
                title = "Revenu mensuel",
                value = elMoneyFormat(currentKpi.monthlyRevenue),
                gradient = ElGradient.REVENUE,
                icon = Icons.Default.AccountBalance,
                subtitle = "Encaissements du mois",
                modifier = Modifier.width(220.dp),
            )
        }
        item {
            ElGradientStatCard(
                title = "Créances en souffrance",
                value = elMoneyFormat(currentKpi.outstandingDebt),
                gradient = ElGradient.DEBT,
                icon = Icons.Default.MoneyOff,
                subtitle = "${currentKpi.overdueAlerts} en retard",
                modifier = Modifier.width(220.dp),
            )
        }
        item {
            ElGradientStatCard(
                title = "Présence aujourd'hui",
                value = elPercentFormat(currentKpi.attendanceRateToday / 100.0),
                gradient = ElGradient.ATTENDANCE,
                icon = Icons.Default.TrendingUp,
                subtitle = "${currentKpi.totalStaff} staff",
                modifier = Modifier.width(200.dp),
            )
        }
    }
}
