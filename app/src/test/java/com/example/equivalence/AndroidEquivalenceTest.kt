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

    /**
     * Resolve a configured path, probing in order:
     *  1. the system property (explicit override),
     *  2. the module directory (`app/`),
     *  3. the Android repo root (`..`),
     *  4. the hub repo's desktop module checked out as a SIBLING
     *     (`../AgentGithubUplaod/elimtiyaz-desktop/…` — the canonical
     *     scenario layout per hub AGENTS.md §11; ARCH-007/T-081 fix: the
     *     previous probe list never matched the real three-repo layout, so
     *     the equivalence suite aborted in every documented checkout),
     *  5. a standalone desktop checkout as a sibling (`../elimtiyaz-desktop/…`).
     */
    private fun resolve(property: String, relative: String): File {
        System.getProperty(property)?.let { return File(it) }

        val cwd = File(System.getProperty("user.dir") ?: ".")
        val inCwd = File(cwd, relative)
        if (inCwd.isDirectory) return inCwd

        val inRepoRoot = File(cwd.parentFile ?: cwd, relative)
        if (inRepoRoot.isDirectory) return inRepoRoot

        // Sibling probes: cwd = <root>/app → parent = <root> (Android repo)
        // → parent.parent = the directory that ALSO holds the hub repo.
        val reposDir = cwd.parentFile?.parentFile ?: cwd
        val inSiblingHub = File(reposDir, "AgentGithubUplaod/elimtiyaz-desktop/$relative")
        if (inSiblingHub.isDirectory) return inSiblingHub

        val inSiblingDesktop = File(reposDir, "elimtiyaz-desktop/$relative")
        if (inSiblingDesktop.isDirectory) return inSiblingDesktop

        return inCwd
    }
}
