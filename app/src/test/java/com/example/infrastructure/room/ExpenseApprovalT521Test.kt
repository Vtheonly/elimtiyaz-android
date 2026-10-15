package com.example.infrastructure.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-521 (issue #29 / WORKFORCE-519) — the mobile approve/reject repair pins.
 *
 *  a. THE MAPPER (a direct unit test — the v20 columns proofUploadedBy /
 *     proofUploadedAt / anomalyNote were written by settleProof and the pull
 *     but NEVER read back: LocalMappers.toDomain dropped them, so the detail
 *     timeline's 4th stage could never render as reached).
 *  b. THE GATES (source-scan): canApprove/canDisburse/canSettleProof are
 *     COLLECTED STATE derived from the session flow — the T-492 plain-getter
 *     class rendered the buttons disabled whenever the detail composed
 *     against a not-yet-restored session.
 *  c. THE PULL PROTECTION (source-scan): pullExpenses skips rows with a
 *     PENDING local transition — the old unconditional upsertAll silently
 *     reverted a locally-decided expense when its transition push was
 *     server-rejected (the "approve works, then reverts on reopen" defect).
 *  d. THE PROOF CAPTURE LOOP (source-scan — T-545 / WORKFORCE-522
 *     evolution of the T-521 contract): the settle dialog captures
 *     IN-CONTEXT (Take Photo / Galerie — no scanner route, no navigation):
 *     the ONE upload engine (ProofUploadViewModel) uploads to the
 *     expense-receipts bucket under the expense id and the uploaded path
 *     auto-fills the proof field (the manual re-typing loop stays dead).
 */
class ExpenseApprovalT521Test {

    private fun repoFile(relative: String): File {
        val f = File("src/main/java/com/example/$relative")
        assertTrue("missing source file: $f", f.exists())
        return f
    }

    // ─── a. the mapper (direct) ──────────────────────────────────────────

    @Test
    fun `toDomain maps the v20 proof attribution and anomaly note columns`() {
        val entity = ExpenseEntity(
            id = "exp-1", tenantId = "t1", requestCode = "EXP-2026-001",
            title = "ddd", description = "test", amount = 500_000L,
            category = "other", payee = "X", status = "settled",
            submittedBy = "usr-1", submittedByName = "Karim",
            submittedAt = "2026-10-13T08:00:00Z",
            approvedBy = "usr-2", approvedAt = "2026-10-13T09:00:00Z",
            disbursedAt = "2026-10-13T10:00:00Z", settledAt = "2026-10-13T11:00:00Z",
            proofUrl = "t1/exp-1/proof.webp", urgency = "medium",
            anomalyScore = 0.9, notes = "note",
            createdAt = "2026-10-13T08:00:00Z", updatedAt = "2026-10-13T11:00:00Z",
            finalSpentAmount = 480_000L,
            proofUploadedBy = "usr-2",
            proofUploadedAt = "2026-10-13T11:00:00Z",
            anomalyNote = "Montant inhabituel pour la catégorie",
        )
        val domain = LocalMappers.run { entity.toDomain() }
        // The 4th timeline stage's data (the T-521c fix — previously dropped).
        assertEquals("usr-2", domain.proofUploadedBy)
        assertEquals("2026-10-13T11:00:00Z", domain.proofUploadedAt)
        assertEquals("Montant inhabituel pour la catégorie", domain.anomalyNote)
        // The previously-mapped fields stay intact.
        assertEquals("t1/exp-1/proof.webp", domain.proofUrl)
        assertEquals(480_000L, domain.finalSpentAmount)
        assertNotNull(domain.id)
    }

    // ─── b. the permission gates (source-scan) ──────────────────────────

    @Test
    fun `the approval gates are collected state not plain getters`() {
        val src = repoFile("ui/features/financials/ExpenseApprovalScreen.kt").readText()
        // The T-492 fix pattern: stateIn over the session flow.
        assertTrue(
            "canApprove must be a StateFlow derived from sessionManager.state (the T-492 collected-state rule)",
            src.contains("val canApprove: StateFlow<Boolean> = sessionManager.state"),
        )
        assertTrue(src.contains("val canDisburse: StateFlow<Boolean> = sessionManager.state"))
        assertTrue(src.contains("val canSettleProof: StateFlow<Boolean> = sessionManager.state"))
        // The detail call site COLLECTS the flows (never a plain property read).
        assertTrue(src.contains("viewModel.canApprove.collectAsState().value"))
        // The plain-getter class is gone.
        assertTrue(
            "the T-492 plain-getter class must not return",
            !src.contains("val canApprove: Boolean"),
        )
    }

    // ─── c. the pull protection (source-scan) ───────────────────────────

    @Test
    fun `pullExpenses protects rows with pending local transitions`() {
        val src = repoFile("infrastructure/sync/PullSyncRepository.kt").readText()
        assertTrue(
            "the pending-push protection must consult the sync queue",
            src.contains("db.syncQueueDao().listPending()"),
        )
        assertTrue(src.contains("it.entity == \"expense\""))
        // The upsert is FILTERED, never unconditional.
        assertTrue(src.contains("rows.filter { it.id !in pendingExpenseKeys }"))
    }

    // ─── d. the proof capture loop (source-scan — the T-545 in-context form) ─

    @Test
    fun `the settle dialog captures in-context and auto-fills the uploaded path`() {
        // The ONE upload engine survives the scanner retirement (renamed,
        // same contract: webp compress → tenant-path canon → Storage upload).
        val engine = repoFile("ui/features/financials/ProofUploadViewModel.kt").readText()
        assertTrue(
            "the upload engine must still exist (renamed from ProofScannerViewModel)",
            engine.contains("class ProofUploadViewModel"),
        )

        // The settle dialog captures IN-CONTEXT: the expense-receipts bucket
        // under the expense id (the T-521 path canon) with NO navigation.
        val approval = repoFile("ui/features/financials/ExpenseApprovalScreen.kt").readText()
        assertTrue(
            "the in-context capture must upload to the expense-receipts bucket",
            approval.contains("StorageBuckets.EXPENSE_RECEIPTS"),
        )
        assertTrue(
            "the Take Photo option must exist inside the settle dialog",
            approval.contains("Prendre une photo"),
        )
        assertTrue(
            "the gallery option must exist inside the settle dialog",
            approval.contains("Galerie"),
        )
        assertTrue(
            "the uploaded path must auto-fill the proof field (no manual re-typing)",
            approval.contains("proofUploadedPath?.let { proofPath = it }"),
        )

        // The standalone scanner surface is GONE: no route, no nav-result,
        // no scanner screen file referenced by navigation.
        val routes = repoFile("ui/navigation/Routes.kt").readText()
        assertFalse(
            "the ProofScanner route must be retired",
            routes.contains("data class ProofScanner"),
        )
        val nav = repoFile("ui/navigation/AppNavHost.kt").readText()
        assertFalse(
            "the nav-result loop must be retired (in-context now)",
            nav.contains("t521_scanned_proof_path"),
        )
        assertFalse(
            "the scanner screen must not be referenced by navigation",
            nav.contains("ProofScannerScreen"),
        )
    }
}
