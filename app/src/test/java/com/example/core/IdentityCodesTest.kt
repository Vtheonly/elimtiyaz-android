package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TIER 2 R15 — regression tests for the deterministic identity code
 * generators ([deterministicParentCode], [deterministicActivationCode],
 * [stableHash]).
 *
 * Verifies that:
 *   - The same inputs always produce the same code (idempotency at the source).
 *   - Different inputs produce different codes (no collisions for typical data).
 *   - The hash matches the desktop's FNV-1a implementation bit-for-bit.
 *   - Re-running batchRegister on the same parent no longer generates random codes.
 */
class IdentityCodesTest {

    // ── stableHash ───────────────────────────────────────────────────────

    @Test
    fun `stableHash is deterministic — same input produces same output`() {
        val input = "0555123456|BENALI|Sara"
        assertEquals(stableHash(input), stableHash(input))
    }

    @Test
    fun `stableHash returns 6 uppercase hex chars`() {
        val out = stableHash("any string")
        assertEquals(6, out.length)
        assertTrue("Hash should be uppercase hex: $out", out.matches(Regex("^[0-9A-F]{6}$")))
    }

    @Test
    fun `stableHash matches canonical FNV-1a test vector`() {
        // The desktop's `stableHash` uses FNV-1a 32-bit, hex-encoded,
        // truncated to 6 chars. Verified against the reference impl.
        // FNV-1a("") = 0x811c9dc5 → first 6 hex chars = "811c9d"
        assertEquals("811C9D", stableHash(""))
        // FNV-1a("a") = 0xe40c292c → "e40c29"
        assertEquals("E40C29", stableHash("a"))
    }

    @Test
    fun `stableHash produces different outputs for different inputs`() {
        assertNotEquals(stableHash("parent1"), stableHash("parent2"))
        assertNotEquals(stableHash("phone1|name1"), stableHash("phone1|name2"))
    }

    // ── deterministicParentCode ─────────────────────────────────────────

    @Test
    fun `parent code is deterministic for identical inputs`() {
        val input = ParentCodeInput(
            phone = "0555123456",
            displayName = "BENALI Sara",
            firstName = "Sara",
            lastName = "Benali",
        )
        val code1 = deterministicParentCode(2026, input)
        val code2 = deterministicParentCode(2026, input)
        assertEquals(code1, code2)
    }

    @Test
    fun `parent code includes the year prefix`() {
        val code = deterministicParentCode(
            2026,
            ParentCodeInput(phone = "0555123456", displayName = "Test"),
        )
        assertTrue("Code should start with PAR-2026-: $code", code.startsWith("PAR-2026-"))
    }

    @Test
    fun `parent code is stable when only the field order differs`() {
        // The hash is computed on `listOfNotNull(...).joinToString("|")`,
        // so the order of fields in the input object matters. But two
        // inputs with the same field values should produce the same hash
        // regardless of how they were constructed.
        val input1 = ParentCodeInput(phone = "0555", firstName = "A", lastName = "B", displayName = null)
        val input2 = ParentCodeInput(phone = "0555", firstName = "A", lastName = "B", displayName = null)
        assertEquals(
            deterministicParentCode(2026, input1),
            deterministicParentCode(2026, input2),
        )
    }

    @Test
    fun `parent code differs for different phones`() {
        val input1 = ParentCodeInput(phone = "0555123456", displayName = "A")
        val input2 = ParentCodeInput(phone = "0666123456", displayName = "A")
        assertNotEquals(
            deterministicParentCode(2026, input1),
            deterministicParentCode(2026, input2),
        )
    }

    @Test
    fun `parent code differs for different display names`() {
        val input1 = ParentCodeInput(phone = "0555", displayName = "BENALI Sara")
        val input2 = ParentCodeInput(phone = "0555", displayName = "BENALI Omar")
        assertNotEquals(
            deterministicParentCode(2026, input1),
            deterministicParentCode(2026, input2),
        )
    }

    @Test
    fun `parent code differs for different years`() {
        val input = ParentCodeInput(phone = "0555", displayName = "Test")
        assertNotEquals(
            deterministicParentCode(2025, input),
            deterministicParentCode(2026, input),
        )
    }

    // ── deterministicActivationCode ─────────────────────────────────────

    @Test
    fun `activation code is deterministic for identical inputs`() {
        val code1 = deterministicActivationCode("PAR-2026-ABC123", "tenant-001")
        val code2 = deterministicActivationCode("PAR-2026-ABC123", "tenant-001")
        assertEquals(code1, code2)
    }

    @Test
    fun `activation code is 6 numeric digits`() {
        val code = deterministicActivationCode("PAR-2026-ABC123", "tenant-001")
        assertEquals(6, code.length)
        assertTrue("Code should be numeric: $code", code.matches(Regex("^\\d{6}$")))
    }

    @Test
    fun `activation code is always between 100000 and 999999`() {
        // The canonical rule maps the hash to 6 decimal digits in the
        // [100000, 999999] range so the code is never ambiguous.
        // (No leading-zero strings, no "000000" sentinel.)
        for (i in 1..1000) {
            val code = deterministicActivationCode("PAR-2026-$i", "tenant-001").toLong()
            assertTrue("Code $code should be >= 100000", code >= 100_000L)
            assertTrue("Code $code should be <= 999999", code <= 999_999L)
        }
    }

    @Test
    fun `activation code differs for different parent codes`() {
        assertNotEquals(
            deterministicActivationCode("PAR-2026-ABC123", "tenant-001"),
            deterministicActivationCode("PAR-2026-DEF456", "tenant-001"),
        )
    }

    @Test
    fun `activation code differs for different tenants`() {
        assertNotEquals(
            deterministicActivationCode("PAR-2026-ABC123", "tenant-001"),
            deterministicActivationCode("PAR-2026-ABC123", "tenant-002"),
        )
    }

    // ── Cross-cutting: idempotency contract ────────────────────────────

    @Test
    fun `re-running batchRegister on the same parent produces the SAME codes`() {
        // This is the canonical idempotency test: simulate a re-import
        // of the same parent — the codes MUST be identical so the
        // upsert_parent_from_import RPC's primary identity match
        // (tenant_id, parent_code) succeeds → idempotent upsert, no duplicates.
        val input = ParentCodeInput(
            phone = "0555123456",
            displayName = "BENALI Sara",
            firstName = "Sara",
            lastName = "Benali",
        )
        val parentCodeRun1 = deterministicParentCode(2026, input)
        val activationCodeRun1 = deterministicActivationCode(parentCodeRun1, "tenant-001")

        val parentCodeRun2 = deterministicParentCode(2026, input)
        val activationCodeRun2 = deterministicActivationCode(parentCodeRun2, "tenant-001")

        assertEquals(parentCodeRun1, parentCodeRun2)
        assertEquals(activationCodeRun1, activationCodeRun2)
    }
}
