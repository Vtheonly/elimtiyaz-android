package com.example.ui.features.academics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.core.Session
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElDropdown
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElGradientStatCard
import com.example.ui.components.ElTextField
import com.example.ui.theme.SuccessGreen
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun HomeworkPushScreen(
    session: Session,
    onNavigateToHomeworkPush: (String) -> Unit = {},
    viewModel: HomeworkPushViewModel = hiltViewModel(),
) {
    val classes by viewModel.classes.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()

    var selectedClassId by remember { mutableStateOf<String?>(null) }
    var selectedSubjectId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }
    var photoAttached by remember { mutableStateOf(false) }

    val academicYear = remember {
        val now = LocalDate.now()
        if (now.monthValue >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
    }

    androidx.compose.runtime.LaunchedEffect(classes) {
        if (selectedClassId == null && classes.isNotEmpty()) selectedClassId = classes.first().id
    }
    androidx.compose.runtime.LaunchedEffect(selectedClassId) {
        selectedClassId?.let { viewModel.loadSubjectsForClass(it) }
    }
    androidx.compose.runtime.LaunchedEffect(subjects) {
        if (selectedSubjectId == null && subjects.isNotEmpty()) selectedSubjectId = subjects.first().id
    }

    val selectedClass = classes.firstOrNull { it.id == selectedClassId }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ElGradientStatCard(
            title = "Diffusion des Devoirs",
            value = selectedClass?.name ?: "Sélectionnez une classe",
            subtitle = "Publiez sur le portail élèves & parents",
            modifier = Modifier.fillMaxWidth(),
        )

        if (classes.isEmpty()) {
            ElEmptyState(
                icon = Icons.Default.Class,
                title = "Aucune classe",
                message = "Créez d'abord une classe pour diffuser un devoir.",
            )
            return@Column
        }

        ElDropdown(
            label = "Classe Cible",
            selectedValue = selectedClass?.name ?: "",
            options = classes.map { it.name },
            onSelected = { name ->
                selectedClassId = classes.first { it.name == name }.id
                selectedSubjectId = null
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (subjects.isNotEmpty()) {
            ElDropdown(
                label = "Matière",
                selectedValue = selectedSubject?.name ?: "",
                options = subjects.map { it.name },
                onSelected = { name -> selectedSubjectId = subjects.first { it.name == name }.id },
                modifier = Modifier.fillMaxWidth(),
            )
        }

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

        message?.let {
            ElAlertBanner(
                message = it,
                severity = if (it.startsWith("Devoir diffusé")) ElAlertSeverity.Success else ElAlertSeverity.Warning,
            )
        }

        ElButton(
            text = "Diffuser le Devoir",
            onClick = {
                val cid = selectedClassId ?: return@ElButton
                val sid = selectedSubjectId ?: return@ElButton
                viewModel.pushHomework(
                    classId = cid,
                    subjectId = sid,
                    title = title.ifBlank { "Devoir" },
                    description = description,
                    dueDate = dueDate,
                    attachments = if (photoAttached) listOf("photo_tableau.webp") else emptyList(),
                    academicYear = academicYear,
                    actorId = session.userId,
                    actorName = session.displayName,
                )
            },
            fullWidth = true,
            icon = Icons.Default.Send,
            enabled = !busy && selectedClassId != null && selectedSubjectId != null && title.isNotBlank(),
        )
    }
}
