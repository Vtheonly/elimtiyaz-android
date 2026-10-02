package com.example.ui.designsystem

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * F-19(a) (133rd session, the T-460 residual) — the SPACING-TOKEN GATE,
 * enforced in the test suite so every session runs it (the 132nd session's
 * gate lived in an uncommitted container script — the SignOutScreen 6.dp
 * regression slipped through exactly because the gate was not in the repo).
 *
 * The standard (mirrors scripts/dp-sweep.py --check — keep the two in sync):
 *  1. TOKEN gate — every token-sized dp literal (0/4/8/12/16/24/32/48/64)
 *     in ui/features AND ui/designsystem must be an ElTheme.spacing token.
 *  2. OFF-GRID gate — every dp literal ≥4dp in ui/features must sit on the
 *     4dp grid (the owner-approved F-19 option (b) scope; the DS components'
 *     internal micro-geometry is deliberately out of scope).
 *
 * Documented exemptions (a violation outside these classes is a real one):
 *  - the whole ui/designsystem/theme/ directory (the scale definitions);
 *  - the sub-4dp micro class 1/2/3.dp (hairlines, borders, strokes);
 *  - corner radii — any N.dp inside a RoundedCornerShape(...) argument span
 *    (paren-tracked across newlines; radii are the SHAPE scale's business);
 *  - an explicit trailing `// spacing-token-exempt: <reason>` line marker —
 *    every marker is a reviewed, justified exclusion (ARCH-012 philosophy).
 */
class SpacingTokenGateT460F19aTest {

    private val tokenNames = mapOf(
        0 to "none", 4 to "xs", 8 to "sm", 12 to "md", 16 to "lg",
        24 to "xl", 32 to "xxl", 48 to "xxxl", 64 to "huge",
    )
    private val micro = setOf(1, 2, 3)
    private val dpLiteral = Regex("\\b(\\d+)\\.dp\\b")

    private fun uiRoot(): File {
        val candidates = listOf(
            File("src/main/java/com/example/ui"),
            File("app/src/main/java/com/example/ui"),
            File("../app/src/main/java/com/example/ui"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate the ui source root (cwd=${File(".").absolutePath})")
    }

    /** Char spans of RoundedCornerShape(...) argument lists, across lines. */
    private fun cornerRadiusSpans(text: String): List<IntRange> {
        val spans = mutableListOf<IntRange>()
        val trigger = Regex("RoundedCornerShape\\s*\\(")
        for (m in trigger.findAll(text)) {
            var depth = 1
            var i = m.range.last + 1
            val start = i
            var inString = false
            while (i < text.length && depth > 0) {
                val ch = text[i]
                if (ch == '"') inString = !inString
                else if (!inString) {
                    if (ch == '(') depth++
                    else if (ch == ')') depth--
                }
                i++
            }
            if (depth == 0) spans.add(start until i - 1)
        }
        return spans
    }

    /** Splits a line into code + // comment (a // that is not a URL's ://). */
    private fun splitComment(line: String): Pair<String, String> {
        var i = 0
        var inString = false
        while (i < line.length - 1) {
            val ch = line[i]
            if (ch == '"') inString = !inString
            if (!inString && ch == '/' && line[i + 1] == '/' && (i == 0 || line[i - 1] != ':')) {
                return line.substring(0, i) to line.substring(i)
            }
            i++
        }
        return line to ""
    }

    private data class Violation(val file: String, val line: Int, val message: String)

    private fun scan(root: File, featuresOnly: Boolean): List<Violation> {
        val violations = mutableListOf<Violation>()
        root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { !it.invariantSeparatorsPath.contains("/designsystem/theme/") }
            .filter { !featuresOnly || it.invariantSeparatorsPath.contains("/ui/features/") }
            .sortedBy { it.path }
            .forEach { file ->
                val text = file.readText()
                val radii = cornerRadiusSpans(text)
                var offset = 0
                text.lineSequence().forEachIndexed { idx, raw ->
                    val lineNo = idx + 1
                    val lineStart = offset
                    offset += raw.length + 1
                    val (code, comment) = splitComment(raw)
                    val trimmed = code.trim()
                    if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return@forEachIndexed
                    if ("spacing-token-exempt" in comment || "spacing-token-exempt" in code) return@forEachIndexed
                    for (m in dpLiteral.findAll(code)) {
                        val value = m.groupValues[1].toInt()
                        if (value in micro) continue
                        val pos = lineStart + m.range.first
                        if (radii.any { pos in it }) continue // corner radius
                        val rel = "ui/" + file.relativeTo(root).invariantSeparatorsPath
                        if (value % 4 != 0) {
                            if (featuresOnly) {
                                violations.add(
                                    Violation(rel, lineNo, "$value.dp is OFF-GRID (not a 4dp step) — snap it to the grid")
                                )
                            }
                        } else if (value in tokenNames) {
                            violations.add(
                                Violation(
                                    rel, lineNo,
                                    "$value.dp must be ElTheme.spacing.${tokenNames[value]} (the F-19(a) token gate)",
                                )
                            )
                        }
                    }
                }
            }
        return violations
    }

    @Test
    fun `the token gate - zero raw token-sized spacing literals in features and designsystem`() {
        val violations = scan(uiRoot(), featuresOnly = false)
        assertTrue(
            "F-19(a) gate FAILED (${violations.size} raw token-sized literals; fix or mark with an explicit " +
                "// spacing-token-exempt: <reason> marker):\n" +
                violations.joinToString("\n") { "  ${it.file}:${it.line} ${it.message}" },
            violations.isEmpty(),
        )
    }

    @Test
    fun `the off-grid gate - every feature dp literal sits on the 4dp grid`() {
        val violations = scan(uiRoot(), featuresOnly = true)
        assertTrue(
            "F-19(b) gate FAILED (${violations.size} off-grid literals in features):\n" +
                violations.joinToString("\n") { "  ${it.file}:${it.line} ${it.message}" },
            violations.none { it.message.contains("OFF-GRID") },
        )
    }
}
