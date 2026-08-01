package com.example.domain.repository

import com.example.core.Result

/** Storage repository contract — Supabase Storage uploads + signed URLs. */
interface StorageRepository {
    suspend fun uploadProof(bucket: String, entityId: String, fileName: String, bytes: ByteArray, mimeType: String): Result<String>
    suspend fun createSignedUrl(bucket: String, path: String, expiresInSeconds: Long = 300): Result<String>
}

/** Canonical Supabase Storage bucket names — mirrors desktop `StorageBuckets`. */
object StorageBuckets {
    const val PAYMENT_PROOFS = "payment-proofs"
    const val EXPENSE_RECEIPTS = "expense-receipts"
    const val RECEIPTS = "receipts"
    const val STUDENT_DOCUMENTS = "student-documents"
    const val HOMEWORK_ATTACHMENTS = "homework-attachments"
    const val TASK_ATTACHMENTS = "task-attachments"
    const val CHAT_ATTACHMENTS = "chat-attachments"
    const val TENANT_ASSETS = "tenant-assets"
    const val AI_REPORTS = "ai-reports"
    const val IMPORT_REPORTS = "import-reports"
}
