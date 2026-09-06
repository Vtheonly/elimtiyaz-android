package com.example.infrastructure.local

import com.example.domain.model.WorkflowTrigger
import com.example.infrastructure.room.WorkflowRunEntity
import com.example.infrastructure.supabase.WorkflowEmbedDto
import com.example.infrastructure.supabase.WorkflowRunDto
import com.example.infrastructure.supabase.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-054 regression suite — Android hollow implementations.
 *
 * WEAK-006: `regenerateForCycle` wrote an audit row and returned the
 * installments UNCHANGED (the audit log lied that regeneration happened).
 * The fix mirrors the desktop implementation: re-derive due dates from
 * `officialTuitionDueDates` for non-paid tranches, reset the custom-schedule
 * flags, stamp `academic_cycle`, enqueue the sync pushes.
 *
 * WEAK-008: `WorkflowRunEntity` had NO trigger column and `toDomain()`
 * hardcoded "manual" — every pulled run displayed "Manuel" regardless of the
 * real trigger. The fix: the entity gains the column (MIGRATION_11_12), the
 * DTO mapper keeps the server's value, and toDomain maps it.
 *
 * The repository method itself needs a full Room database + Hilt graph
 * (no in-memory-DB test pattern exists in this suite); its wiring is pinned
 * by source-scan (the SyncErrorSurfacingTest precedent), and the trigger
 * MAPPING is exercised directly (pure mapper).
 */
class HollowImplementationsT054Test {

    // ── WEAK-008: the trigger survives the pull mapping ───────────────────

    @Test
    fun `dto trigger is kept by the entity mapping`() {
        // T-231: the DTO now carries the REAL server column trigger_type.
        val entity = WorkflowRunDto(
            id = "wfr-1", tenantId = null, workflowId = "wf-1",
            workflow = WorkflowEmbedDto(name = "Recouvrement"),
            triggerType = "schedule",
            status = "succeeded", actorId = "cron", startedAt = "2026-08-31T08:00:00Z",
        ).toEntity()
        assertEquals("schedule", entity.trigger)
        assertEquals("Recouvrement", entity.workflowName)
    }

    @Test
    fun `missing dto trigger defaults to manual_run (historical rows)`() {
        val entity = WorkflowRunDto(
            id = "wfr-2", workflowId = "wf-2", triggerType = null, status = "success",
        ).toEntity()
        assertEquals("manual_run", entity.trigger)
    }

    @Test
    fun `entity constructor default is manual (Room migration default matches)`() {
        val entity = WorkflowRunEntity(
            id = "wfr-3", tenantId = "t", workflowId = "wf", workflowName = "n",
            status = "running", startedBy = "u", startedAt = "now",
            finishedAt = null, resultJson = null, errorMessage = null,
        )
        assertEquals("manual", entity.trigger)
    }

    @Test
    fun `the domain trigger enum resolves the wire codes`() {
        assertEquals(WorkflowTrigger.Scheduled, WorkflowTrigger.fromCode("scheduled"))
        assertEquals(WorkflowTrigger.Scheduled, WorkflowTrigger.fromCode("schedule"))
        assertEquals(WorkflowTrigger.Event, WorkflowTrigger.fromCode("event"))
        assertEquals(WorkflowTrigger.Manual, WorkflowTrigger.fromCode("manual"))
        assertEquals(WorkflowTrigger.Manual, WorkflowTrigger.fromCode("manual_run"))
        // T-231: unknown/automatic codes are EVENTS (server-side automatic
        // triggers), never silently "Manuel" — the WEAK-008 family stays dead.
        assertEquals(WorkflowTrigger.Event, WorkflowTrigger.fromCode("payment_overdue"))
        assertEquals(WorkflowTrigger.Event, WorkflowTrigger.fromCode("webhook-unknown"))
    }

    // ── source-scan regression pins ──────────────────────────────────────

    @Test
    fun `toDomain maps the REAL trigger column - no hardcode`() {
        val src = readMainSource("infrastructure/local/LocalRepositories2.kt")
        assertTrue(
            "toDomain must use WorkflowTrigger.fromCode(trigger)",
            src.contains("WorkflowTrigger.fromCode(trigger)"),
        )
        assertFalse(
            "the hardcoded fromCode(\"manual\") must be gone",
            src.contains("WorkflowTrigger.fromCode(\"manual\")"),
        )
    }

    @Test
    fun `room migration 11 to 12 adds the trigger column`() {
        val src = readMainSource("infrastructure/room/ElImtiyazDatabase.kt")
        assertTrue(
            "MIGRATION_11_12 must add the trigger column",
            src.contains("ALTER TABLE workflow_runs ADD COLUMN trigger TEXT NOT NULL DEFAULT 'manual'"),
        )
        // T-039 bumped the database to v13 (notifications.targetRole) — the
        // 11→12 trigger-column migration stays registered; the version just
        // needs to be AT LEAST 12 now (forward-compatible with later bumps).
        val version = Regex("version\\s*=\\s*(\\d+),").find(src)?.groupValues?.get(1)?.toInt()
            ?: error("@Database version not found")
        assertTrue("database version must be >= 12 (was $version)", version >= 12)
    }

    @Test
    fun `regenerateForCycle re-derives due dates from the official schedule`() {
        val src = readMainSource("infrastructure/local/LocalRepositories.kt")
        val body = Regex(
            "override suspend fun regenerateForCycle[\\s\\S]*?\\n    override suspend fun findOverdue",
        ).find(src)?.value ?: error("regenerateForCycle not found")

        assertTrue(
            "must call officialTuitionDueDates (the desktop's schedule source)",
            body.contains("officialTuitionDueDates(year)"),
        )
        assertTrue(
            "must skip paid tranches (desktop contract: settled tranches are preserved)",
            body.contains("if (inst.status == \"paid\") continue"),
        )
        assertTrue(
            "must reset the custom-schedule flags",
            body.contains("customSchedule = false") && body.contains("customScheduleNote = null"),
        )
        assertTrue(
            "must stamp the academic cycle",
            body.contains("academicCycle = cycle"),
        )
        assertTrue(
            "must enqueue the sync pushes so the server sees the new dates",
            body.contains("operation = \"regenerateForCycle\""),
        )
        // The hollow signature is gone: no early return of the unchanged list.
        assertFalse(
            "the hollow implementation (audit + return unchanged) must be gone",
            Regex("auditDao\\.upsert\\(audit\\(\"installment\\.regenerate\"[^)]*\\)\\)\\s*\\n\\s*return Result\\.Ok\\(installmentDao\\.listByParent").containsMatchIn(src),
        )
    }

    private fun readMainSource(relativeUnderSrcMainJava: String): String {
        val relative = "src/main/java/com/example/$relativeUnderSrcMainJava"
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val inModule = File(cwd, relative)
        if (inModule.isFile) return inModule.readText()
        val inRepoRoot = File(cwd.parentFile ?: cwd, relative)
        if (inRepoRoot.isFile) return inRepoRoot.readText()
        error("Source file not found from either ${cwd.absolutePath} or ${cwd.parentFile}: $relative")
    }
}
