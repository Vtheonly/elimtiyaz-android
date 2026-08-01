package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.repository.RevenuePoint
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElBarChart
import com.example.ui.designsystem.components.data.ElBarChartItem
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme

/**
 * Section (c) — monthly revenue bar chart covering the last 12 months.
 *
 * Each bar's label is truncated to its first 3 characters (month abbreviation)
 * and all bars share the primary brand color.
 */
@Composable
internal fun DashboardRevenueChart(
    revenue: List<RevenuePoint>,
) {
    ElSectionHeader(
        title = "Revenu mensuel",
        subtitle = "12 derniers mois",
    )
    ElCard(modifier = Modifier.fillMaxWidth()) {
        ElBarChart(
            data = revenue.map {
                ElBarChartItem(
                    label = it.label.take(3),
                    value = it.amount.toFloat(),
                    color = ElTheme.colors.primary,
                )
            },
            height = 200.dp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
