package com.example.infrastructure.supabase

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-310 (49th session, AUDIT-502) — the audit_logs row mapper's
 * payment-detail recovery, mirroring the desktop suite
 * (src/tests/infrastructure/t-310-payment-audit-details.test.ts).
 *
 * The owner's report: "the audit is not bringing up the payment details
 * correctly." Live evidence (2026-09-12): payment.collect rows carry
 * before_json = NULL, after_json = NULL and the full payment result ONLY
 * in the legacy `diff` column — a column the Android DTO never declared
 * (and the String? jsonb declarations would have thrown on any non-null
 * snapshot anyway). This suite pins the JsonElement migration + the
 * effective-snapshot fallback.
 */
class AuditLogDiffRecoveryT310Test {

    private val json = Json

    private fun dtoOf(
        beforeJson: String? = null,
        afterJson: String? = null,
        diff: String? = null,
    ): AuditLogDto {
        val element = { raw: String? -> raw?.let { json.parseToJsonElement(it) } }
        return AuditLogDto(
            id = "aud-310",
            tenantId = "00000000-0000-0000-0000-000000000001",
            action = "payment.collect",
            entityType = "payment",
            entityId = "07fc79e5-90f6-4b68-8b17-13ec1364ee6a",
            actorId = "dac9c821-22a3-4edb-857c-6c4414199d2e",
            actorName = "dac9c821-22a3-4edb-857c-6c4414199d2e",
            actorRole = null,
            beforeJson = element(beforeJson),
            afterJson = element(afterJson),
            diff = element(diff),
            note = null,
            occurredAt = "2026-09-11T19:35:57.439347+00:00",
            createdAt = "2026-09-11T19:35:57.439347+00:00",
        )
    }

    // ── payment.collect: the flat legacy diff becomes the AFTER state ──────

    @Test
    fun `flat diff with null snapshots maps to the after state`() {
        val flat = """{"amount":152500,"method":"cash","status":"paid","receipt":"REC-2026-000001","allocations":[],"unallocatedCredit":152500}"""
        val entity = dtoOf(diff = flat).toEntity()

        assertNull(entity.beforeJson)
        // Stringified compact JSON — parseable by core/FieldDiff.parseJsonOrNull.
        val after = Json.parseToJsonElement(entity.afterJson!!).let { it.toString() }
        assertTrue(after.contains("\"receipt\":\"REC-2026-000001\""))
        assertTrue(after.contains("\"amount\":152500"))
    }

    // ── payment.refund: the wrapped {before, after} diff unwraps ───────────

    @Test
    fun `wrapped diff unwraps to the two snapshots`() {
        val wrapped = """{"before":{"status":"paid"},"after":{"status":"refunded","totalReverted":152500}}"""
        val entity = dtoOf(diff = wrapped).toEntity()

        val before = Json.parseToJsonElement(entity.beforeJson!!).toString()
        val after = Json.parseToJsonElement(entity.afterJson!!).toString()
        assertEquals("{\"status\":\"paid\"}", before)
        assertTrue(after.contains("\"status\":\"refunded\""))
    }

    // ── Canonical columns WIN when present (0086 trigger rows) ─────────────

    @Test
    fun `canonical snapshots win over the legacy diff`() {
        val entity = dtoOf(
            beforeJson = """{"first_name":"right now _T306"}""",
            afterJson = """{"first_name":"right now "}""",
            diff = """{"ignored":"legacy"}""",
        ).toEntity()

        assertEquals("""{"first_name":"right now _T306"}""", entity.beforeJson)
        assertEquals("""{"first_name":"right now "}""", entity.afterJson)
    }

    // ── Object snapshots survive the String? migration (the decode crash) ──

    @Test
    fun `object snapshots stringify without the old String decode crash`() {
        // Pre-T-310 these arrived as JsonObjects; the String? DTO field made
        // decodeList throw on the WHOLE pull. Now: JsonElement → toString.
        val entity = dtoOf(
            beforeJson = """{"nested":{"deep":1},"arr":[1,2]}""",
            afterJson = null,
        ).toEntity()

        val parsed = Json.parseToJsonElement(entity.beforeJson!!)
        assertTrue(parsed.toString().contains("\"deep\":1"))
    }

    // ── Everything null stays honest ────────────────────────────────────────

    @Test
    fun `everything null stays null`() {
        val entity = dtoOf().toEntity()
        assertNull(entity.beforeJson)
        assertNull(entity.afterJson)
    }
}
