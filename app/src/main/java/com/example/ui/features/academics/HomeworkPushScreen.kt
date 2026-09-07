package com.example.ui.features.academics

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
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
import java.io.File
import java.time.LocalDate

@Composable
fun HomeworkPushScreen(
    session: Session,
    onNavigateToHomeworkPush: (String) -> Unit = {},
    initialClassId: String? = null,
    onBack: (() -> Unit)? = null,
    viewModel: HomeworkPushViewModel = hiltViewModel(),
) {
    val classes by viewModel.classes.collectAsState()
    val subjects by viewModel.subjects.collectAsState()
    val busy by viewModel.busy.collectAsState()
    val message by viewModel.message.collectAsState()
    val context = LocalContext.current

    var selectedClassId by remember { mutableStateOf<String?>(initialClassId) }
    var selectedSubjectId by remember { mutableStateOf<String?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(LocalDate.now().plusDays(7).toString()) }

    var capturedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    var capturedPhotoName by remember { mutableStateOf<String?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success: Boolean ->
        if (success) {
            photoFile?.let { file ->
                capturedPhotoName = file.name
            }
        } else {
            capturedPhotoUri = null
            capturedPhotoName = null
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted: Boolean ->
        if (granted) {
            val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(cameraDir, "homework_board_${System.currentTimeMillis()}.jpg")
            photoFile = file
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                capturedPhotoUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur caméra : ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Permission caméra requise pour photographier le tableau", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            capturedPhotoUri = uri
            capturedPhotoName = "tableau_${System.currentTimeMillis()}.jpg"
        }
    }

    val academicYear = remember {
        val now = LocalDate.now()
        if (now.monthValue >= 9) "${now.year}-${now.year + 1}" else "${now.year - 1}-${now.year}"
    }

    LaunchedEffect(classes) {
        if (selectedClassId == null && classes.isNotEmpty()) selectedClassId = classes.first().id
    }
    LaunchedEffect(selectedClassId) {
        selectedClassId?.let { viewModel.loadSubjectsForClass(it) }
    }
    LaunchedEffect(subjects) {
        if (selectedSubjectId == null && subjects.isNotEmpty()) selectedSubjectId = subjects.first().id
    }

    val selectedClass = classes.firstOrNull { it.id == selectedClassId }
    val selectedSubject = subjects.firstOrNull { it.id == selectedSubjectId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (onBack != null) {
            com.example.ui.components.ElTopBar(
                title = "Diffusion de devoir — ${selectedClass?.name ?: "…"}",
                onBack = onBack,
            )
        }
        ElGradientStatCard(
            title = "Diffusion des Devoirs",
            value = selectedClass?.name ?: "Sélectionnez une classe",
            subtitle = "Publiez directement sur le portail élèves & parents",
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

        ElTextField(value = title, onValueChange = { title = it }, label = "Titre du Devoir *", modifier = Modifier.fillMaxWidth())
        ElTextField(value = description, onValueChange = { description = it }, label = "Consignes et Détails", modifier = Modifier.fillMaxWidth(), singleLine = false)
        ElTextField(value = dueDate, onValueChange = { dueDate = it }, label = "Date de Rendu (AAAA-MM-JJ) *", modifier = Modifier.fillMaxWidth())

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Photo du Tableau / Support", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                        Text(
                            if (capturedPhotoName != null) "✓ $capturedPhotoName" else "Aucune photo jointe",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (capturedPhotoName != null) SuccessGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (capturedPhotoName != null) {
                        ElButton(
                            text = "Retirer",
                            onClick = {
                                capturedPhotoUri = null
                                capturedPhotoName = null
                                photoFile = null
                            },
                            style = ElButtonStyle.Secondary,
                        )
                    }
                }
                capturedPhotoUri?.let { uri ->
                    AsyncImage(
                        model = uri,
                        contentDescription = "Photo du tableau",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElButton(
                        text = "Caméra",
                        onClick = {
                            val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            if (hasPerm) {
                                val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
                                val file = File(cameraDir, "homework_board_${System.currentTimeMillis()}.jpg")
                                photoFile = file
                                try {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    capturedPhotoUri = uri
                                    cameraLauncher.launch(uri)
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Erreur caméra : ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        style = ElButtonStyle.Secondary,
                        icon = Icons.Default.CameraAlt,
                        modifier = Modifier.weight(1f),
                    )
                    ElButton(
                        text = "Galerie",
                        onClick = { galleryLauncher.launch("image/*") },
                        style = ElButtonStyle.Secondary,
                        icon = Icons.Default.Image,
                        modifier = Modifier.weight(1f),
                    )
                }
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
                    attachments = capturedPhotoName?.let { listOf(it) } ?: emptyList(),
                    academicYear = academicYear,
                    actorId = session.userId,
                    actorName = session.displayName,
                )
            },
            fullWidth = true,
            icon = Icons.Default.Send,
            enabled = !busy && selectedClassId != null && selectedSubjectId != null && title.isNotBlank(),
        )

        Spacer(Modifier.height(80.dp))
    }
}