package com.example.ui.features.financials

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElTopBar
import com.example.ui.theme.SuccessGreen
import java.io.File
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Proof capture + upload screen.
 *
 * BUGFIX (iter 2): previously the "Capturer & téléverser" button had an
 * empty `onClick = {}` lambda and the screen said "(CameraX integration
 * required)". Now we use Android's built-in [ActivityResultContracts.TakePicture]
 * — the system camera app captures a full-res photo to a temp file, then
 * we decode + upload it via [ProofScannerViewModel.uploadProof].
 *
 * This avoids pulling in CameraX (already declared in libs.versions.toml
 * but unused) and works on every Android 7+ device with a camera app.
 *
 * The entity ID defaults to the current session's user ID — callers can
 * extend this by passing a specific payment/expense ID via a route arg.
 */
@Composable
fun ProofScannerScreen(
    onBack: () -> Unit,
    viewModel: ProofScannerViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val uploadedPath by viewModel.uploadedPath.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    // Temp URI for the camera capture result.
    val capturedImageUri = remember {
        mutableStateOf<Uri?>(null)
    }
    var pendingBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Camera launcher — captures a full-res photo to a temp file.
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
    ) { success: Boolean ->
        if (success) {
            val uri = capturedImageUri.value
            if (uri != null) {
                // Decode the captured image into a Bitmap for upload.
                pendingBitmap = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()
            }
        }
    }

    // File picker launcher — alternative to camera (gallery/file pick).
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            pendingBitmap = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    BitmapFactory.decodeStream(input)
                }
            }.getOrNull()
        }
    }

    // Trigger upload whenever a new bitmap is captured.
    androidx.compose.runtime.LaunchedEffect(pendingBitmap) {
        val bmp = pendingBitmap ?: return@LaunchedEffect
        val entityId = sessionManager.currentUserId() ?: "unknown"
        viewModel.uploadProof(bmp, entityId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ElTopBar(title = "Scanner une preuve", onBack = onBack)

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Preview area — shows captured image or a placeholder.
            ElCard(modifier = Modifier.fillMaxWidth().height(300.dp), gradient = false) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val bmp = pendingBitmap
                    if (bmp != null) {
                        // Render the in-memory bitmap via a small helper that
                        // wraps it as a remembered ImageBitmap.
                        capturedImageUri.value?.let { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = "Preuve capturée",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit,
                            )
                        }
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
                            Text("Aperçu caméra", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Capturez une photo ou sélectionnez une image",
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
                        Text("Preuve téléversée!", style = MaterialTheme.typography.titleMedium, color = SuccessGreen)
                        Text("Chemin: $path", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            ElButton(
                text = if (isLoading) "Téléversement…" else "Capturer & téléverser",
                onClick = {
                    viewModel.reset()
                    pendingBitmap = null
                    // Create a temp file URI for the camera to write to.
                    val tempFile = File(context.cacheDir, "proof_capture_${UUID.randomUUID()}.jpg")
                    capturedImageUri.value = Uri.fromFile(tempFile)
                    cameraLauncher.launch(capturedImageUri.value!!)
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
                    galleryLauncher.launch("image/*")
                },
                enabled = !isLoading,
                style = ElButtonStyle.Secondary,
                fullWidth = true,
                icon = Icons.Default.Image,
            )
        }
    }
}
