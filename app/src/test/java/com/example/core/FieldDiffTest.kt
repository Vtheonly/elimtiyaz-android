package com.example.core

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-297 (OFFLINE-400) — the FieldDiff engine mirror test suite.
 *
 * Mirrors the desktop vectors from src/tests/domain/field-diff.test.ts
 * (commit 86dcf77, T-295) — every boundary rule pinned on both platforms:
 * nested objects, arrays (grow/shrink/index-wise), primitives, null vs
 * absent vs "", INSERT/DELETE semantics, deep-equal fast path, display
 * formatting.
 */
class FieldDiffTest {

    // ─── Flat objects ────────────────────────────────────────────────

    @Test
    fun `structurally equal snapshots produce no rows (key order irrelevant`() {
        val before = buildJsonObject { put("a", 1); put("b", "x"); put("c", JsonNull) }
        val after = buildJsonObject { put("c", JsonNull); put("b", "x"); put("a", 1) }
        assertTrue(computeFieldDiff(before, after).isEmpty())
    }

    @Test
    fun `single changed primitive field`() {
        val before = buildJsonObject { put("status", "pending"); put("amount", 2500000) }
        val after = buildJsonObject { put("status", "paid"); put("amount", 2500000) }
        val rows = computeFieldDiff(before, after)
        assertEquals(1, rows.size)
        assertEquals("status", rows[0].path)
        assertEquals(FieldDiffKind.CHANGED, rows[0].kind)
        assertEquals("pending", rows[0].oldDisplay)
        assertEquals("paid", rows[0].newDisplay)
    }

    @Test
    fun `multiple changed fields in sorted key order`() {
        val before = buildJsonObject { put("zeta", 1); put("alpha", 2); put("mid", 3) }
        val after = buildJsonObject { put("zeta", 9); put("alpha", 2); put("mid", 8) }
        val rows = computeFieldDiff(before, after)
        assertEquals(listOf("mid", "zeta"), rows.map { it.path })
    }

    @Test
    fun `null to empty-string is a real change`() {
        val before = buildJsonObject { put("note", JsonNull) }
        val after = buildJsonObject { put("note", "") }
        val rows = computeFieldDiff(before, after)
        assertEquals(1, rows.size)
        assertEquals(FieldDiffKind.CHANGED, rows[0].kind)
        assertEquals("null", rows[0].oldDisplay)
        assertEquals("\"\"", rows[0].newDisplay)
    }

    @Test
    fun `absent key vs present-with-null is an added row`() {
        val before = buildJsonObject { put("a", 1) }
        val after = buildJsonObject { put("a", 1); put("b", JsonNull) }
        val rows = computeFieldDiff(before, after)
        assertEquals(1, rows.size)
        assertEquals("b", rows[0].path)
        assertEquals(FieldDiffKind.ADDED, rows[0].kind)
        assertEquals("—", rows[0].oldDisplay)
        assertEquals("null", rows[0].newDisplay)
    }

    @Test
    fun `present-with-null vs absent key is a removed row`() {
        val before = buildJsonObject { put("a", 1); put("b", JsonNull) }
        val after = buildJsonObject { put("a", 1) }
        val rows = computeFieldDiff(before, after)
        assertEquals(1, rows.size)
        assertEquals("b", rows[0].path)
        assertEquals(FieldDiffKind.REMOVED, rows[0].kind)
        assertEquals("null", rows[0].oldDisplay)
        assertEquals("—", rows[0].newDisplay)
    }

    @Test
    fun `boolean and number changes detected`() {
        val before = buildJsonObject { put("active", 1); put("score", 10) }
        val after = buildJsonObject { put("active", true); put("score", 10.5) }
        val rows = computeFieldDiff(before, after)
        assertEquals(setOf("active", "score"), rows.map { it.path }.toSet())
        val active = rows.first { it.path == "active" }
        assertEquals("1", active.oldDisplay)
        assertEquals("true", active.newDisplay)
    }

    // ─── INSERT / DELETE semantics ───────────────────────────────────

    @Test
    fun `INSERT renders every top-level field as added green rows`() {
        val after = buildJsonObject { put("id", "p1"); put("name", "Ahmed"); put("amount", 1000) }
        val rows = computeFieldDiff(null, after)
        assertEquals(3, rows.size)
        assertTrue(rows.all { it.kind == FieldDiffKind.ADDED })
        assertTrue(rows.all { it.oldDisplay == "—" })
    }

    @Test
    fun `DELETE renders every top-level field as removed red rows`() {
        val before = buildJsonObject { put("id", "p1"); put("name", "Ahmed") }
        val rows = computeFieldDiff(before, null)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.kind == FieldDiffKind.REMOVED })
        assertTrue(rows.all { it.newDisplay == "—" })
    }

    @Test
    fun `both sides null produce no rows`() {
        assertTrue(computeFieldDiff(null, null).isEmpty())
    }

    @Test
    fun `INSERT of nested object renders per-field green rows`() {
        val after = buildJsonObject {
            put("parent", buildJsonObject { put("firstName", "A"); put("lastName", "B") })
            put("status", "active")
        }
        val rows = computeFieldDiff(null, after)
        assertEquals(setOf("parent.firstName", "parent.lastName", "status"), rows.map { it.path }.toSet())
        assertTrue(rows.all { it.kind == FieldDiffKind.ADDED })
    }

    // ─── Nested objects ──────────────────────────────────────────────

    @Test
    fun `recurses into changed nested objects with dotted paths`() {
        val before = buildJsonObject {
            put("parent", buildJsonObject {
                put("firstName", "Ahmed")
                put("contact", buildJsonObject { put("phone", "+2131") })
            })
        }
        val after = buildJsonObject {
            put("parent", buildJsonObject {
                put("firstName", "Ahmed")
                put("contact", buildJsonObject { put("phone", "+2132") })
            })
        }
        val rows = computeFieldDiff(before, after)
        assertEquals(1, rows.size)
        assertEquals("parent.contact.phone", rows[0].path)
        assertEquals("+2131", rows[0].oldDisplay)
        assertEquals("+2132", rows[0].newDisplay)
    }

    @Test
    fun `nested object added wholesale renders per-field green rows`() {
        val before = buildJsonObject { put("meta", JsonNull) }
        val after = buildJsonObject {
            put("meta", buildJsonObject { put("source", "excel"); put("row", 42) })
        }
        val rows = computeFieldDiff(before, after)
        assertEquals(setOf("meta.source", "meta.row"), rows.map { it.path }.toSet())
        assertTrue(rows.all { it.kind == FieldDiffKind.ADDED })
    }

    @Test
    fun `nested object removed wholesale renders per-field red rows`() {
        val before = buildJsonObject { put("meta", buildJsonObject { put("source", "excel") }); put("keep", 1) }
        val after = buildJsonObject { put("keep", 1) }
        val rows = computeFieldDiff(before, after)
        assertEquals(1, rows.size)
        assertEquals("meta.source", rows[0].path)
        assertEquals(FieldDiffKind.REMOVED, rows[0].kind)
    }

    @Test
    fun `scalar to object shape change renders whole-value change`() {
        val before = buildJsonObject { put("x", 5) }
        val after = buildJsonObject { put("x", buildJsonObject { put("deep", 1) }) }
        val rows = computeFieldDiff(before, after)
        assertEquals(1, rows.size)
        assertEquals("x", rows[0].path)
        assertEquals(FieldDiffKind.CHANGED, rows[0].kind)
        assertEquals("5", rows[0].oldDisplay)
        assertEquals("{…} 1 champ", rows[0].newDisplay)
    }

    @Test
    fun `triple-level nesting`() {
        val before = buildJsonObject { put("a", buildJsonObject { put("b", buildJsonObject { put("c", buildJsonObject { put("d", 1) }) }) }) }
        val after = buildJsonObject { put("a", buildJsonObject { put("b", buildJsonObject { put("c", buildJsonObject { put("d", 2) }) }) }) }
        val rows = computeFieldDiff(before, after)
        assertEquals(1, rows.size)
        assertEquals("a.b.c.d", rows[0].path)
        assertEquals("1", rows[0].oldDisplay)
        assertEquals("2", rows[0].newDisplay)
    }

    // ─── Arrays ──────────────────────────────────────────────────────

    @Test
    fun `in-place element change with bracketed index path`() {
        val before = buildJsonObject {
            put("installments", buildJsonArray {
                add(buildJsonObject { put("id", "i1"); put("amount", 100) })
                add(buildJsonObject { put("id", "i2"); put("amount", 200) })
            })
        }
        val after = buildJsonObject {
            put("installments", buildJsonArray {
                add(buildJsonObject { put("id", "i1"); put("amount", 100) })
                add(buildJsonObject { put("id", "i2"); put("amount", 250) })
            })
        }
        val rows = computeFieldDiff(before, after)
        assertEquals(1, rows.size)
        assertEquals("installments[1].amount", rows[0].path)
        assertEquals(FieldDiffKind.CHANGED, rows[0].kind)
    }

    @Test
    fun `array growth tail entries added`() {
        val before = buildJsonObject { put("tags", buildJsonArray { add(JsonPrimitive("a")) }) }
        val after = buildJsonObject { put("tags", buildJsonArray { add(JsonPrimitive("a")); add(JsonPrimitive("b")); add(JsonPrimitive("c")) }) }
        val rows = computeFieldDiff(before, after)
        assertEquals(listOf("tags[1]", "tags[2]"), rows.map { it.path })
        assertTrue(rows.all { it.kind == FieldDiffKind.ADDED })
    }

    @Test
    fun `array shrink tail entries removed`() {
        val before = buildJsonObject { put("tags", buildJsonArray { add(JsonPrimitive("a")); add(JsonPrimitive("b")); add(JsonPrimitive("c")) }) }
        val after = buildJsonObject { put("tags", buildJsonArray { add(JsonPrimitive("a")) }) }
        val rows = computeFieldDiff(before, after)
        assertEquals(listOf("tags[1]", "tags[2]"), rows.map { it.path })
        assertTrue(rows.all { it.kind == FieldDiffKind.REMOVED })
    }

    @Test
    fun `equal arrays of objects produce no rows`() {
        val before = buildJsonObject { put("items", buildJsonArray { add(buildJsonObject { put("a", 1) }) }) }
        val after = buildJsonObject { put("items", buildJsonArray { add(buildJsonObject { put("a", 1) }) }) }
        assertTrue(computeFieldDiff(before, after).isEmpty())
    }

    // ─── Display formatting ──────────────────────────────────────────

    @Test
    fun `formatDiffValue covers each value class`() {
        assertEquals("—", formatDiffValue(null))
        assertEquals("null", formatDiffValue(JsonPrimitive(null)))
        assertEquals("paid", formatDiffValue(JsonPrimitive("paid")))
        assertEquals("\"\"", formatDiffValue(JsonPrimitive("")))
        assertEquals("2500", formatDiffValue(JsonPrimitive(2500)))
        assertEquals("false", formatDiffValue(JsonPrimitive(false)))
        assertEquals("[…] 2 éléments", formatDiffValue(buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive(2)) }))
        assertEquals("[…] 1 élément", formatDiffValue(buildJsonArray { add(JsonPrimitive(1)) }))
        assertEquals("{…} 1 champ", formatDiffValue(buildJsonObject { put("a", 1) }))
        assertEquals("{…} 2 champs", formatDiffValue(buildJsonObject { put("a", 1); put("b", 2) }))
    }

    @Test
    fun `counts by kind`() {
        val before = buildJsonObject { put("a", 1); put("nested", buildJsonObject { put("x", 1); put("y", 2) }); put("arr", buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive(2)); add(JsonPrimitive(3)) }) }
        val after = buildJsonObject { put("a", 2); put("nested", buildJsonObject { put("x", 1); put("y", 3) }); put("arr", buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive(2)) }) }
        val counts = countDiffRows(computeFieldDiff(before, after))
        assertEquals(2, counts.changed) // a + nested.y
        assertEquals(1, counts.removed) // arr[2]
        assertEquals(0, counts.added)
        assertEquals(3, counts.total)
    }

    // ─── deepEqual reference vectors ─────────────────────────────────

    @Test
    fun `deepEqual canonical table`() {
        assertTrue(deepEqual(null, null))
        assertFalse(deepEqual(JsonPrimitive(null), null))
        assertFalse(deepEqual(JsonPrimitive(1), JsonPrimitive("1")))
        assertTrue(deepEqual(buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive(2)) }, buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive(2)) }))
        assertFalse(deepEqual(buildJsonArray { add(JsonPrimitive(1)); add(JsonPrimitive(2)) }, buildJsonArray { add(JsonPrimitive(2)); add(JsonPrimitive(1)) }))
        assertTrue(deepEqual(buildJsonObject { put("a", 1) }, buildJsonObject { put("a", 1) }))
        assertFalse(deepEqual(buildJsonObject { put("a", 1) }, buildJsonObject { put("a", 1); put("b", 2) }))
    }

    // ─── The raw-string entry point (the screens' contract) ──────────

    @Test
    fun `computeAuditDiffRows parses raw JSON strings`() {
        val before = """{"id":"pay-001","status":"pending","amount":2500000,"note":null}"""
        val after = """{"id":"pay-001","status":"paid","amount":2500000,"note":"Reçu comptoir"}"""
        val rows = computeAuditDiffRows(before, after)
        assertEquals(2, rows.size)
        assertEquals(setOf("status", "note"), rows.map { it.path }.toSet())
        assertTrue(rows.all { it.kind == FieldDiffKind.CHANGED })
    }

    @Test
    fun `computeAuditDiffRows tolerates null and malformed input`() {
        assertTrue(computeAuditDiffRows(null, null).isEmpty())
        assertTrue(computeAuditDiffRows("{bad json", """{"a":1}""").isEmpty())
    }

    // ─── Audit-shaped integration vector (desktop parity) ────────────

    @Test
    fun `payment status transition with nested allocation objects`() {
        val before = """
            {"id":"pay-001","parent_id":"par-001","status":"pending","amount":2500000,
             "allocation":{"installments":[{"id":"i1","applied":0}]}}
        """.trimIndent()
        val after = """
            {"id":"pay-001","parent_id":"par-001","status":"paid","amount":2500000,
             "allocation":{"installments":[{"id":"i1","applied":2500000}]}}
        """.trimIndent()
        val rows = computeAuditDiffRows(before, after)
        assertEquals(setOf("allocation.installments[0].applied", "status"), rows.map { it.path }.toSet())
        val status = rows.first { it.path == "status" }
        assertEquals("pending", status.oldDisplay)
        assertEquals("paid", status.newDisplay)
    }
}
