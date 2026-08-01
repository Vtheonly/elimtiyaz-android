package com.example.ui.features.academics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

import com.example.core.Session
import com.example.ui.theme.DangerRed
import com.example.ui.theme.LightBlue
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.WarmGold
import kotlinx.coroutines.launch

import com.example.ui.components.ModernSecondaryTabRow

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RollCallScreen(session: Session) {
    var selectedClass by remember { mutableStateOf(SAMPLE_CLASSES[1]) }
    var classExpanded by remember { mutableStateOf(false) }
    
    // Student statuses map
    val statuses = remember { mutableStateMapOf<String, AttendanceStatus>() }
    val lateTimes = remember { mutableStateMapOf<String, String>() }
    var submitted by remember { mutableStateOf(false) }
    var alertMessage by remember { mutableStateOf<String?>(null) }

    // Init defaults
    SAMPLE_STUDENTS.forEach { student ->
        if (!statuses.containsKey(student.id)) {
            statuses[student.id] = AttendanceStatus.PRESENT
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Appel — 30 Secondes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Sélectionnez la classe et basculez les statuts des élèves.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        // Class selector
        ExposedDropdownMenuBox(
            expanded = classExpanded,
            onExpandedChange = { classExpanded = it },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = selectedClass,
                onValueChange = {},
                readOnly = true,
                label = { Text("Classe") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = classExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(
                expanded = classExpanded,
                onDismissRequest = { classExpanded = false },
            ) {
                SAMPLE_CLASSES.forEach { cls ->
                    DropdownMenuItem(
                        text = { Text(cls) },
                        onClick = {
                            selectedClass = cls
                            classExpanded = false
                        },
                    )
                }
            }
        }

        // Student roster cards
        SAMPLE_STUDENTS.forEach { student ->
            val currentStatus = statuses[student.id] ?: AttendanceStatus.PRESENT
            val isLate = currentStatus == AttendanceStatus.LATE
            val isThresholdReached = student.termAbsences + (if (currentStatus == AttendanceStatus.ABSENT) 1 else 0) >= 3

            Card(
                elevation = CardDefaults.cardElevation(2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isThresholdReached) DangerRed.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .padding(end = 10.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    student.name.take(2).uppercase(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                )
                            }
                            Column {
                                Text(student.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text("${student.termAbsences} absences ce trimestre", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        if (isThresholdReached) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Warning, contentDescription = "Alerte 3+ Absences", tint = DangerRed)
                                Spacer(Modifier.width(4.dp))
                                Text("Alerte 3+", style = MaterialTheme.typography.labelSmall, color = DangerRed)
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Status toggle row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AttendanceStatus.values().forEach { st ->
                            val isSelected = currentStatus == st
                            Button(
                                onClick = {
                                    statuses[student.id] = st
                                    if (st == AttendanceStatus.LATE && !lateTimes.containsKey(student.id)) {
                                        lateTimes[student.id] = "08:15"
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) st.color else MaterialTheme.colorScheme.surface,
                                    contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(st.label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    // Inline time picker if LATE
                    if (isLate) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Schedule, contentDescription = null, tint = LightBlue)
                            Spacer(Modifier.width(6.dp))
                            OutlinedTextField(
                                value = lateTimes[student.id] ?: "08:15",
                                onValueChange = { lateTimes[student.id] = it },
                                label = { Text("Heure d'arrivée") },
                                singleLine = true,
                                modifier = Modifier.width(160.dp),
                            )
                        }
                    }
                }
            }
        }

        alertMessage?.let {
            Card(
                colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.15f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Check, contentDescription = null, tint = SuccessGreen)
                    Spacer(Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = SuccessGreen)
                }
            }
        }

        Button(
            onClick = {
                submitted = true
                val thresholdCount = SAMPLE_STUDENTS.count { student ->
                    val status = statuses[student.id] ?: AttendanceStatus.PRESENT
                    (student.termAbsences + (if (status == AttendanceStatus.ABSENT) 1 else 0)) >= 3
                }
                alertMessage = "Appel enregistré avec succès! $thresholdCount élève(s) ayant atteint le seuil d'alerte des 3 absences ont été notifiés au portail parents."
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Default.Send, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Valider l'appel ($selectedClass)")
        }
    }
}

// ── 2. Mobile Grade Entry Engine ──────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
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

    // Real-time formula: (Devoir 1 + Devoir 2 + (Examen * 2)) / 4
    val subjectAverage = (d1 + d2 + (ex * 2)) / 4.0

    var savedMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Saisie des Notes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Calcule la moyenne en temps réel selon la formule officielle.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Matière") }, modifier = Modifier.weight(1f))
            OutlinedTextField(value = selectedClass, onValueChange = { selectedClass = it }, label = { Text("Classe") }, modifier = Modifier.weight(1f))
        }

        OutlinedTextField(value = term, onValueChange = { term = it }, label = { Text("Période / Trimestre") }, modifier = Modifier.fillMaxWidth())

        Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Évaluation Élève: Amine Benali", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = devoir1Text,
                        onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull()?.let { it in 0.0..20.0 } == true) devoir1Text = value },
                        label = { Text("Devoir 1 (/20)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = devoir2Text,
                        onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull()?.let { it in 0.0..20.0 } == true) devoir2Text = value },
                        label = { Text("Devoir 2 (/20)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = examenText,
                    onValueChange = { value -> if (value.isEmpty() || value.toDoubleOrNull()?.let { it in 0.0..20.0 } == true) examenText = value },
                    label = { Text("Examen (/20 - Coeff 2)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Computed Formula Preview Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Moyenne Calculée en Temps Réel", style = MaterialTheme.typography.labelSmall)
                        Text(
                            "%.2f / 20".format(subjectAverage),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "Formule: (D1 + D2 + (Examen × 2)) / 4",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }

        savedMessage?.let {
            Text(it, color = SuccessGreen, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = {
                savedMessage = "Feuille de notes sauvegardée avec succès pour $selectedClass ($subject)!"
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Text("Enregistrer le bulletin de notes")
        }
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
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Diffusion des Devoirs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Publiez les devoirs directement sur le portail élèves & parents.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(value = targetClass, onValueChange = { targetClass = it }, label = { Text("Classe Cible") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Matière") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Titre du Devoir") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Consignes et Détails") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Date de Rendu (AAAA-MM-JJ)") }, modifier = Modifier.fillMaxWidth())

        Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Photo du Tableau / Exercice", style = MaterialTheme.typography.titleMedium)
                    Text(if (photoAttached) "✓ Photo capturée et optimisée WebP" else "Aucune photo jointe", style = MaterialTheme.typography.bodySmall, color = if (photoAttached) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = { photoAttached = !photoAttached }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (photoAttached) "Retirer" else "Capturer")
                }
            }
        }

        pushedMessage?.let {
            Text(it, color = SuccessGreen, style = MaterialTheme.typography.bodyMedium)
        }

        Button(
            onClick = {
                pushedMessage = "Devoir diffusé instantanément à la classe $targetClass! Notification envoyée aux élèves et parents."
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Default.Send, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Diffuser le Devoir au Portail")
        }
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

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Annuaire des Classes", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
            items(classes) { (name, teacher, capacity) ->
                Card(elevation = CardDefaults.cardElevation(2.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Class, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Professeur Principal: $teacher", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(capacity, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

