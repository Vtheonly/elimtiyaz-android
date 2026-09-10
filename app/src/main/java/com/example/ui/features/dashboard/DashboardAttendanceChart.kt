package com.example.ui.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
            title = "Vie Scolaire & Assiduité",
            subtitle = "Pointage quotidien et tendance hebdomadaire",
            trailing = {
                ElButton(
                    text = "Module Pédagogie",
                    onClick = onNavigateToAcademics,
                    variant = ElButtonVariant.GHOST,
                    size = ElButtonSize.SMALL,
                )
            },
        )

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Statut de l'appel par classe",
                        style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                    ElTag(
                        text = "$classesCompleted / $totalClasses validées",
                        tone = if (classesCompleted == totalClasses) ElTagTone.SUCCESS else ElTagTone.NEUTRAL,
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
                                .clip(ElTheme.shapes.small)
                                .clickable { onNavigateToRollCall(classStatus.classId) }
                                .padding(vertical = 5.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (classStatus.isCompletedToday) ElTheme.colors.success
                                            else ElTheme.colors.outline
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
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.size(4.dp))
                                    Text(
                                        text = "Validé",
                                        style = ElTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ElTheme.colors.success,
                                    )
                                }
                            } else {
                                Text(
                                    text = "En attente",
                                    style = ElTheme.typography.labelSmall,
                                    color = ElTheme.colors.textMuted,
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

                Text(
                    text = "Taux de présence sur 7 jours (%.0f%% aujourd'hui)".format(attendanceRateToday),
                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = ElTheme.colors.textPrimary,
                )
                Spacer(Modifier.height(8.dp))

                if (attendanceTrend.isNotEmpty()) {
                    ElLineChart(
                        points = attendanceTrend,
                        height = 130.dp,
                        lineColor = ElTheme.colors.info,
                        gradientFill = true,
                    )
                } else {
                    Text(
                        text = "Aucun appel enregistré ces 7 derniers jours.",
                        style = ElTheme.typography.bodySmall,
                        color = ElTheme.colors.textSecondary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}