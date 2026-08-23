package com.example.equivalence

import org.junit.Test
import java.io.File

/**
 * JUnit wrapper around [AndroidEquivalenceRunner] so the canonical scenario
 * suite runs as part of `./gradlew :app:testDebugUnitTest`.
 *
 * Scenario / output directories can be overridden via system properties:
 *   -DandroidEquivalence.scenariosDir=... -DandroidEquivalence.outputDir=...
 */
class AndroidEquivalenceTest {

    @Test
    fun runCanonicalScenarios() {
        val scenariosDir = File(
            System.getProperty("androidEquivalence.scenariosDir")
                ?: "financial-tests/equivalence/scenarios",
        )
        val outputDir = File(
            System.getProperty("androidEquivalence.outputDir")
                ?: "financial-tests/equivalence/results/android",
        )
        require(scenariosDir.isDirectory) {
            "Scenarios directory not found: ${scenariosDir.absolutePath} — " +
                "run from the Android repo root or pass " +
                "-DandroidEquivalence.scenariosDir=<path>"
        }
        AndroidEquivalenceRunner.runAll(scenariosDir, outputDir)
    }
}
