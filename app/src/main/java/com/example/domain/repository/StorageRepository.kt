package com.example.domain.repository

import com.example.core.Result

/**
 * Storage repository contract — Supabase Storage uploads + signed URLs.
 *
 * T-362 / UPLOAD-103: [uploadProof] takes the caller's TENANT id — every
 * storage.objects policy in the canonical chain (hub migration 0018/0043/
 * 0092) requires folder[1] of the object path to equal the caller's
 * `current_tenant_id()`. The previous tenant-less contract produced paths
 * like `{entityId}/{fileName}` that were RLS-rejected on EVERY remote
 * upload (live RED proof: t-359-upload-e2e.py check A) while the caller
 * believed it succeeded.
 *
 * The canonical path convention (mirrors the desktop media-vault):
 * `{tenantId}/{entityId}/{fileName}`.
 */
interface StorageRepository {
    suspend fun uploadProof(
        bucket: String,
        tenantId: String?,
        entityId: String,
        fileName: String,
        bytes: ByteArray,
        mimeType: String,
    ): Result<String>

    suspend fun createSignedUrl(bucket: String, path: String, expiresInSeconds: Long = 300): Result<String>
}

/** Canonical Supabase Storage bucket names — mirrors desktop `StorageBuckets`. */
object StorageBuckets {
    const val PAYMENT_PROOFS = "payment-proofs"
    const val EXPENSE_RECEIPTS = "expense-receipts"

    @Deprecated("Dropped by hub migration 0079 (the orphaned receipts bucket) — never upload to it.")
    const val RECEIPTS = "receipts"
    const val STUDENT_DOCUMENTS = "student-documents"
    const val HOMEWORK_ATTACHMENTS = "homework-attachments"
    const val TASK_ATTACHMENTS = "task-attachments"
    const val CHAT_ATTACHMENTS = "chat-attachments"
    const val TENANT_ASSETS = "tenant-assets"
    const val AI_REPORTS = "ai-reports"
    const val IMPORT_REPORTS = "import-reports"

    /** The canonical storage upload path (T-362): `{tenant}/{entity}/{file}`. */
    fun objectPath(tenantId: String, entityId: String, fileName: String): String =
        "$tenantId/$entityId/$fileName"
}
