package com.example.core

/**
 * Identity Code Generators — Kotlin port of the desktop's
 * `deterministicParentCode` + `stableHash` functions in
 * `supabase-shared-repositories.ts`.
 *
 * TIER 2 R15 — Idempotency at the source. The desktop uses an FNV-1a
 * hash of identity fields (phone, display name, first name, last name)
 * to derive a deterministic `parent_code` so that re-importing the same
 * Excel row produces the SAME code, letting the `upsert_parent_from_import`
 * RPC hit its primary identity match `(tenant_id, parent_code)`. The
 * Android app was using `UUID.randomUUID().toString().takeLast(4)` — a
 * random code on every call, so retries always generated new codes and
 * the upsert RPC could never match. Even after Tier 1 wired the sync
 * push, the same parent imported twice would create two different rows.
 *
 * The hash is FNV-1a 32-bit, hex-encoded, truncated to 6 chars. Not
 * cryptographic — the goal is determinism + low collision rate across
 * a few thousand parents/students, which FNV-1a easily achieves.
 *
 * Pure: zero I/O, zero side effects. The same inputs produce the same
 * outputs on Android and desktop.
 */

/**
 * Compute a short stable hash (6 hex chars) from an arbitrary string.
 *
 * Implementation: FNV-1a 32-bit, hex-encoded, truncated to 6 chars.
 * Matches the desktop's `stableHash` function bit-for-bit.
 */
fun stableHash(input: String): String {
    var h: Int = 0x811c9dc5.toInt()       // FNV offset basis (32-bit)
    for (i in 0 until input.length) {
        h = h xor input[i].code
        h = (h * 0x01000193)              // FNV prime (32-bit signed multiplication)
    }
    // Force unsigned 32-bit and encode as 8-char hex, take first 6.
    val unsigned = h.toLong() and 0xFFFFFFFFL
    val hex = unsigned.toString(16).padStart(8, '0').take(6).uppercase()
    return hex
}

/**
 * Inputs for [deterministicParentCode].
 *
 * Mirrors the desktop's `CreateParentInput` interface — only the identity
 * fields that participate in the hash are required.
 */
data class ParentCodeInput(
    val phone: String? = null,
    val displayName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
)

/**
 * Derive a deterministic parent code from identity fields.
 *
 * Format: `PAR-{year}-{6-hex}` where the hex suffix is the FNV-1a hash
 * of `phone|displayName|firstName|lastName`. Re-importing the same
 * Excel row produces the SAME code → the `upsert_parent_from_import`
 * RPC's primary identity match `(tenant_id, parent_code)` succeeds →
 * idempotent upsert, no duplicates.
 *
 * Falls back to a random 4-char suffix when no identity fields are
 * available — this should never happen in practice (the importer always
 * sets at least one field) but matches the desktop's defensive behavior.
 */
fun deterministicParentCode(year: Int, input: ParentCodeInput): String {
    val identity = listOfNotNull(
        input.phone,
        input.displayName,
        input.firstName,
        input.lastName,
    ).joinToString("|").trim()
    val suffix = if (identity.isNotEmpty()) {
        stableHash(identity)
    } else {
        // Defensive fallback — same shape as the desktop's `randomParentSuffix()`.
        val random = (Math.random() * 36 * 36 * 36 * 36).toLong()
        random.toString(36).uppercase().padStart(4, '0').take(4)
    }
    return "PAR-$year-$suffix"
}

/**
 * Inputs for [deterministicStudentCode].
 */
data class StudentCodeInput(
    val displayName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
)

/**
 * Derive a deterministic student code from (parentId, student display name).
 *
 * Re-importing the same Excel row produces the SAME code → primary identity
 * match `(tenant_id, student_code)` succeeds → idempotent upsert.
 */
fun deterministicStudentCode(
    year: Int,
    parentId: String,
    input: StudentCodeInput,
): String {
    val identity = listOf(
        parentId,
        input.displayName,
        input.firstName,
        input.lastName,
    ).joinToString("|").trim()
    val suffix = if (identity.isNotEmpty()) {
        stableHash(identity)
    } else {
        val random = (Math.random() * 1_000_000L).toInt()
        random.toString(10).padStart(6, '0').take(6)
    }
    return "ELV-$year-$suffix"
}

/**
 * Derive a deterministic activation code from (parentCode, tenantId).
 *
 * The previous Android implementation used `(100_000..999_999).random()`
 * — a different code on every call. Re-importing or retrying the same
 * parent would generate different codes, breaking the
 * `bind-activation-code` edge function's idempotency.
 *
 * The canonical rule is to derive a 6-digit numeric code from the
 * parent's stable identity. We use FNV-1a and take 6 decimal digits.
 */
fun deterministicActivationCode(parentCode: String, tenantId: String = ""): String {
    val identity = "$tenantId|$parentCode".trim()
    if (identity.isEmpty()) return "000000"
    var h: Int = 0x811c9dc5.toInt()
    for (i in 0 until identity.length) {
        h = h xor identity[i].code
        h = (h * 0x01000193)
    }
    val unsigned = h.toLong() and 0xFFFFFFFFL
    // Map to 6 decimal digits [100000, 999999] so the code is never
    // ambiguous (no leading-zero strings, no "000000" sentinel).
    val numeric = (unsigned % 900_000L) + 100_000L
    return numeric.toString()
}
