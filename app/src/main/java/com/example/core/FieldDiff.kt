package com.example.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * Field-level diff engine — OFFLINE-400 / T-297 (46th session, 2026-09-11).
 *
 * VERBATIM Kotlin mirror of the desktop engine
 * `src/domain/calc/diff/field-diff.ts` (commit 86dcf77, T-295 — ADR-002
 * mirror discipline: same semantics, same boundary rules, deterministic
 * output). Computes a structured field diff between two JSON snapshots
 * (`beforeJson` vs `afterJson` in the audit log). RED = Before/old value,
 * GREEN = After/new value — the visual convention is the CALLER's job.
 *
 * Boundary semantics (identical to the desktop):
 *   - null, absent and "" are distinct at the leaves; a null → container
 *     transition renders per-field ADDED rows; container → null renders
 *     per-field REMOVED rows.
 *   - Objects recurse by sorted key order; arrays diff index-wise with
 *     added/removed tail semantics.
 *   - Structurally-equal snapshots produce an empty list.
 */

/** Kind of change at one field path. */
enum class FieldDiffKind { ADDED, REMOVED, CHANGED }

/**
 * One flattened display row (leaf change with the full dotted path).
 * The audit screens render exactly these rows.
 */
data class DiffRow(
    val path: String,
    val kind: FieldDiffKind,
    val oldDisplay: String,
    val newDisplay: String,
)

/** Absent sentinel — renders as "—" (distinct from JsonNull which renders "null"). */
private const val ABSENT_DISPLAY = "—"

/* ------------------------------------------------------------------ */
/*  Deep equality (cycle-safe by pair identity — JSON trees here are  */
/*  acyclic, but the desktop contract includes the guard)             */
/* ------------------------------------------------------------------ */

fun deepEqual(a: JsonElement?, b: JsonElement?): Boolean {
    // Identity only — structural == on JsonPrimitive compares `content`
    // alone (JsonPrimitive(1) == JsonPrimitive("1")), which would wrongly
    // collapse number/string boundaries the desktop engine keeps distinct.
    if (a === b) return true
    if (a == null || b == null) return false
    return deepEqualInner(a, b, mutableMapOf())
}

private fun deepEqualInner(
    a: JsonElement,
    b: JsonElement,
    seen: MutableMap<JsonElement, JsonElement>,
): Boolean {
    if (a === b) return true
    if (seen[a] === b) return true // cycle guard
    seen[a] = b

    if (a is JsonObject && b is JsonObject) {
        if (a.size != b.size) return false
        if (a.keys != b.keys) return false
        return a.keys.all { deepEqualInner(a[it]!!, b[it]!!, seen) }
    }
    if (a is JsonArray && b is JsonArray) {
        if (a.size != b.size) return false
        return a.zip(b).all { (x, y) -> deepEqualInner(x, y, seen) }
    }
    if (a is JsonPrimitive && b is JsonPrimitive) {
        if (a is JsonNull || b is JsonNull) return a is JsonNull && b is JsonNull
        if (a.isString != b.isString) return false
        if (a.isString) return a.content == b.content
        // Numeric comparison by VALUE (10 == 10.0, matching the desktop's
        // JS number semantics — textual "10" vs "10.0" is not a change).
        val an = a.doubleOrNull
        val bn = b.doubleOrNull
        if (an != null && bn != null) return an == bn
        return a.content == b.content // booleans
    }
    // Shape mismatch (object vs array vs primitive).
    return false
}

/* ------------------------------------------------------------------ */
/*  The diff engine                                                    */
/* ------------------------------------------------------------------ */

/**
 * Compute the field-level diff between two JSON snapshots and flatten it
 * into display rows (the audit screens' contract).
 *
 * @param before The old snapshot (null when the action is an INSERT).
 * @param after  The new snapshot (null when the action is a DELETE).
 */
fun computeFieldDiff(before: JsonElement?, after: JsonElement?): List<DiffRow> {
    val rows = mutableListOf<DiffRow>()
    diffNodes(before, after, "", rows, mutableMapOf())
    return rows
}

/** Parse a raw JSON string (or null) into an element, tolerating bad input. */
fun parseJsonOrNull(raw: String?): JsonElement? {
    if (raw.isNullOrBlank()) return null
    return try {
        Json.parseToJsonElement(raw)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private val Json = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/** Internal recursive worker over two PRESENT-or-null values. */
private fun diffNodes(
    before: JsonElement?,
    after: JsonElement?,
    path: String,
    rows: MutableList<DiffRow>,
    seen: MutableMap<JsonElement, JsonElement>,
) {
    // INSERT semantics: before absent/null → every present field is "added".
    if (before == null || before is JsonNull) {
        if (after == null || after is JsonNull) return
        addedRows(after, path, rows)
        return
    }
    // DELETE semantics: after absent/null → every present field is "removed".
    if (after == null || after is JsonNull) {
        removedRows(before, path, rows)
        return
    }

    // Both present. Object↔object or array↔array recurse; null↔container
    // collapses to added/removed content; everything else is a leaf change.
    if (before is JsonObject && after is JsonObject) {
        if (seen[before] === after) return // cycle guard
        seen[before] = after
        val keys = (before.keys + after.keys).sorted()
        for (key in keys) {
            val childPath = joinPath(path, key)
            diffValues(before[key], after[key], childPath, rows, seen)
        }
        return
    }
    if (before is JsonArray && after is JsonArray) {
        if (seen[before] === after) return // cycle guard
        seen[before] = after
        diffArrays(before, after, path, rows, seen)
        return
    }
    if (deepEqual(before, after)) return
    rows.add(
        DiffRow(
            path = path.ifEmpty { "(racine)" },
            kind = FieldDiffKind.CHANGED,
            oldDisplay = formatDiffValue(before),
            newDisplay = formatDiffValue(after),
        )
    )
}

/** Diff a pair where one side may be absent (key present on one side only). */
private fun diffValues(
    bv: JsonElement?,
    av: JsonElement?,
    path: String,
    rows: MutableList<DiffRow>,
    seen: MutableMap<JsonElement, JsonElement>,
) {
    val hasB = bv != null
    val hasA = av != null
    if (!hasB && !hasA) return

    // Key appeared → its whole subtree is new (per-field green rows).
    if (!hasB) {
        if (av != null && av !is JsonNull) addedRows(av, path, rows)
        else rows.add(DiffRow(path, FieldDiffKind.ADDED, ABSENT_DISPLAY, "null"))
        return
    }
    // Key disappeared → its whole subtree is gone (per-field red rows).
    if (!hasA) {
        if (bv !is JsonNull) removedRows(bv, path, rows)
        else rows.add(DiffRow(path, FieldDiffKind.REMOVED, "null", ABSENT_DISPLAY))
        return
    }

    if (deepEqual(bv, av)) return

    // null → container: the content appeared (per-field ADDED rows).
    if (bv is JsonNull && (av is JsonObject || av is JsonArray)) {
        addedRows(av, path, rows)
        return
    }
    // container → null: the content vanished (per-field REMOVED rows).
    if (av is JsonNull && (bv is JsonObject || bv is JsonArray)) {
        removedRows(bv, path, rows)
        return
    }

    // Present on both sides, different → recurse when shapes allow.
    if (bv is JsonObject && av is JsonObject) {
        diffNodes(bv, av, path, rows, seen)
        return
    }
    if (bv is JsonArray && av is JsonArray) {
        diffNodes(bv, av, path, rows, seen)
        return
    }

    rows.add(
        DiffRow(
            path = path,
            kind = FieldDiffKind.CHANGED,
            oldDisplay = formatDiffValue(bv),
            newDisplay = formatDiffValue(av),
        )
    )
}

/** Index-wise array diff with added/removed tail semantics. */
private fun diffArrays(
    before: JsonArray,
    after: JsonArray,
    path: String,
    rows: MutableList<DiffRow>,
    seen: MutableMap<JsonElement, JsonElement>,
) {
    val shared = minOf(before.size, after.size)
    for (i in 0 until shared) {
        val bv = before[i]
        val av = after[i]
        if (deepEqual(bv, av)) continue
        val childPath = joinPath(path, i)
        diffValues(bv, av, childPath, rows, seen)
    }
    // Grown: tail entries are added.
    for (i in shared until after.size) {
        addedRows(after[i], joinPath(path, i), rows)
    }
    // Shrunk: tail entries are removed.
    for (i in shared until before.size) {
        removedRows(before[i], joinPath(path, i), rows)
    }
}

/** Build the FLAT per-field "everything here is new" rows for an added value. */
private fun addedRows(value: JsonElement, path: String, rows: MutableList<DiffRow>) {
    when (value) {
        is JsonObject -> {
            if (value.isEmpty()) {
                rows.add(DiffRow(path.ifEmpty { "(racine)" }, FieldDiffKind.ADDED, ABSENT_DISPLAY, formatDiffValue(value)))
                return
            }
            for (key in value.keys.sorted()) {
                addedRows(value[key]!!, joinPath(path, key), rows)
            }
        }
        is JsonArray -> {
            if (value.isEmpty()) {
                rows.add(DiffRow(path.ifEmpty { "(racine)" }, FieldDiffKind.ADDED, ABSENT_DISPLAY, formatDiffValue(value)))
                return
            }
            value.forEachIndexed { i, item -> addedRows(item, joinPath(path, i), rows) }
        }
        else -> rows.add(
            DiffRow(
                path = path.ifEmpty { "(racine)" },
                kind = FieldDiffKind.ADDED,
                oldDisplay = ABSENT_DISPLAY,
                newDisplay = formatDiffValue(value),
            )
        )
    }
}

/** Build the FLAT per-field "everything here is gone" rows for a removed value. */
private fun removedRows(value: JsonElement, path: String, rows: MutableList<DiffRow>) {
    when (value) {
        is JsonObject -> {
            if (value.isEmpty()) {
                rows.add(DiffRow(path.ifEmpty { "(racine)" }, FieldDiffKind.REMOVED, formatDiffValue(value), ABSENT_DISPLAY))
                return
            }
            for (key in value.keys.sorted()) {
                removedRows(value[key]!!, joinPath(path, key), rows)
            }
        }
        is JsonArray -> {
            if (value.isEmpty()) {
                rows.add(DiffRow(path.ifEmpty { "(racine)" }, FieldDiffKind.REMOVED, formatDiffValue(value), ABSENT_DISPLAY))
                return
            }
            value.forEachIndexed { i, item -> removedRows(item, joinPath(path, i), rows) }
        }
        else -> rows.add(
            DiffRow(
                path = path.ifEmpty { "(racine)" },
                kind = FieldDiffKind.REMOVED,
                oldDisplay = formatDiffValue(value),
                newDisplay = ABSENT_DISPLAY,
            )
        )
    }
}

/** Join a parent path with a child segment (root renders as the segment). */
private fun joinPath(parent: String, segment: Any): String {
    if (parent.isEmpty()) return segment.toString()
    return if (segment is Int) "$parent[$segment]" else "$parent.$segment"
}

/* ------------------------------------------------------------------ */
/*  Display formatting (mirrors formatDiffValue)                       */
/* ------------------------------------------------------------------ */

/**
 * Format a value for compact one-line display in the diff rows.
 * Objects → "{…} N champs", arrays → "[…] N éléments", strings stay raw,
 * null → "null", absent → "—".
 */
fun formatDiffValue(value: JsonElement?): String {
    if (value == null) return ABSENT_DISPLAY
    if (value is JsonNull) return "null"
    if (value is JsonPrimitive) {
        val content = value.content
        if (value.isString) return content.ifEmpty { "\"\"" }
        return content // numbers/booleans as canonical text
    }
    if (value is JsonArray) {
        return "[…] ${value.size} élément${if (value.size == 1) "" else "s"}"
    }
    if (value is JsonObject) {
        val n = value.size
        return "{…} $n champ${if (n == 1) "" else "s"}"
    }
    return value.toString()
}

/** Count rows by kind — for badge summaries ("2 modifiés, 1 ajouté"). */
data class DiffCounts(val added: Int, val removed: Int, val changed: Int) {
    val total: Int get() = added + removed + changed
}

fun countDiffRows(rows: List<DiffRow>): DiffCounts = DiffCounts(
    added = rows.count { it.kind == FieldDiffKind.ADDED },
    removed = rows.count { it.kind == FieldDiffKind.REMOVED },
    changed = rows.count { it.kind == FieldDiffKind.CHANGED },
)

/**
 * Convenience: the audit-diff entry point used by the screens — parse two
 * raw JSON strings (before/after) and produce the display rows.
 *
 * Malformed-input semantics mirror the desktop drawer's `parseAuditDiff`
 * (audit-log-tab.tsx, T-296): a payload that fails to parse renders NO
 * rows — a broken snapshot pair is never partially interpreted as an
 * INSERT or a DELETE.
 */
fun computeAuditDiffRows(beforeJson: String?, afterJson: String?): List<DiffRow> {
    val before = parseJsonOrNull(beforeJson)
    val after = parseJsonOrNull(afterJson)
    val beforeMalformed = !beforeJson.isNullOrBlank() && before == null
    val afterMalformed = !afterJson.isNullOrBlank() && after == null
    if (beforeMalformed || afterMalformed) return emptyList()
    return computeFieldDiff(before, after)
}
