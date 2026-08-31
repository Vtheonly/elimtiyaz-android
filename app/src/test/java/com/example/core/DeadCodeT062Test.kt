package com.example.core

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * T-062 regression suite — Android dead-code removal.
 *
 * DEAD-008: `infrastructure/stub/StubRepositories.kt` (a 2-line comment-only
 * placeholder) — deleted.
 * DEAD-009: the 833-line design-system gallery showcase (ElGalleryActivity +
 * ElGalleryScreen + GallerySection + 5 tabs), unreachable from production
 * (never declared in the manifest, only self-referenced) — deleted. The
 * design-system CORE (components/foundation/theme/overlays) is still used
 * by real screens and is untouched.
 * DEAD-007: AuditActions trimmed to the constants actually referenced
 * (6 of 82) after a per-constant reachability scan; the wire-protocol KDoc
 * now points at the desktop registry as the canonical list.
 * DRIFT-007: the SupabaseModule KDoc no longer claims remote sync is
 * "future work needing a @Binds swap" — it is already wired via
 * SyncSupport.enqueueOnly.
 */
class DeadCodeT062Test {

    @Test
    fun `the stub placeholder file is gone`() {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val inModule = File(cwd, "src/main/java/com/example/infrastructure/stub/StubRepositories.kt")
        val inRepoRoot = File(cwd.parentFile ?: cwd, "src/main/java/com/example/infrastructure/stub/StubRepositories.kt")
        assertFalse(inModule.exists() || inRepoRoot.exists())
    }

    @Test
    fun `the unreachable gallery showcase is gone`() {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        for (root in listOf(cwd, cwd.parentFile ?: cwd)) {
            val gallery = File(root, "src/main/java/com/example/ui/designsystem/gallery")
            assertFalse("the gallery showcase directory must not exist", gallery.exists())
        }
    }

    @Test
    fun `the design system core is still present (only the showcase was removed)`() {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val inModule = File(cwd, "src/main/java/com/example/ui/designsystem/ElDesignSystem.kt")
        val inRepoRoot = File(cwd.parentFile ?: cwd, "src/main/java/com/example/ui/designsystem/ElDesignSystem.kt")
        assertTrue(inModule.exists() || inRepoRoot.exists())
    }

    @Test
    fun `AuditActions declares only referenced constants`() {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val path = listOf(cwd, cwd.parentFile ?: cwd)
            .map { File(it, "src/main/java/com/example/core/AuditActions.kt") }
            .firstOrNull { it.exists() } ?: error("AuditActions.kt not found")
        val src = path.readText()
        val declared = Regex("const val (\\w+)").findAll(src).map { it.groupValues[1] }.toList()
        // The six reachability-verified constants.
        assertTrue(declared.containsAll(listOf("AUTH_LOGIN", "AUTH_LOGOUT", "AUTH_PASSWORD_CHANGE", "SUBJECT_CREATE", "SUBJECT_UPDATE", "SYNC_PUSH_FAIL")))
        // The never-referenced families are gone.
        for (gone in listOf("BACKUP_CREATED", "WORKFLOW_PUBLISHED", "AI_NARRATIVE_DRAFTED", "OVERDUE_SCAN_RUN", "MATERIALIZED_VIEWS_REFRESH", "SERVER_SECRET_UPDATE", "SYNC_CONFLICT", "ACCOUNT_APPROVAL_APPROVE")) {
            assertFalse("$gone must be trimmed (unreferenced)", declared.contains(gone))
        }
        // The wire-protocol pointer to the desktop registry is kept.
        assertTrue(src.contains("audit-actions.ts"))
    }

    @Test
    fun `the SupabaseModule KDoc tells the truth about sync wiring`() {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val path = listOf(cwd, cwd.parentFile ?: cwd)
            .map { File(it, "src/main/java/com/example/di/SupabaseModule.kt") }
            .firstOrNull { it.exists() } ?: error("SupabaseModule.kt not found")
        val src = path.readText()
        assertFalse(
            "the stale 'future remote sync via @Binds swap' claim must be gone",
            src.contains("Future remote sync") || src.contains("@Binds\" declarations in"),
        )
        assertTrue(
            "the corrected comment must name the real wiring (SyncSupport / enqueueOnly)",
            src.contains("SyncSupport") && src.contains("enqueueOnly"),
        )
    }
}
