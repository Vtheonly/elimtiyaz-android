package com.example.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Scaffold inner-padding gate — the "content hides UNDER the top bar" family.
 *
 * [com.example.ui.designsystem.components.nav.ElScaffold] hands its `content`
 * lambda a `PaddingValues` that insets the body past the bars (it wraps the M3
 * Scaffold with `contentWindowInsets = ScaffoldDefaults.contentWindowInsets`).
 * A caller that DISCARDS that parameter lays its scroll container out from y=0,
 * so the first children render UNDER the `ElTopBar` — on the « Inscription
 * famille » wizard the stepper and the top of the first card were invisible
 * because of exactly that (BatchRegistrationScreen.kt).
 *
 * This generalises the AGENTS.md §8.1 "update BOTH sides" lesson: the same
 * forgetting already bit three separate classes (the Realtime subscription
 * count, the ARCH-012 release exclusion list, this), so the contract is pinned
 * as a SCAN over every caller instead of a one-off assertion. Canonical idiom:
 * ParentDetailScreen.kt — `) { padding ->` + `.fillMaxSize().padding(padding)`.
 *
 * KNOWN DEBT: CounterPaymentScreen is the single remaining violator; it is
 * listed in [KNOWN_DEBT] so the gate is green today and flips RED as soon as a
 * THIRD violator appears. Delete the entry when that screen is fixed.
 */
class ScaffoldInnerPaddingGateTest {

    private val uiRoot: File = sequenceOf(
        File(System.getProperty("user.dir") ?: "."),
        File(System.getProperty("user.dir") ?: ".").parentFile ?: File("."),
    )
        .map { File(it, "src/main/java/com/example/ui") }
        .first { it.isDirectory }

    /** The ElScaffold DEFINITION (it receives the padding, it doesn't consume one). */
    private val definition = "designsystem/components/nav/ElScaffold.kt"

    /** Documented, still-open violators — this set must shrink to zero. */
    private val KNOWN_DEBT = setOf("features/financials/CounterPaymentScreen.kt")

    private fun relativeKtFiles(): List<String> =
        uiRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { it.relativeTo(uiRoot).path }
            .filter { it != definition }
            .filter { File(uiRoot, it).readText().contains("ElScaffold(") }
            .sorted()
            .toList()

    /** The padding param name the content lambda binds, or null when it binds none. */
    private fun boundPaddingParam(sourceAfterScaffold: String): String? =
        Regex("""\)\s*\{\s*([A-Za-z_]*(?i:padding)[A-Za-z_]*)\s*->""")
            .find(sourceAfterScaffold)?.groupValues?.get(1)

    /** True when the scaffold padding is actually applied to a Modifier. */
    private fun appliesPadding(sourceAfterScaffold: String): Boolean {
        val param = boundPaddingParam(sourceAfterScaffold) ?: return false
        return Regex("""\.padding\(\s*""" + Regex.escape(param) + """\s*[,)]""")
            .containsMatchIn(sourceAfterScaffold)
    }

    /** Every caller that renders its content under the bars. */
    private fun violators(): Set<String> =
        relativeKtFiles()
            .filter { !appliesPadding(File(uiRoot, it).readText().let { s -> s.substring(s.indexOf("ElScaffold(")) }) }
            .toSet()

    // ── the gate ────────────────────────────────────────────────────────────

    @Test
    fun `the scan itself is not vacuous`() {
        assertTrue(
            "the gate must still SEE callers — if this is 0 the scan silently rotted",
            relativeKtFiles().size >= 20,
        )
    }

    @Test
    fun `every ElScaffold caller insets its content past the bars`() {
        val offenders = violators() - KNOWN_DEBT
        assertTrue(
            "these ElScaffold callers DISCARD the inner padding, so their content " +
                "renders under the top bar (fix: name the lambda param and apply " +
                ".padding(padding)): $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `the Inscription famille wizard insets its scroll container past the top bar`() {
        val rel = "features/crm/BatchRegistrationScreen.kt"
        val src = File(uiRoot, rel).readText()
        val after = src.substring(src.indexOf("ElScaffold("))
        val param = boundPaddingParam(after)
        assertTrue(
            "$rel must name the ElScaffold content padding parameter",
            param != null,
        )
        assertTrue(
            "$rel must apply .padding($param) to its root scroll Column — the " +
                "stepper and the top of the first card were hidden under the ElTopBar",
            appliesPadding(after),
        )
    }

    @Test
    fun `the known-debt allowlist has not silently drifted`() {
        assertTrue(
            "KNOWN_DEBT drifted from the real violator set — actual=${violators()}, " +
                "allowlist=$KNOWN_DEBT",
            violators() == KNOWN_DEBT,
        )
    }
}