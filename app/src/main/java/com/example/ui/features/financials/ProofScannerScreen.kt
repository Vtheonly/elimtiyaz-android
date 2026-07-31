package com.example.ui.features.financials

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.repository.StorageBuckets
import com.example.domain.repository.StorageRepository
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import java.io.ByteArrayOutputStream
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Proof scanner — camera capture + upload to private Supabase bucket.
 *
 * Path convention enforced by RLS: <tenant_id>/<entity_id>/<filename>
 * Images below 640x480 are rejected (per plan §STORAGE_SETUP).
 * Compressed to WebP (quality 85, max 1920x1080) before upload.
 *
 * NOTE: Full CameraX integration requires the camera permission flow.
 * This implementation accepts a bitmap (from a future CameraX preview
 * use case) and handles the upload + path generation. The camera preview
 * UI itself is a separate concern — see the CameraX docs for the preview
 * use case + ImageCapture use case wiring.
 */
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

    suspend fun uploadProof(bitmap: Bitmap, entityId: String, bucket: String = StorageBuckets.PAYMENT_PROOFS) {
        _isLoading.value = true
        _error.value = null

        // Validate resolution (reject below 640x480)
        if (bitmap.width < 640 || bitmap.height < 480) {
            _isLoading.value = false
            _error.value = "Résolution insuffisante (minimum 640x480)"
            return
        }

        withContext(Dispatchers.IO) {
            // Compress to WebP (quality 85, max 1920x1080)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProofScannerScreen(
    onBack: () -> Unit,
    viewModel: ProofScannerViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val uploadedPath by viewModel.uploadedPath.collectAsState()
    val error by viewModel.error.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scanner une preuve") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, contentDescription = "Retour") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Camera preview placeholder — would be replaced with CameraX PreviewView
            Card(
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth().height(300.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.height(64.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Aperçu caméra", style = MaterialTheme.typography.bodyMedium)
                        Text("(CameraX integration required)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            uploadedPath?.let { path ->
                Card(elevation = CardDefaults.cardElevation(2.dp)) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Preuve téléversée!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Chemin: $path", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Button(
                onClick = {
                    // In a real implementation, this would trigger the CameraX ImageCapture use case
                    // and pass the resulting bitmap to viewModel.uploadProof(...).
                    // For now, this is a placeholder.
                },
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(if (isLoading) "Téléversement..." else "Capturer & téléverser")
            }
        }
    }
}
