package com.example.ui.features.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElGradientStatCard
import com.example.ui.components.ElTag
import com.example.ui.components.ElTextField
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LightBlue
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun RollCallScreen(
    session: Session,
    onNavigateToRollCall: (String) -> Unit = {},
    /** Pre-selected class when opened standalone from ClassDetail. */
    initialClassId: String? = null,
    /** Back affordance when pushed as a standalone route (hidden when embedded in the hub). */
    onBack: (() -> Unit)? = null,
    viewModel: RollCallViewModel = hiltViewModel(),
) {
    val classes by viewModel.classes.collectAsState()
    val students by viewModel.students.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    var selectedClassId by remember { mutableStateOf<String?>(initialClassId) }
    val statuses = remember { mutableStateMapOf<String, AttendanceStatus>() }
    val lateTimes = remember { mutableStateMapOf<String, String>() }
    val today = remember { LocalDate.now().toString() }

    // Auto-select first class when list loads (skip when an initial class was
    // provided — keep the caller's choice).
    androidx.compose.runtime.LaunchedEffect(classes) {
        if (selectedClassId == null && classes.isNotEmpty()) {
            selectedClassId = classes.first().id
        }
    }
    // Load students when class changes.
    // FIX (cross-class contamination): previously the status/late maps were
    // NEVER cleared when switching classes, so students of a previously
    // selected class were submitted as "present" in the new class's roll
    // call. The maps are now reset on every class switch.
    androidx.compose.runtime.LaunchedEffect(selectedClassId) {
        statuses.clear()
        lateTimes.clear()
        selectedClassId?.let { viewModel.loadStudentsForClass(it) }
    }

    val selectedClass = classes.firstOrNull { it.id == selectedClassId }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            com.example.ui.components.ElTopBar(
                title = "Appel — ${selectedClass?.name ?: "…"}",
                onBack = onBack,
            )
        }
        ElGradientStatCard(
            title = "Appel — ${today}",
            value = selectedClass?.name ?: "Chargement…",
            subtitle = "Basculez les statuts des élèves rapidement",
            modifier = Modifier.fillMaxWidth(),
        )

        if (classes.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Class,
                title = "Aucune classe",
                message = "Aucune classe n'est disponible. Créez-en une depuis les paramètres ou contactez un administrateur.",
            )
            return@Column
        }

        ElDropdown(
            label = "Classe",
            selectedValue = selectedClass?.name ?: "",
            options = classes.map { it.name },
            onSelected = { name -> selectedClassId = classes.first { it.name == name }.id },
            modifier = Modifier.fillMaxWidth(),
        )

        if (students.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Class,
                title = "Aucun élève",
                message = "Aucun élève inscrit dans cette classe.",
            )
        } else {
            students.forEach { student ->
                val currentStatus = statuses[student.id] ?: AttendanceStatus.PRESENT
                if (!statuses.containsKey(student.id)) statuses[student.id] = AttendanceStatus.PRESENT
                val isLate = currentStatus == AttendanceStatus.LATE

                ElCard(
                    modifier = Modifier.fillMaxWidth(),
                    accent = if (currentStatus == AttendanceStatus.ABSENT) DangerRed else null,
                    compact = true,
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ElAvatar(initials = student.fullName, size = 40)
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(
                                        student.fullName,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 15.sp,
                                        ),
                                    )
                                    Text(
                                        "Code: ${student.code}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AttendanceStatus.values().forEach { st ->
                                ElTag(
                                    text = st.label,
                                    color = st.color,
                                    selected = currentStatus == st,
                                    onClick = {
                                        statuses[student.id] = st
                                        if (st == AttendanceStatus.LATE && !lateTimes.containsKey(student.id)) {
                                            lateTimes[student.id] = "08:15"
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }

                        if (isLate) {
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Schedule, contentDescription = null, tint = LightBlue, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                ElTextField(
                                    value = lateTimes[student.id] ?: "08:15",
                                    onValueChange = { lateTimes[student.id] = it },
                                    label = "Heure d'arrivée",
                                    modifier = Modifier.width(180.dp),
                                    singleLine = true,
                                )
                            }
                        }
                    }
                }
            }
        }

        message?.let {
            ElAlertBanner(
                message = it,
                severity = if (it.startsWith("Appel enregistré")) ElAlertSeverity.Success else ElAlertSeverity.Warning,
                title = if (it.startsWith("Appel enregistré")) "Appel Validé" else "Erreur",
            )
        }

        ElButton(
            text = "Valider l'appel (${selectedClass?.name ?: ""})",
            onClick = {
                val cid = selectedClassId ?: return@ElButton
                // FIX (contamination): only submit statuses for students of the
                // CURRENT class — never leftover entries from other classes.
                val currentStudentIds = students.map { it.id }.toSet()
                val scopedStatuses = statuses.filterKeys { it in currentStudentIds }
                val scopedLateTimes = lateTimes.filterKeys { it in currentStudentIds }
                viewModel.submitRollCall(
                    classId = cid,
                    date = today,
                    session = "morning",
                    statuses = scopedStatuses,
                    lateTimes = scopedLateTimes,
                    actorId = session.userId,
                    actorName = session.displayName,
                )
            },
            fullWidth = true,
            icon = Icons.Default.Send,
            enabled = !busy && students.isNotEmpty(),
        )
    }
}
