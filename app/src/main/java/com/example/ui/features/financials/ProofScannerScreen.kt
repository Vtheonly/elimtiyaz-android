package com.example.ui.features.financials

import android.graphics.Bitmap
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.domain.repository.StorageBuckets
import com.example.domain.repository.StorageRepository
import com.example.session.SessionManager
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElTopBar
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.elDesignTokens
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

        if (bitmap.width < 640 || bitmap.height < 480) {
            _isLoading.value = false
            _error.value = "Résolution insuffisante (minimum 640x480)"
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
}

@Composable
fun ProofScannerScreen(
    onBack: () -> Unit,
    viewModel: ProofScannerViewModel = hiltViewModel(),
) {
    val isLoading by viewModel.isLoading.collectAsState()
    val uploadedPath by viewModel.uploadedPath.collectAsState()
    val error by viewModel.error.collectAsState()
    val tokens = elDesignTokens()

    Column(modifier = Modifier.fillMaxSize()) {
        ElTopBar(title = "Scanner une preuve", onBack = onBack)

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ElCard(modifier = Modifier.fillMaxWidth().height(300.dp), gradient = false) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(tokens.primaryBrush.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("Aperçu caméra", style = MaterialTheme.typography.bodyMedium)
                        Text("(CameraX integration required)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            uploadedPath?.let { path ->
                ElCard(modifier = Modifier.fillMaxWidth(), accent = SuccessGreen) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Preuve téléversée!", style = MaterialTheme.typography.titleMedium, color = SuccessGreen)
                        Text("Chemin: $path", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            ElButton(
                text = if (isLoading) "Téléversement..." else "Capturer & téléverser",
                onClick = { },
                enabled = !isLoading,
                loading = isLoading,
                fullWidth = true,
                icon = Icons.Default.CameraAlt,
            )
        }
    }
}