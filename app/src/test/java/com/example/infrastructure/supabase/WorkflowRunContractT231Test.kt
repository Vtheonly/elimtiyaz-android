package com.example.infrastructure.supabase

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-231 (34th session) — the workflow_runs pull contract.
 *
 * The DTO previously targeted a *planned* schema (trigger / started_by /
 * finished_at / result_json) that NEVER existed: every pulled run decoded
 * with null trigger (→ "Manuel" via the old default), null timestamps and
 * null results. This test decodes the EXACT JSON the workflow-execute EF
 * writes (live-verified shapes from the T-225/T-227 matrices) and pins the
 * full chain: DTO → entity → domain (trigger labels, node results, honest
 * outputs).
 */
class WorkflowRunContractT231Test {

    /** A real server row (the T-225 live matrix shape, abridged ids). */
    private val serverRow = """
        {
          "id": "5b2fbeda-83c9-414c-b319-e2e2bfaaff16",
          "tenant_id": "00000000-0000-0000-0000-000000000001",
          "workflow_id": "f3c9dc85-a10a-4a30-a787-3d233235507d",
          "workflows": { "name": "Relance échelonnée", "code": "WF-T225-LIVE" },
          "trigger_type": "payment_overdue",
          "status": "succeeded",
          "actor_id": "f596bf9a-65f8-46c2-86c3-89bf63c874e5",
          "started_at": "2026-09-07T10:00:00.115Z",
          "completed_at": "2026-09-07T10:00:00.777Z",
          "duration_ms": 662,
          "node_results": [
            {
              "node_id": "t1",
              "node_type": "trigger",
              "node_subtype": "payment_overdue",
              "node_label": "Paiement en retard",
              "status": "succeeded",
              "started_at": "2026-09-07T10:00:00.2Z",
              "completed_at": "2026-09-07T10:00:00.3Z",
              "output": { "note": "trigger entry point", "trigger_type": "payment_overdue" }
            },
            {
              "node_id": "a2",
              "node_type": "action",
              "node_subtype": "log_audit",
              "node_label": "Action-B",
              "status": "skipped",
              "started_at": "2026-09-07T10:00:00.4Z",
              "completed_at": "2026-09-07T10:00:00.4Z",
              "output": { "reason": "branch_not_taken", "skipped": true }
            }
          ],
          "error_message": null
        }
    """.trimIndent()

    @Test
    fun `dto decodes the REAL server columns`() {
        val dto = Json.decodeFromString(WorkflowRunDto.serializer(), serverRow)
        assertEquals("payment_overdue", dto.triggerType)
        assertEquals("succeeded", dto.status)
        assertNotNull(dto.actorId)
        assertEquals("2026-09-07T10:00:00.115Z", dto.startedAt)
        assertEquals("2026-09-07T10:00:00.777Z", dto.completedAt)
        assertEquals(662L, dto.durationMs)
        assertEquals("Relance échelonnée", dto.workflow?.name)
        assertNull(dto.errorMessage)
    }

    @Test
    fun `node_results decode into the EF's exact node shape`() {
        val dto = Json.decodeFromString(WorkflowRunDto.serializer(), serverRow)
        val nodes = dto.nodeResults
        assertNotNull(nodes)
        assertEquals(2, nodes!!.size)
        assertEquals("t1", nodes[0].nodeId)
        assertEquals("trigger", nodes[0].nodeType)
        assertEquals("payment_overdue", nodes[0].nodeSubtype)
        assertEquals("succeeded", nodes[0].status)
        assertEquals("skipped", nodes[1].status)
        // output is a JsonElement (unknown-shape tolerant)
        assertTrue(nodes[0].output.toString().contains("trigger entry point"))
    }

    @Test
    fun `toEntity maps the real fields (trigger_type, actor_id, completed_at, serialized node_results)`() {
        val dto = Json.decodeFromString(WorkflowRunDto.serializer(), serverRow)
        val entity = dto.toEntity()
        assertEquals("payment_overdue", entity.trigger)
        assertEquals("succeeded", entity.status)
        assertEquals("f596bf9a-65f8-46c2-86c3-89bf63c874e5", entity.startedBy)
        assertEquals("2026-09-07T10:00:00.777Z", entity.finishedAt)
        assertEquals("Relance échelonnée", entity.workflowName)
        // node_results round-trip through resultJson (no Room schema change)
        val decoded = Json.decodeFromString(
            ListSerializer(WorkflowNodeResultDto.serializer()),
            entity.resultJson!!,
        )
        assertEquals(2, decoded.size)
        assertEquals("t1", decoded[0].nodeId)
    }

    @Test
    fun `the workflow trigger enum maps the REAL codes (no more Manuel-by-default)`() {
        val fromCode = com.example.domain.model.WorkflowTrigger.Companion::fromCode
        assertEquals(com.example.domain.model.WorkflowTrigger.Manual, fromCode("manual_run"))
        assertEquals(com.example.domain.model.WorkflowTrigger.Manual, fromCode("manual"))
        assertEquals(com.example.domain.model.WorkflowTrigger.Scheduled, fromCode("schedule"))
        assertEquals(com.example.domain.model.WorkflowTrigger.Event, fromCode("payment_overdue"))
        assertEquals(com.example.domain.model.WorkflowTrigger.Event, fromCode("grade_below_threshold"))
        assertEquals(com.example.domain.model.WorkflowTrigger.Manual, fromCode(null))
    }

    @Test
    fun `source scan - the pull selects the workflows(name) embed`() {
        val src = File("src/main/java/com/example/infrastructure/sync/PullSyncRepository.kt").readText()
        assertTrue(src.contains("Columns.raw"))
        assertTrue(src.contains("workflows(name)"))
    }

    @Test
    fun `source scan - the DTO carries the real column names (old names gone)`() {
        val src = File("src/main/java/com/example/infrastructure/supabase/SharedDtos.kt").readText()
        assertTrue(src.contains("""@SerialName("trigger_type")"""))
        assertTrue(src.contains("""@SerialName("completed_at")"""))
        assertTrue(src.contains("""@SerialName("node_results")"""))
        assertTrue(src.contains("""@SerialName("actor_id")"""))
        // the phantom columns must be GONE from the DTO
        assertFalse_(src.contains("""@SerialName("started_by")"""))
        assertFalse_(src.contains("""@SerialName("result_json")"""))
    }

    private fun assertFalse_(condition: Boolean) {
        assertTrue("expected the old phantom column to be removed", !condition)
    }
}
