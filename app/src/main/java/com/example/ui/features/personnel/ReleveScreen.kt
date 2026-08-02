package com.example.ui.features.personnel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.ui.components.ElCard
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElGradientStatCard
import com.example.ui.components.ElProgressBar
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import androidx.compose.runtime.getValue

@Composable
fun ReleveScreen(
    session: Session,
    onNavigateToReleve: (String) -> Unit = {},
    viewModel: ReleveViewModel = hiltViewModel(),
) {
    val personnel by viewModel.personnel.collectAsState()
    val teachers = personnel.filter { it.staffCategory == "teacher" || it.weeklyHoursTarget > 0 }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
            val target = staff.weeklyHoursTarget.coerceAtLeast(1)
            val logged = staff.weeklyHoursLogged
            val compliance = (logged.toFloat() / target * 100).toInt().coerceIn(0, 100)
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
                        "$logged / $target Heures Effectuées",
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
    }
}
