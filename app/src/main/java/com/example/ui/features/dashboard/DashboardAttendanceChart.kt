package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElLineChart
import com.example.ui.designsystem.components.data.ElLineChartPoint
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.theme.ElTheme

/**
 * Section (e) — attendance trend line chart covering the last 7 days.
 *
 * Points are passed straight through from [DashboardViewModel.attendanceTrend],
 * which already merges today's live rate with the 6-day historical baseline.
 */
@Composable
internal fun DashboardAttendanceChart(
    attendanceTrend: List<ElLineChartPoint>,
) {
    ElSectionHeader(
        title = "Tendance de présence",
        subtitle = "7 derniers jours",
    )
    ElCard(modifier = Modifier.fillMaxWidth()) {
        ElLineChart(
            points = attendanceTrend,
            height = 160.dp,
            lineColor = ElTheme.colors.info,
            gradientFill = true,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
