package com.example.ui.features.financials

import android.graphics.Bitmap
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.filled.Image
import androidx.lifecycle.ViewModel
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

    internal fun scaleIfNeeded(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
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
