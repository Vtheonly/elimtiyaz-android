package com.example.ui.features.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.example.core.Session
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElAvatar
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElGradientStatCard
import com.example.ui.components.ElListItem
import com.example.ui.components.ElSectionHeader
import com.example.ui.components.ElTag
import com.example.ui.components.ElTextField
import com.example.ui.components.ModernSecondaryTabRow
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LightBlue
import com.example.ui.theme.PrimaryBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold

@Composable
fun AcademicsHubScreen(session: Session) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Présences", "Notes", "Devoirs", "Classes")

    Column(modifier = Modifier.fillMaxSize()) {
        ModernSecondaryTabRow(
            tabs = tabs,
            selectedTabIndex = selectedTab,
            onTabSelected = { selectedTab = it },
        )
        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.TopStart) {
            when (selectedTab) {
                0 -> RollCallScreen(session)
                1 -> GradeEntryScreen(session)
                2 -> HomeworkPushScreen(session)
                3 -> ClassesDirectoryScreen(session)
            }
        }
    }
}

// ── 1. 30-Second Roll Call Engine ──────────────────────────────────────────

enum class AttendanceStatus(val label: String, val color: Color) {
    PRESENT("Présent", SuccessGreen),
    ABSENT("Absent", DangerRed),
    EXCUSED("Excusé", WarmGold),
    LATE("Retard", LightBlue),
}

data class SampleStudent(
    val id: String,
    val name: String,
    val classId: String,
    val termAbsences: Int,
)

val SAMPLE_CLASSES = listOf("PRIM - CP A", "PRIM - CE1 B", "COLG - 1AAM A", "COLG - 4AM C", "LYC - 3AS S")
val SAMPLE_STUDENTS = listOf(
    SampleStudent("STU-001", "Amine Benali", "PRIM - CE1 B", 2),
    SampleStudent("STU-002", "Sarra Khelifi", "PRIM - CE1 B", 0),
    SampleStudent("STU-003", "Yacine Belkacem", "PRIM - CE1 B", 3),
    SampleStudent("STU-004", "Lina Brahimi", "PRIM - CE1 B", 1),
    SampleStudent("STU-005", "Mehdi Mansouri", "PRIM - CE1 B", 5),
    SampleStudent("STU-006", "Nour Haddad", "PRIM - CE1 B", 0),
)

@Composable
fun RollCallScreen(session: Session) {
    var selectedClass by remember { mutableStateOf(SAMPLE_CLASSES[1]) }
    val statuses = remember { mutableStateMapOf<String, AttendanceStatus>() }
    val lateTimes = remember { mutableStateMapOf<String, String>() }
    var alertMessage by remember { mutableStateOf<String?>(null) }

    SAMPLE_STUDENTS.forEach { student ->
        if (!statuses.containsKey(student.id)) {
            statuses[student.id] = AttendanceStatus.PRESENT
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElGradientStatCard(
            title = "Appel — 30 Secondes",
            value = selectedClass,
            subtitle = "Basculez les statuts des élèves rapidement",
            modifier = Modifier.fillMaxWidth(),
        )

        ElDropdown(
            label = "Classe",
            selectedValue = selectedClass,
            options = SAMPLE_CLASSES,
            onSelected = { selectedClass = it },
            modifier = Modifier.fillMaxWidth(),
        )

        SAMPLE_STUDENTS.forEach { student ->
            val currentStatus = statuses[student.id] ?: AttendanceStatus.PRESENT
            val isLate = currentStatus == AttendanceStatus.LATE
            val isThresholdReached = student.termAbsences + (if (currentStatus == AttendanceStatus.ABSENT) 1 else 0) >= 3

            ElCard(
                modifier = Modifier.fillMaxWidth(),
                accent = if (isThresholdReached) DangerRed else null,
                compact = true,
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ElAvatar(initials = student.name, size = 40)
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(student.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp))
                                Text("${student.termAbsences} absences ce trimestre", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (isThresholdReached) {
                            ElTag(text = "Alerte 3+", color = DangerRed, selected = true)
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

        alertMessage?.let {
            ElAlertBanner(
                message = it,
                severity = ElAlertSeverity.Success,
                title = "Appel Validé",
            )
        }

        ElButton(
            text = "Valider l'appel ($selectedClass)",
            onClick = {
                val thresholdCount = SAMPLE_STUDENTS.count { student ->
                    val status = statuses[student.id] ?: AttendanceStatus.PRESENT
                    (student.termAbsences + (if (status == AttendanceStatus.ABSENT) 1 else 0)) >= 3
                }
                alertMessage = "Appel enregistré! $thresholdCount élève(s) au seuil d'alerte notifiés au portail parents."
            },
            fullWidth = true,
            icon = Icons.Default.Send,
        )
    }
}

// ── 2. Mobile Grade Entry Engine ──────────────────────────────────────────

@Composable
fun GradeEntryScreen(session: Session) {
    var subject by remember { mutableStateOf("Mathématiques") }
    var selectedClass by remember { mutableStateOf("COLG - 1AAM A") }
    var term by remember { mutableStateOf("Trimestre 1") }

    var devoir1Text by remember { mutableStateOf("14.5") }
    var devoir2Text by remember { mutableStateOf("15.0") }
    var examenText by remember { mutableStateOf("16.0") }

    val d1 = devoir1Text.toDoubleOrNull() ?: 0.0
    val d2 = devoir2Text.toDoubleOrNull() ?: 0.0
    val ex = examenText.toDoubleOrNull() ?: 0.0
    val subjectAverage = (d1 + d2 + (ex * 2)) / 4.0

    var savedMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElGradientStatCard(
            title = "Saisie des Notes",
            value = "%.2f / 20".format(subjectAverage),
            subtitle = "Moyenne calculée en temps réel",
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ElTextField(value = subject, onValueChange = { subject = it }, label = "Matière", modifier = Modifier.weight(1f))
            ElTextField(value = selectedClass, onValueChange = { selectedClass = it }, label = "Classe", modifier = Modifier.weight(1f))
        }

        ElTextField(value = term, onValueChange = { term = it }, label = "Période / Trimestre", modifier = Modifier.fillMaxWidth())

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ElSectionHeader(title = "Évaluation: Amine Benali")

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElTextField(
                        value = devoir1Text,
                        onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull()?.let { it in 0.0..20.0 } == true) devoir1Text = value },
                        label = "Devoir 1 (/20)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    ElTextField(
                        value = devoir2Text,
                        onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull()?.let { it in 0.0..20.0 } == true) devoir2Text = value },
                        label = "Devoir 2 (/20)",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                ElTextField(
                    value = examenText,
                    onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull()?.let { it in 0.0..20.0 } == true) examenText = value },
                    label = "Examen (/20 - Coeff 2)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                ElCard(modifier = Modifier.fillMaxWidth(), accent = PrimaryBlue) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Moyenne Calculée", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "%.2f / 20".format(subjectAverage),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 28.sp),
                            color = PrimaryBlue,
                        )
                        Text("Formule: (D1 + D2 + (Examen × 2)) / 4", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        savedMessage?.let {
            ElAlertBanner(message = it, severity = ElAlertSeverity.Success)
        }

        ElButton(
            text = "Enregistrer le bulletin",
            onClick = { savedMessage = "Feuille de notes sauvegardée pour $selectedClass ($subject)!" },
            fullWidth = true,
        )
    }
}

// ── 3. Homework Push Engine ────────────────────────────────────────────────

@Composable
fun HomeworkPushScreen(session: Session) {
    var subject by remember { mutableStateOf("Physique-Chimie") }
    var targetClass by remember { mutableStateOf("COLG - 1AAM A") }
    var title by remember { mutableStateOf("Exercice 4 p. 52 - Lois d'Ohm") }
    var description by remember { mutableStateOf("Rédiger les réponses dans le cahier d'exercices et préparer la démonstration.") }
    var dueDate by remember { mutableStateOf("2026-08-05") }
    var photoAttached by remember { mutableStateOf(false) }
    var pushedMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElGradientStatCard(
            title = "Diffusion des Devoirs",
            value = targetClass,
            subtitle = "Publiez sur le portail élèves & parents",
            modifier = Modifier.fillMaxWidth(),
        )

        ElTextField(value = targetClass, onValueChange = { targetClass = it }, label = "Classe Cible", modifier = Modifier.fillMaxWidth())
        ElTextField(value = subject, onValueChange = { subject = it }, label = "Matière", modifier = Modifier.fillMaxWidth())
        ElTextField(value = title, onValueChange = { title = it }, label = "Titre du Devoir", modifier = Modifier.fillMaxWidth())
        ElTextField(value = description, onValueChange = { description = it }, label = "Consignes et Détails", modifier = Modifier.fillMaxWidth(), singleLine = false)
        ElTextField(value = dueDate, onValueChange = { dueDate = it }, label = "Date de Rendu (AAAA-MM-JJ)", modifier = Modifier.fillMaxWidth())

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Photo du Tableau", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                    Text(
                        if (photoAttached) "✓ Photo capturée (WebP)" else "Aucune photo jointe",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (photoAttached) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ElButton(
                    text = if (photoAttached) "Retirer" else "Capturer",
                    onClick = { photoAttached = !photoAttached },
                    style = ElButtonStyle.Secondary,
                    icon = Icons.Default.CameraAlt,
                )
            }
        }

        pushedMessage?.let {
            ElAlertBanner(message = it, severity = ElAlertSeverity.Success)
        }

        ElButton(
            text = "Diffuser le Devoir",
            onClick = { pushedMessage = "Devoir diffusé à $targetClass! Notification envoyée aux élèves et parents." },
            fullWidth = true,
            icon = Icons.Default.Send,
        )
    }
}

// ── 4. Classes Directory ───────────────────────────────────────────────────

@Composable
fun ClassesDirectoryScreen(session: Session) {
    val classes = listOf(
        Triple("PRIM - CP A", "Mme. Amrani", "28/30 Élèves"),
        Triple("PRIM - CE1 B", "M. Khelil", "25/30 Élèves"),
        Triple("COLG - 1AAM A", "Mme. Brahimi", "32/35 Élèves"),
        Triple("COLG - 4AM C", "M. Benaissa", "30/30 Élèves"),
        Triple("LYC - 3AS S", "M. Saidi", "22/25 Élèves"),
    )

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ElSectionHeader(title = "Annuaire des Classes")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(classes) { (name, teacher, capacity) ->
                ElListItem(
                    title = name,
                    subtitle = "Professeur: $teacher",
                    leading = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(PrimaryBlue.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Class, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                        }
                    },
                    trailing = {
                        ElTag(text = capacity, color = PrimaryBlue)
                    },
                )
            }
        }
    }
}