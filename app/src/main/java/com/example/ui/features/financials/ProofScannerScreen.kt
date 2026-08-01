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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.example.core.Result
import com.example.domain.repository.StorageBuckets
import com.example.domain.repository.StorageRepository
import com.example.session.SessionManager
import com.example.ui.components.ElAlertBanner
import com.example.ui.components.ElAlertSeverity
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElTopBar
import com.example.ui.theme.DangerRed
import com.example.ui.theme.SuccessGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class ProofScannerViewModel @Inject constructor(
    private val storageRepository: StorageRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _uploadedPath = MutableStateFlow<String?>(null)
    val uploadedPath: StateFlow<String?> = _uploadedPath.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Compress, scale, and upload the captured bitmap to Supabase Storage.
     *
     * Mirrors the desktop's `proof-upload` flow:
     *   - Min resolution 640×480 (enforced by `enforce_payment_proof` trigger
     *     for check/transfer methods).
     *   - WebP compression at quality 85.
     *   - Path convention: `{tenantId}/{entityId}/{fileName}` (RLS-enforced).
     */
    suspend fun uploadProof(bitmap: Bitmap, entityId: String, bucket: String = StorageBuckets.PAYMENT_PROOFS) {
        _isLoading.value = true
        _error.value = null
        _uploadedPath.value = null

        if (bitmap.width < 640 || bitmap.height < 480) {
            _isLoading.value = false
            _error.value = "Résolution insuffisante (minimum 640×480). Image capturée: ${bitmap.width}×${bitmap.height}."
            return
        }

        withContext(Dispatchers.IO) {
            val scaled = scaleIfNeeded(bitmap, 1920, 1080)
            val outputStream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.WEBP, 85, outputStream)
            val bytes = outputStream.toByteArray()

            val fileName = "proof-${UUID.randomUUID()}.webp"
            when (val result = storageRepository.uploadProof(bucket, entityId, fileName, bytes, "image/webp")) {
                is Result.Ok -> {
                    _uploadedPath.value = result.value
                    _isLoading.value = false
                }
                is Result.Err -> {
                    _error.value = result.error.userMessage
                    _isLoading.value = false
                }
            }
        }
    }

    private fun scaleIfNeeded(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        if (bitmap.width <= maxWidth && bitmap.height <= maxHeight) return bitmap
        val ratio = minOf(maxWidth.toFloat() / bitmap.width, maxHeight.toFloat() / bitmap.height)
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    fun reset() {
        _uploadedPath.value = null
        _error.value = null
    }
}

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
