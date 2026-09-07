package com.example.ui.features.financials

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElTopBar
import com.example.ui.navigation.LocalSession
import com.example.ui.theme.SuccessGreen
import java.io.File

@Composable
fun ProofScannerScreen(
    onBack: () -> Unit,
    viewModel: ProofScannerViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val uploadedPath by viewModel.uploadedPath.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current
    val session = LocalSession.current

    var photoFile by remember { mutableStateOf<File?>(null) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success: Boolean ->
        if (success) {
            val file = photoFile
            if (file != null && file.exists()) {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    ?: photoUri?.let { uri ->
                        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
                    }
                pendingBitmap = bitmap
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted: Boolean ->
        if (granted) {
            val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
            val file = File(cameraDir, "proof_${System.currentTimeMillis()}.jpg")
            photoFile = file
            try {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                photoUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Erreur caméra : ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Toast.makeText(context, "Permission caméra nécessaire pour prendre une photo", Toast.LENGTH_SHORT).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            photoUri = uri
            pendingBitmap = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
    }

    androidx.compose.runtime.LaunchedEffect(pendingBitmap) {
        val bmp = pendingBitmap ?: return@LaunchedEffect
        val entityId = session?.userId ?: "guichet"
        viewModel.uploadProof(bmp, entityId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ElTopBar(title = "Scanner une preuve", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ElCard(modifier = Modifier.fillMaxWidth().height(280.dp), gradient = false) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (photoUri != null) {
                        AsyncImage(
                            model = photoUri,
                            contentDescription = "Preuve capturée",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Aperçu de la pièce", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                            Text(
                                "Chèque, virement bancaire ou reçu de dépense",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            error?.let { msg ->
                ElAlertBanner(message = msg, severity = ElAlertSeverity.Danger, title = "Erreur")
            }

            uploadedPath?.let { path ->
                ElCard(modifier = Modifier.fillMaxWidth(), accent = SuccessGreen) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Preuve capturée et enregistrée !", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = SuccessGreen)
                        Text("Chemin local : $path", style = MaterialTheme.typography.bodySmall)
                        Text("Prêt pour la synchronisation Supabase Storage.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            ElButton(
                text = if (isLoading) "Enregistrement…" else "Prendre une photo",
                onClick = {
                    viewModel.reset()
                    pendingBitmap = null
                    photoUri = null
                    val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    if (hasPerm) {
                        val cameraDir = File(context.cacheDir, "camera").apply { mkdirs() }
                        val file = File(cameraDir, "proof_${System.currentTimeMillis()}.jpg")
                        photoFile = file
                        try {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            photoUri = uri
                            cameraLauncher.launch(uri)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Erreur caméra : ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                enabled = !isLoading,
                loading = isLoading,
                fullWidth = true,
                icon = Icons.Default.CameraAlt,
            )

            ElButton(
                text = "Choisir depuis la galerie",
                onClick = {
                    viewModel.reset()
                    pendingBitmap = null
                    photoUri = null
                    galleryLauncher.launch("image/*")
                },
                enabled = !isLoading,
                style = ElButtonStyle.Secondary,
                fullWidth = true,
                icon = Icons.Default.Image,
            )

            Spacer(Modifier.height(80.dp))
        }
    }
}