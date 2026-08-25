package com.example.ui.features.personnel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.domain.model.ReleveActivity
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElGradientStatCard
import com.example.ui.components.ElProgressBar
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold

@Composable
fun ReleveScreen(
    session: Session,
    onNavigateToReleve: (String) -> Unit = {},
    /** Back affordance when pushed as a standalone route. */
    onBack: (() -> Unit)? = null,
    viewModel: ReleveViewModel = hiltViewModel(),
) {
    val personnel by viewModel.personnel.collectAsState()
    val focused by viewModel.focusedPersonnel.collectAsState()
    val focusedEntries by viewModel.focusedEntries.collectAsState()
    val weeklyMinutes by viewModel.weeklyMinutesByPersonnel.collectAsState()
    val teachers = personnel.filter { it.staffCategory == "teacher" || it.weeklyHoursTarget > 0 }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            com.example.ui.components.ElTopBar(
                title = "Relevé d'activité",
                onBack = onBack,
            )
        }

        // ── Focused view: opened from a personnel profile (personnelId arg) ──
        // FIX: the route argument was previously ignored — the screen ALWAYS
        // showed the all-staff directory even when navigated from a profile.
        if (viewModel.personnelId.isNotBlank()) {
            ElGradientStatCard(
                title = "Relevé d'activité",
                value = focused?.fullName ?: "…",
                subtitle = "Heures enregistrées cette semaine",
                modifier = Modifier.fillMaxWidth(),
            )
            focused?.let { staff ->
                val loggedMinutes = weeklyMinutes[staff.id] ?: 0L
                ComplianceCard(staff = staff, loggedMinutes = loggedMinutes)
            }
            if (focusedEntries.isEmpty()) {
                ElEmptyState(
                    icon = Icons.Default.Schedule,
                    title = "Aucune activité enregistrée",
                    message = "Aucune entrée de relevé n'existe encore pour ce membre du personnel.",
                )
            } else {
                focusedEntries.take(20).forEach { entry ->
                    ElCard(modifier = Modifier.fillMaxWidth(), compact = true) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    entry.date,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    entry.activity.displayFr,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                buildString {
                                    append("Durée : ${entry.durationMinutes ?: 0} min")
                                    if (entry.hoursIn.isNotBlank()) {
                                        append(" · ${entry.hoursIn}")
                                        entry.hoursOut?.let { append(" → $it") }
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            return@Column
        }

        // ── Directory view: weekly compliance for all teaching staff ──
        ElGradientStatCard(
            title = "Relevé d'Activité",
            value = "${teachers.size} Enseignants",
            subtitle = "Suivi hebdomadaire des heures",
            modifier = Modifier.fillMaxWidth(),
        )

        if (teachers.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Code,
                title = "Aucune donnée d'activité",
                message = "Aucun personnel avec objectif horaire défini.",
            )
            return@Column
        }

        teachers.forEach { staff ->
            // FIX (stale compliance): the bar was driven by a
            // `weeklyHoursLogged` column that nothing ever updated — it was
            // always 0. The logged minutes now come from the REAL
            // `releve_entries` rows of the current ISO week.
            ComplianceCard(staff = staff, loggedMinutes = weeklyMinutes[staff.id] ?: 0L)
        }
    }
}

@Composable
private fun ComplianceCard(
    staff: com.example.domain.model.Personnel,
    loggedMinutes: Long,
) {
    val target = staff.weeklyHoursTarget.coerceAtLeast(1)
    val loggedHours = loggedMinutes / 60.0
    val compliance = (loggedHours / target * 100).toInt().coerceIn(0, 100)
    val complianceColor = when {
        compliance >= 95 -> SuccessGreen
        compliance >= 80 -> WarmGold
        else -> PrimaryBlue
    }

    ElCard(modifier = Modifier.fillMaxWidth(), compact = true) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                staff.fullName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "%.1f / $target Heures Effectuées".format(loggedHours),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Conformité", style = MaterialTheme.typography.labelSmall)
                Text(
                    "$compliance%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = complianceColor,
                )
            }
            Spacer(Modifier.height(6.dp))
            ElProgressBar(progress = compliance / 100f)
        }
    }
}
