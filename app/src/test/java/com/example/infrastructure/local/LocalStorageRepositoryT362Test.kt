package com.example.infrastructure.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.domain.repository.StorageBuckets
import com.example.infrastructure.supabase.SupabaseClientProvider
import com.example.infrastructure.sync.OnlineDetector
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * T-362 / UPLOAD-103 — the tenant-scoped, honest-failure storage upload.
 *
 * The defects (live-proven 2026-09-14, 64th session — t-359-upload-e2e.py
 * check A + the hub evidence doc):
 *  1. `uploadProof` pushed `{entityId}/{fileName}` — folder[1] was the
 *     entity, but every storage.objects policy in the hub chain (0018/
 *     0043/0092) requires folder[1] = current_tenant_id(). EVERY remote
 *     upload was RLS-rejected ("new row violates row-level security
 *     policy").
 *  2. The read-oriented `NetworkTimeouts.guard` caught the RestException
 *     and returned null — the code fell back to the local `file://` URI and
 *     reported SUCCESS while the proof never reached the server (the
 *     CROSS-200 silent-write class reborn in the storage path). The
 *     ProofScanner screen even printed "Prêt pour la synchronisation
 *     Supabase Storage" under a local path.
 *  3. `DEFAULT_TIMEOUT_MS` (4 s) aborted real 10 MB proof uploads
 *     mid-flight on ordinary mobile networks — then misclassified the
 *     timeout as offline.
 *
 * Fix under test: the contract carries the tenant; the remote path is
 * `{tenantId}/{entityId}/{fileName}`; TRANSPORT failures keep the
 * sanctioned offline-first local fallback (classified by the SAME
 * canonical SyncErrorClassifier the sync pipeline uses) while PERMANENT
 * 4xx rejections surface as Result.Err; uploads get a dedicated 60 s
 * timeout; a null tenant fails closed before any remote call.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalStorageRepositoryT362Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val provider = SupabaseClientProvider(context)
    private val onlineDetector = OnlineDetector(context)

    // AuditContext is unused on the uploadProof path — a lazy that throws
    // if ever touched keeps the test honest about that.
    private val auditContext = AuditContext(dagger.Lazy { error("AuditContext must not be touched by uploadProof") })

    private val repo = LocalStorageRepository(auditContext, context, provider, onlineDetector)

    // ─── Behavioural (config-independent) ────────────────────────────────────

    @Test
    fun `objectPath builds the canonical tenant-scoped path`() {
        assertEquals(
            "tenant-1/payment-9/proof-42.webp",
            StorageBuckets.objectPath("tenant-1", "payment-9", "proof-42.webp"),
        )
    }

    @Test
    fun `a null tenant fails CLOSED before any remote call`() = runBlocking {
        val result = repo.uploadProof(
            bucket = StorageBuckets.PAYMENT_PROOFS,
            tenantId = null,
            entityId = "payment-9",
            fileName = "proof-42.webp",
            bytes = byteArrayOf(1, 2, 3),
            mimeType = "image/webp",
        )
        assertTrue("the fail-closed branch must return Err, got $result", result is com.example.core.Result.Err)
        val err = (result as com.example.core.Result.Err).error
        assertTrue(
            "the error must name the missing tenant (got: ${err.userMessage})",
            err.userMessage.contains("établissement", ignoreCase = true),
        )
    }

    // ─── Source pins (the whole defect family, config-independent) ──────────

    private fun repoSource(): String =
        File("src/main/java/com/example/infrastructure/local/LocalStorageRepository.kt").readText()

    private fun contractSource(): String =
        File("src/main/java/com/example/domain/repository/StorageRepository.kt").readText()

    private fun viewModelSource(): String =
        File("src/main/java/com/example/ui/features/financials/ProofScannerViewModel.kt").readText()

    @Test
    fun `the remote upload path is built through StorageBuckets_objectPath (never tenant-less)`() {
        val src = repoSource()
        assertTrue(
            "the remote path must come from the canonical builder",
            src.contains("StorageBuckets.objectPath(tenantId, entityId, fileName)"),
        )
        assertFalse(
            "the tenant-less upload pattern must be gone (UPLOAD-103's exact form)",
            src.contains("upload(\"\$entityId/\$fileName\""),
        )
    }

    @Test
    fun `the failure classification reuses SyncErrorClassifier (no catch-all guard on the upload)`() {
        val src = repoSource()
        assertTrue(
            "the transient/permanent decision must reuse the canonical classifier",
            src.contains("SyncErrorClassifier.isTransient(e, onlineDetector.isOnline())"),
        )
        assertFalse(
            "the read-oriented catch-all guard must NOT wrap the upload (the silent-failure source)",
            src.contains("NetworkTimeouts.guard(\n            \"storage.uploadProof\""),
        )
    }

    @Test
    fun `a PERMANENT server rejection surfaces as Result_Err (never a fake success)`() {
        val src = repoSource()
        assertTrue(src.contains("uploadProof rejected by Supabase Storage"))
    }

    @Test
    fun `uploads use the dedicated 60s timeout, not the 4s read default`() {
        val src = repoSource()
        assertTrue(src.contains("UPLOAD_TIMEOUT_MS"))
        assertTrue(src.contains("const val UPLOAD_TIMEOUT_MS: Long = 60_000L"))
        assertTrue(src.contains("withTimeout(UPLOAD_TIMEOUT_MS)"))
    }

    @Test
    fun `the contract carries the tenant parameter (desktop uploadPrivateMedia mirror)`() {
        val src = contractSource()
        assertTrue(src.contains("tenantId: String?,"))
        assertTrue(src.contains("fun objectPath(tenantId: String, entityId: String, fileName: String): String"))
    }

    @Test
    fun `the proof scanner passes the session's working tenant`() {
        val src = viewModelSource()
        assertTrue(src.contains("val tenantId = sessionManager.currentTenantId()"))
        assertTrue(src.contains("storageRepository.uploadProof(bucket, tenantId, entityId, fileName, bytes, \"image/webp\")"))
    }
}
