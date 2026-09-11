package com.example.infrastructure.sync

import com.example.infrastructure.room.AuditLogEntity
import com.example.infrastructure.supabase.AuditLogDto
import com.example.infrastructure.supabase.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * T-299 (OFFLINE-400) — the audit_logs pull mapping test.
 *
 * The realtime route ("audit_logs" → pullAudits) lands server rows into
 * the Room cache the T-297 attributed feed renders from. This suite pins
 * the DTO → entity mapping: the denormalized attribution columns
 * (actor_name / actor_role / actor_id) AND the diff snapshots
 * (before_json / after_json — the red/green renderer's input) must all
 * survive the round-trip, with the occurred_at → createdAt precedence.
 */
class AuditPullT299Test {

    private fun dto() = AuditLogDto(
        id = "aud-001",
        tenantId = "00000000-0000-0000-0000-000000000001",
        action = "payment.status_changed",
        entityType = "payment",
        entityId = "0192f0aa-1111-4222-8333-444455556666",
        actorId = "11111111-1111-4111-8111-111111111111",
        actorName = "Yacine Benali",
        actorRole = "financial_officer",
        beforeJson = """{"status":"pending","amount":2500000}""",
        afterJson = """{"status":"paid","amount":2500000}""",
        note = "Encaissement comptoir",
        occurredAt = "2026-09-11T12:00:00Z",
        createdAt = "2026-09-11T12:00:01Z",
    )

    @Test
    fun `attribution columns survive the mapping`() {
        val e: AuditLogEntity = dto().toEntity()
        assertEquals("aud-001", e.id)
        assertEquals("Yacine Benali", e.actorName)
        assertEquals("financial_officer", e.actorRole)
        assertEquals("11111111-1111-4111-8111-111111111111", e.actorId)
        assertEquals("payment.status_changed", e.action)
        assertEquals("payment", e.entityType)
        assertEquals("0192f0aa-1111-4222-8333-444455556666", e.entityId)
    }

    @Test
    fun `diff snapshots survive the mapping (the red green renderer's input)`() {
        val e = dto().toEntity()
        assertEquals("""{"status":"pending","amount":2500000}""", e.beforeJson)
        assertEquals("""{"status":"paid","amount":2500000}""", e.afterJson)
        assertEquals("Encaissement comptoir", e.note)
    }

    @Test
    fun `occurred_at takes precedence over created_at`() {
        assertEquals("2026-09-11T12:00:00Z", dto().toEntity().createdAt)
        // occurredAt absent → createdAt fallback.
        val fallback = dto().copy(occurredAt = null).toEntity()
        assertEquals("2026-09-11T12:00:01Z", fallback.createdAt)
    }

    @Test
    fun `null columns render honestly (no fabrication)`() {
        val e = dto().copy(actorRole = null, beforeJson = null, afterJson = null, note = null, entityId = null)
            .toEntity()
        assertNull(e.actorRole)
        assertNull(e.beforeJson)
        assertNull(e.afterJson)
        assertNull(e.note)
        assertEquals("", e.entityId)
    }
}
