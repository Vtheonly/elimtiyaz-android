package com.example.ui.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.ClassRollCallStatus
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.data.ElLineChart
import com.example.ui.designsystem.components.data.ElLineChartPoint
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme

/**
 * Section (4) — Today's Class Roll-Call & Live Attendance Pulse.
 * Displays class-by-class live attendance status and 7-day attendance trend.
 */
@Composable
internal fun DashboardAttendanceChart(
    classStatuses: List<ClassRollCallStatus>,
    attendanceTrend: List<ElLineChartPoint>,
    attendanceRateToday: Double,
    classesCompleted: Int,
    totalClasses: Int,
    onNavigateToRollCall: (String) -> Unit,
    onNavigateToAcademics: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ElSectionHeader(
            title = "Vie Scolaire & Suivi des Présences",
            subtitle = "Validation de l'appel du matin et tendance hebdomadaire",
            trailing = {
                ElButton(
                    text = "Pédagogie",
                    onClick = onNavigateToAcademics,
                    variant = ElButtonVariant.GHOST,
                    size = ElButtonSize.SMALL,
                )
            },
        )

        // ── Class-by-Class Roll Call Grid ────────────────────────────────────
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Appel du jour par classe",
                        style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                    ElTag(
                        text = "$classesCompleted / $totalClasses validées",
                        tone = if (classesCompleted == totalClasses) ElTagTone.SUCCESS else ElTagTone.WARNING,
                    )
                }

                Spacer(Modifier.height(10.dp))

                if (classStatuses.isEmpty()) {
                    Text(
                        text = "Aucune classe configurée.",
                        style = ElTheme.typography.bodySmall,
                        color = ElTheme.colors.textSecondary,
                    )
                } else {
                    classStatuses.forEach { classStatus ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (classStatus.isCompletedToday) ElTheme.colors.success
                                            else ElTheme.colors.warning
                                        ),
                                )
                                Spacer(Modifier.size(8.dp))
                                Column {
                                    Text(
                                        text = classStatus.className,
                                        style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = ElTheme.colors.textPrimary,
                                    )
                                    Text(
                                        text = if (classStatus.isCompletedToday) {
                                            "${classStatus.presentCount} présents • ${classStatus.absentCount} absents"
                                        } else {
                                            "${classStatus.totalStudents} élèves inscrits"
                                        },
                                        style = ElTheme.typography.labelSmall,
                                        color = ElTheme.colors.textSecondary,
                                    )
                                }
                            }

                            if (classStatus.isCompletedToday) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = ElTheme.colors.success,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        text = "Validé",
                                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ElTheme.colors.success,
                                    )
                                }
                            } else {
                                ElButton(
                                    text = "Faire l'appel",
                                    onClick = { onNavigateToRollCall(classStatus.classId) },
                                    variant = ElButtonVariant.OUTLINED,
                                    size = ElButtonSize.SMALL,
                                    icon = Icons.Default.EditNote,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                androidx.compose.material3.HorizontalDivider(
                    color = ElTheme.colors.outlineVariant,
                    thickness = 1.dp,
                )
                Spacer(Modifier.height(10.dp))

                // ── 7-Day Attendance Trend ────────────────────────────────────
                Text(
                    text = "Taux de présence sur 7 jours (%.1f%% aujourd'hui)".format(attendanceRateToday),
                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))

                if (attendanceTrend.isNotEmpty()) {
                    ElLineChart(
                        points = attendanceTrend,
                        height = 140.dp,
                        lineColor = ElTheme.colors.info,
                        gradientFill = true,
                    )
                } else {
                    // FIX (truthful empty state): previously nothing was shown
                    // when there was no attendance data — now the user gets an
                    // explicit explanation instead of a silently missing chart.
                    Text(
                        text = "Aucun appel enregistré ces 7 derniers jours — la tendance apparaîtra dès le premier appel.",
                        style = ElTheme.typography.bodySmall,
                        color = ElTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}