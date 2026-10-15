package com.example.ui.features.financials

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-545 / WORKFORCE-522 (the 181st session — the owner's CLARIFIED scope) —
 * the scanner-simplification pins. The owner's words: "I simply meant a
 * camera option that allows users to take a picture of an object or
 * document and attach it directly … Do not create a separate scanner
 * screen, module, or navigation entry. Reuse the existing attachment
 * workflow and keep the implementation lightweight."
 *
 * These source-scans pin the delivered contract:
 *  a. THE HUB TAB CENSUS — the Financials hub has FIVE tabs and "Scanner"
 *     is not one of them (the standalone section is dead).
 *  b. THE CHAT COMPOSER — the existing attachment interface gains the
 *     Take Photo option (camera button + capture → the EXISTING
 *     pendingAttachments workflow + a preview strip), while the file
 *     picker (images/PDFs, 10 Mo) is PRESERVED verbatim.
 *  c. THE STANDALONE SURFACE CENSUS — no Routes.ProofScanner, no
 *     composable route block, no MainScreen navigation param, no
 *     ProofScannerScreen file at all.
 *  d. THE SETTLE DIALOG — the capture is in-context (Take Photo +
 *     Galerie inside the dialog, the ProofUploadViewModel engine, the
 *     path auto-fill) — covered in depth by ExpenseApprovalT521Test item d.
 *  e. THE HOMEWORK PRECEDENT — the pre-existing in-form camera capture
 *     (the pattern this task generalised) is untouched.
 */
class ScannerIntegrationT545Test {

    private fun repoFile(relative: String): File {
        val f = File("src/main/java/com/example/$relative")
        assertTrue("missing source file: $f", f.exists())
        return f
    }

    // ─── a. the hub tab census ──────────────────────────────────────────

    @Test
    fun `the financials hub has no Scanner tab`() {
        val hub = repoFile("ui/features/financials/FinancialsHubScreen.kt").readText()
        assertTrue(
            "the hub's tab list must be the five canonical tabs",
            hub.contains("""listOf("Encaissements", "Tranches", "Créances", "Dépenses", "Journal")"""),
        )
        assertFalse(
            "the standalone Scanner tab must be gone from the tabs list",
            hub.contains("\"Scanner\""),
        )
        assertFalse(
            "the inline ProofScannerScreen render must be gone",
            hub.contains("ProofScannerScreen"),
        )
        assertFalse(
            "the dead onNavigateToProofScanner param must be gone from the hub",
            hub.contains("onNavigateToProofScanner"),
        )
    }

    // ─── b. the chat composer's Take Photo option ───────────────────────

    @Test
    fun `the chat composer offers Take Photo through the existing attachment workflow`() {
        val chat = repoFile("ui/features/chat/ChatDetailScreen.kt").readText()
        // The camera option exists in the composer row.
        assertTrue(
            "the camera button must be part of the composer",
            chat.contains("Icons.Default.CameraAlt"),
        )
        assertTrue(
            "the camera button must advertise the photo attach action",
            chat.contains("Prendre une photo et la joindre"),
        )
        // The capture feeds the EXISTING workflow (not a parallel one).
        assertTrue(
            "the capture must enter the existing pendingAttachments set",
            chat.contains("viewModel.addPendingAttachment("),
        )
        // The preview strip exists and is derived from the pending set.
        assertTrue(
            "the preview strip must render while the capture is still pending",
            chat.contains("capturedPreviewStillPending"),
        )
        // The existing picker is PRESERVED (images/PDFs, the 10 Mo contract).
        assertTrue(
            "the existing file picker must stay",
            chat.contains("pickFile.launch(\"*/*\")"),
        )
        assertTrue(
            "the existing picker affordance text must stay (10 Mo max)",
            chat.contains("Joindre une image ou un PDF (10 Mo max)"),
        )
        // The runtime camera permission is requested (no manifest-only gap).
        assertTrue(
            "the camera permission launcher must exist",
            chat.contains("cameraPermissionLauncher.launch(Manifest.permission.CAMERA)"),
        )
    }

    // ─── c. the standalone surface census ───────────────────────────────

    @Test
    fun `the standalone scanner surface is fully retired`() {
        // The route class is gone.
        val routes = repoFile("ui/navigation/Routes.kt").readText()
        assertFalse(
            "Routes.ProofScanner must not exist",
            routes.contains("data class ProofScanner"),
        )
        assertFalse(
            "the route-permission mapping entry must be gone",
            routes.contains("Routes.ProofScanner::class"),
        )
        // The navigation wiring is gone.
        val nav = repoFile("ui/navigation/AppNavHost.kt").readText()
        assertFalse(
            "the composable route block must be gone",
            nav.contains("composable<Routes.ProofScanner>"),
        )
        assertFalse(nav.contains("ProofScannerScreen"))
        // The MainScreen dead param is gone.
        val main = repoFile("ui/features/main/MainScreen.kt").readText()
        assertFalse(
            "the dead MainScreen param must be gone",
            main.contains("onNavigateToProofScanner: () -> Unit"),
        )
        // The standalone screen FILE is deleted.
        assertFalse(
            "the standalone scanner screen file must not exist",
            File("src/main/java/com/example/ui/features/financials/ProofScannerScreen.kt").exists(),
        )
    }

    // ─── d. the upload engine survives (renamed, same contract) ─────────

    @Test
    fun `the one upload engine survives as ProofUploadViewModel`() {
        val engine = repoFile("ui/features/financials/ProofUploadViewModel.kt").readText()
        assertTrue(engine.contains("class ProofUploadViewModel"))
        // The T-362/UPLOAD-103 tenant-path canon survives verbatim.
        assertTrue(engine.contains("storageRepository.uploadProof(bucket, tenantId, entityId, fileName, bytes, \"image/webp\")"))
        // The old name is retired.
        assertFalse(engine.contains("class ProofScannerViewModel"))
    }

    // ─── e. the settle dialog + the homework precedent ──────────────────

    @Test
    fun `the settle dialog and the homework capture keep their camera options`() {
        val approval = repoFile("ui/features/financials/ExpenseApprovalScreen.kt").readText()
        assertTrue(approval.contains("\"Prendre une photo\""))
        assertTrue(approval.contains("\"Galerie\""))
        // The homework form's pre-existing camera capture is untouched.
        val homework = repoFile("ui/features/academics/HomeworkPushScreen.kt").readText()
        assertTrue(
            "the homework camera capture (the precedent) must stay",
            homework.contains("\"Caméra\""),
        )
        assertTrue(homework.contains("\"Galerie\""))
    }
}
