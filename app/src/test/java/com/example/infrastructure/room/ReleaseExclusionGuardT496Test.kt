package com.example.infrastructure.room

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-496 (TEST-504) — the MECHANICAL release-exclusion guard: the 6th-recurrence
 * prevention for ARCH-012.
 *
 * The 5th recurrence (T-491, the 147th session's discovery): a session lands a
 * MigrationTestHelper suite WITHOUT its testReleaseUnitTest exclusion entry, and
 * the FULL `./gradlew test` gate goes red on main for every session that quotes
 * only `testDebugUnitTest`. Each recurrence was HUMAN-noticed — this suite makes
 * the contract MECHANICAL: it fails on the debug variant the moment the
 * unexcluded suite exists, in the SAME commit (the §15.86 rule, now enforced by
 * a test instead of by memory).
 *
 * THE SIGNAL: the import `androidx.room.testing.MigrationTestHelper`. The
 * debug-scoped Room schema assets are unreachable on the release variant (the
 * sourceSets rule in app/build.gradle.kts), so EVERY suite importing the helper
 * fails on testReleaseUnitTest by construction — whatever its name. A file that
 * only MENTIONS the helper in comments (the T-492/T-046/T-181 wiring tests)
 * does not import it and is correctly NOT matched.
 *
 * The guard itself imports nothing but JUnit + java.io — it runs green on BOTH
 * variants, which is the point: it guards the release gate.
 */
class ReleaseExclusionGuardT496Test {

    private val moduleDir = File(".").absoluteFile.normalize()
    private val testRoot = File("src/test/java")
    private val gradleFile = File("build.gradle.kts")

    /** The `testReleaseUnitTest` block's exclusion entries (FQNs). */
    private fun releaseExclusions(): List<String> {
        assertTrue("missing app/build.gradle.kts (working dir must be the module dir: $moduleDir)", gradleFile.exists())
        val src = gradleFile.readText()
        val blockStart = src.indexOf("it.name == \"testReleaseUnitTest\"")
        assertTrue("the testReleaseUnitTest filter block is missing", blockStart >= 0)
        val blockEnd = src.indexOf("tasks.withType<Test>", blockStart)
        val block = src.substring(blockStart, if (blockEnd > blockStart) blockEnd else src.length)
        return Regex("excludeTestsMatching\\(\"([^\"]+)\"\\)").findAll(block)
            .map { it.groupValues[1] }.toList()
    }

    /**
     * Every test class that IMPORTS MigrationTestHelper, with its fully
     * qualified name derived from the package declaration + the file name
     * (the repo's one-class-per-file convention).
     */
    private fun migrationHelperSuites(): List<Pair<String, File>> {
        assertTrue("missing test root: ${testRoot.absolutePath}", testRoot.exists())
        val found = mutableListOf<Pair<String, File>>()
        testRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val src = f.readText()
            if (Regex("^import androidx\\.room\\.testing\\.MigrationTestHelper$", RegexOption.MULTILINE).containsMatchIn(src)) {
                val pkg = Regex("^package\\s+([\\w.]+)", RegexOption.MULTILINE).find(src)?.groupValues?.get(1)
                assertTrue("MigrationTestHelper importer without a package declaration: $f", pkg != null)
                found += ("$pkg.${f.nameWithoutExtension}") to f
            }
        }
        return found
    }

    // ─── 1. the core guard ────────────────────────────────────────────────

    @Test
    fun `every MigrationTestHelper suite appears in the release exclusion list`() {
        val suites = migrationHelperSuites()
        assertTrue("the scan found no MigrationTestHelper suites — the walk is broken", suites.isNotEmpty())
        val exclusions = releaseExclusions()
        val missing = suites.map { it.first }.filter { it !in exclusions }
        assertEquals(
            "MigrationTestHelper suites MISSING from the testReleaseUnitTest exclusion list " +
                "(the §15.86 same-commit rule — add the excludeTestsMatching entry in the SAME commit " +
                "as the suite): $missing",
            emptyList<String>(),
            missing,
        )
    }

    // ─── 2. the block's structural sanity ────────────────────────────────

    @Test
    fun `the release exclusion list is populated and carries the known schema suites`() {
        val exclusions = releaseExclusions()
        assertTrue(
            "the exclusion list must carry the seven live MigrationTestHelper suites (found ${exclusions.size})",
            exclusions.size >= 7,
        )
        // The documented members (T-046-gap/T-102v2/T-181/T-348/T-463/T-464/T-492) —
        // if one of these goes missing the block was edited destructively.
        for (known in listOf(
            "com.example.infrastructure.room.RoomSchemaUpgradeT046GapTest",
            "com.example.infrastructure.room.RoomSchemaUpgradeT102v2Test",
            "com.example.infrastructure.room.RoomSchemaUpgradeT181Test",
            "com.example.infrastructure.room.RoomSchemaUpgradeT348Test",
            "com.example.infrastructure.room.RoomSchemaUpgradeT463Test",
            "com.example.infrastructure.room.RoomSchemaUpgradeT464Test",
            "com.example.infrastructure.room.RoomSchemaUpgradeT492Test",
        )) {
            assertTrue("the documented exclusion entry went missing: $known", known in exclusions)
        }
    }

    // ─── 3. the stale-entry direction (rot detection) ─────────────────────

    @Test
    fun `every Room-schema exclusion entry corresponds to an existing test file`() {
        val exclusions = releaseExclusions()
        val schemaEntries = exclusions.filter { it.substringAfterLast('.').startsWith("RoomSchemaUpgrade") }
        assertTrue("no RoomSchemaUpgrade entries found — the pattern moved?", schemaEntries.isNotEmpty())
        val stale = schemaEntries.filter { fqn ->
            val pkg = fqn.substringBeforeLast('.')
            val cls = fqn.substringAfterLast('.')
            !File(testRoot, pkg.replace('.', '/') + "/" + cls + ".kt").exists()
        }
        assertEquals(
            "STALE exclusion entries (the test file no longer exists — remove the entry): $stale",
            emptyList<String>(),
            stale,
        )
    }

    // ─── 4. the guard runs on BOTH variants (self-check) ──────────────────

    @Test
    fun `the guard itself never needs exclusion`() {
        // The guard must run on testReleaseUnitTest too — it uses no debug-scoped
        // assets. If someone adds THIS class to the exclusion list, the guard has
        // been neutered: fail loudly.
        assertTrue(
            "the guard test must NOT be excluded from the release gate — it is the guard",
            "com.example.infrastructure.room.ReleaseExclusionGuardT496Test" !in releaseExclusions(),
        )
    }
}
