package com.example.equivalence

import org.junit.Test
import java.io.File

/**
 * JUnit wrapper around [AndroidEquivalenceRunner] so the canonical scenario
 * suite runs as part of `./gradlew :app:testDebugUnitTest`.
 *
 * Scenario / output directories can be overridden via system properties:
 *   -DandroidEquivalence.scenariosDir=... -DandroidEquivalence.outputDir=...
 *
 * FIX (path resolution): the working directory of a Gradle test worker is the
 * MODULE directory (`app/`), not the repo root — the previous single relative
 * path made the test abort with "Scenarios directory not found" in every
 * environment. Candidate roots are now probed in order.
 */
class AndroidEquivalenceTest {

    @Test
    fun runCanonicalScenarios() {
        val scenariosDir = resolve(
            "androidEquivalence.scenariosDir",
            "financial-tests/equivalence/scenarios",
        )
        val outputDir = resolve(
            "androidEquivalence.outputDir",
            "financial-tests/equivalence/results/android",
        )
        require(scenariosDir.isDirectory) {
            "Scenarios directory not found: ${scenariosDir.absolutePath} — " +
                "run from the Android repo root or pass " +
                "-DandroidEquivalence.scenariosDir=<path>"
        }
        AndroidEquivalenceRunner.runAll(scenariosDir, outputDir)
    }

    /** Resolve a configured path, probing the CWD then the repo root (`..`). */
    private fun resolve(property: String, relative: String): File {
        System.getProperty(property)?.let { return File(it) }

        val cwd = File(System.getProperty("user.dir") ?: ".")
        val inCwd = File(cwd, relative)
        if (inCwd.isDirectory) return inCwd

        val inRepoRoot = File(cwd.parentFile ?: cwd, relative)
        if (inRepoRoot.isDirectory) return inRepoRoot

        return inCwd
    }
}
