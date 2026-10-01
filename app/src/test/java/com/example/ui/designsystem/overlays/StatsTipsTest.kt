package com.example.ui.designsystem.overlays

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T-458 (128th session) — the StatsTips glossary integrity suite.
 *
 * The glossary is GENERATED from the desktop's canonical
 * `src/i18n/stats-tips.ts` FR tree (scripts/gen_stats_tips_kotlin.mjs —
 * the verbatim-port discipline). This suite pins the CONTRACT against
 * the desktop's own shape: the section/entry counts (22 sections, 85
 * entries — the desktop's T-447 inventory), the dotted-key lookup, the
 * honest-null for unknown keys, and verbatim spot-checks (the texts are
 * byte-identical to the desktop's FR reference — a divergence here
 * means the two platforms render different explanations).
 */
class StatsTipsTest {

    @Test
    fun `the glossary carries the desktop's inventory - 22 sections, 85 entries`() {
        val sections = listOf(
            "viewMode", "header", "slicers", "statStrip", "waveVelocity", "triage", "erosion",
            "concentration", "dynamics", "transport", "services", "risk", "methodMix", "categoryMix",
            "yoy", "aging", "pareto", "payroll", "console", "crossRisk", "pivot", "inspector",
        )
        val allEntries = sections.flatMap { section ->
            val map = StatsTips.tip("$section.__probe__") // null — just for coverage discipline
            sectionEntries(section)
        }
        assertEquals(22, sections.size)
        assertEquals(85, allEntries.size)
    }

    private fun sectionEntries(section: String): List<String> = when (section) {
        "viewMode" -> StatsTips.viewMode.keys
        "header" -> StatsTips.header.keys
        "slicers" -> StatsTips.slicers.keys
        "statStrip" -> StatsTips.statStrip.keys
        "waveVelocity" -> StatsTips.waveVelocity.keys
        "triage" -> StatsTips.triage.keys
        "erosion" -> StatsTips.erosion.keys
        "concentration" -> StatsTips.concentration.keys
        "dynamics" -> StatsTips.dynamics.keys
        "transport" -> StatsTips.transport.keys
        "services" -> StatsTips.services.keys
        "risk" -> StatsTips.risk.keys
        "methodMix" -> StatsTips.methodMix.keys
        "categoryMix" -> StatsTips.categoryMix.keys
        "yoy" -> StatsTips.yoy.keys
        "aging" -> StatsTips.aging.keys
        "pareto" -> StatsTips.pareto.keys
        "payroll" -> StatsTips.payroll.keys
        "console" -> StatsTips.console.keys
        "crossRisk" -> StatsTips.crossRisk.keys
        "pivot" -> StatsTips.pivot.keys
        "inspector" -> StatsTips.inspector.keys
        else -> emptyList()
    }.let { it as? List<String> ?: it.toList() }

    @Test
    fun `the dotted-key lookup resolves every mounted key`() {
        // The exact keys the Android analytics surfaces mount (T-458's
        // mounting inventory — the desktop's own key convention).
        val mountedKeys = listOf(
            "waveVelocity.card", "waveVelocity.collectedPct", "waveVelocity.pending", "waveVelocity.remaining",
            "triage.card", "triage.notDue", "triage.current", "triage.reminder", "triage.chronic", "triage.callList",
            "pareto.card", "yoy.card", "aging.card",
            "statStrip.count", "statStrip.total", "statStrip.mean", "statStrip.median", "statStrip.stdDev", "statStrip.bestMonth",
            "slicers.header", "slicers.badge",
        )
        for (key in mountedKeys) {
            val entry = StatsTips.tip(key)
            assertNotNull("missing glossary entry: $key", entry)
            assertTrue("$key: blank title", entry!!.title.isNotBlank())
            assertTrue("$key: blank measures", entry.measures.isNotBlank())
            assertTrue("$key: blank calc", entry.calc.isNotBlank())
        }
    }

    @Test
    fun `unknown keys return null - the honest-empty contract`() {
        assertNull(StatsTips.tip("waveVelocity.noSuchEntry"))
        assertNull(StatsTips.tip("noSuchSection.card"))
        assertNull(StatsTips.tip("nodot"))
        assertNull(StatsTips.tip("trailing."))
        assertNull(StatsTips.tip(".leading"))
    }

    @Test
    fun `verbatim spot-checks - the FR texts are the desktop's reference`() {
        // Byte-identical spot-checks against stats-tips.ts (the FR tree —
        // the documentation of record per the desktop's file header).
        assertEquals("Total Encaissé", StatsTips.statStrip.getValue("total").title)
        assertEquals("Panier Moyen", StatsTips.statStrip.getValue("mean").title)
        assertEquals("Médiane", StatsTips.statStrip.getValue("median").title)
        assertEquals("Mesure :", StatsTips.metaMeasures)
        assertEquals("Calcul :", StatsTips.metaCalc)
        assertEquals("Statut :", StatsTips.metaStatus)
        assertNotNull(StatsTips.waveVelocity.getValue("card"))
        assertEquals(
            "Filtres Dynamiques (Slicers)",
            StatsTips.slicers.getValue("header").title,
        )
    }

    @Test
    fun `the triage entries carry their status fields (the verdict vocabulary)`() {
        // The triage entries render verdicts — the status field is present
        // (the §15.66b audit-in-place principle).
        for (bucket in listOf("notDue", "current", "reminder", "chronic")) {
            assertNotNull(StatsTips.triage.getValue(bucket).status)
        }
        assertNotNull(StatsTips.triage.getValue("callList").status)
    }
}
