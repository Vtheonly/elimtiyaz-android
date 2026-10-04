package com.example.infrastructure.sync

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-494 (DATA-059) — the wiring pins (the source-scan convention):
 *  1. the SEEDER GATE — the demo content runs only under
 *     `demoAllowed` (the T-002/SEC-101 demo-sandbox posture), the catalogs
 *     unconditionally;
 *  2. the EVICTION GATE — the eviction runs only on CONFIGURED builds
 *     (a demo-sandbox build must never delete the demo rows the seeder
 *     just created — its demo content IS its content);
 *  3. the RELEVE PULL — the canonical releve_entries stream is wired into
 *     doPullAll, paginated, with the personnel-name backfill.
 */
class DemoHygieneWiringT494Test {

    private fun src(path: String): String {
        val f = File(path)
        assertTrue("missing source file: $f", f.exists())
        return f.readText()
    }

    @Test
    fun `the seeder gates every demo seed behind the demo posture`() {
        val s = src("src/main/java/com/example/infrastructure/room/DatabaseSeeder.kt")
        // The posture parameter defaults to the T-002 policy.
        assertTrue(s.contains("demoAllowed: Boolean = com.example.infrastructure.local.AuthEnvironment.fromBuildConfig().isDemoFallbackAllowed()"))
        // The catalogs are unconditional.
        assertTrue(s.contains("seedPricing()\n            seedSubjects()\n            seedClasses()"))
        // Every demo seed is inside the demoAllowed branch.
        val gateBlock = s.substringAfter("if (demoAllowed) {").substringBefore("// ─── Pricing")
        for (seed in listOf("seedPersonnel()", "seedDemoFamilies()", "seedRouting()", "seedReleveEntries()")) {
            assertTrue("$seed must be demo-only", gateBlock.contains(seed))
        }
        // The academic history is demo-only too (fabricated grades for real
        // students on a configured build — the DATA-059 class).
        val historyBlock = s.substringAfter("if (demoAllowed) {\n            seedAssessments()")
        assertTrue(historyBlock.contains("seedAttendanceHistory()"))
        // The demo-id vocabulary exists for the eviction.
        assertTrue(s.contains("object DemoSeedIds"))
    }

    @Test
    fun `the eviction runs only on configured builds`() {
        val s = src("src/main/java/com/example/infrastructure/sync/PullSyncRepository.kt")
        assertTrue(
            "the eviction must be gated on the CONFIGURED posture — a demo-sandbox build never evicts its own demo content",
            s.contains("if (com.example.infrastructure.supabase.NetworkTimeouts.isSupabaseConfigured) {\n            runCatching { evictDemoRowsAfterPull() }"),
        )
        assertTrue(s.contains("internal suspend fun evictDemoRowsAfterPull()"))
        // Every cluster is evicted through the shared vocabulary.
        for (call in listOf(
            "deleteByIds(com.example.infrastructure.room.DemoSeedIds.PERSONNEL)",
            "deleteByIds(com.example.infrastructure.room.DemoSeedIds.DEPARTMENTS)",
            "deleteByPersonnelIds(com.example.infrastructure.room.DemoSeedIds.PERSONNEL)",
            "deleteByIds(demoParents)",
            "deleteByIds(com.example.infrastructure.room.DemoSeedIds.STUDENTS)",
            "deleteDemoRows(demoParents, com.example.infrastructure.room.DemoSeedIds.EXTRA_LEDGER_IDS)",
            "deleteByParentIds(demoParents)",
            "deleteDemoRows(demoParents, com.example.infrastructure.room.DemoSeedIds.EXTRA_PAYMENT_IDS)",
            "deleteByIds(com.example.infrastructure.room.DemoSeedIds.VEHICLES)",
            "deleteByIds(com.example.infrastructure.room.DemoSeedIds.ROUTING_STOPS)",
            "deleteSeeded()",
        )) {
            assertTrue("the eviction must call $call", s.contains(call))
        }
    }

    @Test
    fun `the releve pull is wired paginated with the name backfill`() {
        val s = src("src/main/java/com/example/infrastructure/sync/PullSyncRepository.kt")
        assertTrue(s.contains("suspend fun pullReleveEntries()"))
        assertTrue(s.contains("from(\"releve_entries\")"))
        assertTrue(s.contains("backfillPersonnelNames()"))
        assertTrue(s.contains("pullReleveEntries() as? Result.Ok"))
    }

    @Test
    fun `the UUID-impossible LIKE proofs hold on the DAO`() {
        val s = src("src/main/java/com/example/infrastructure/room/LocalDaos.kt")
        // The assessments pattern: the -sub- infix cannot appear in a UUID.
        assertTrue(s.contains("DELETE FROM assessments WHERE id LIKE 'asm-%-sub-%'"))
        // The attendance pattern: the seeder's unique infix.
        assertTrue(s.contains("DELETE FROM attendance WHERE id LIKE 'att-seed-%'"))
        // The installments deletion is PARENT-SCOPED (the ins-stu-* shape is
        // also produced by batchRegister for REAL students).
        assertTrue(s.contains("DELETE FROM installments WHERE parentId IN (:parentIds)"))
    }
}
