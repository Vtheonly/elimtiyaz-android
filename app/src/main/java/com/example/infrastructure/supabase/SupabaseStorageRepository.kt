package com.example.infrastructure.supabase

import com.example.core.Errors
import com.example.core.Result
import com.example.domain.repository.StorageRepository
import io.github.jan.supabase.storage.storage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Storage repository — handles uploads to private Supabase buckets.
 *
 * Path convention enforced by RLS:
 *   <tenant_id>/<entity_id>/<filename>
 *
 * The first path segment MUST match the caller's tenant_id (resolved
 * server-side from the JWT via current_tenant_id()). Violating this
 * results in a 403 from the storage RLS policy.
 *
 * Camera images are compressed to WebP (quality 85, max 1920x1080) before
 * upload — see [com.example.ui.features.financials.ProofScannerViewModel].
 * Images below 640x480 are rejected.
 */
@Singleton
class SupabaseStorageRepository @Inject constructor(
    private val provider: SupabaseClientProvider,
    private val sessionProvider: com.example.session.SessionManager,
) : StorageRepository {

    override suspend fun uploadProof(
        bucket: String,
        entityId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): Result<String> = try {
        val tenantId = sessionProvider.current()?.tenantId
            ?: return Result.Err(Errors.unauthorized("No session — cannot upload"))

        val path = "$tenantId/$entityId/$fileName"

        provider.storage.from(bucket).upload(path, bytes) {
            upsert = false
            contentType = mimeType
        }

        Result.Ok(path)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }

    override suspend fun createSignedUrl(bucket: String, path: String, expiresInSeconds: Long): Result<String> = try {
        val url = provider.storage.from(bucket).createSignedUrl(path, expiresInSeconds)
        Result.Ok(url)
    } catch (e: Exception) {
        Result.Err(Errors.fromException(e))
    }
}
