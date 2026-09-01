package com.example.infrastructure.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * T-046-gap (session 18) — Room schema export + MigrationTestHelper upgrade
 * test. This closes the gap recorded at T-046 (17th session): the discipline
 * test proved loud failure on missing migrations, but a true upgrade-path
 * data-preservation test needs Room's MigrationTestHelper with an EXPORTED
 * schema history (exportSchema = true + committed app/schemas/).
 *
 * Machinery under test:
 *  - `ElImtiyazDatabase` now exports its schema (ksp arg
 *    `room.schemaLocation` → `app/schemas/`), and the schema history is
 *    COMMITTED: `12.json` backfilled from the T-024 commit's entity set
 *    (the last pre-`targetRole` shape) and `13.json` the first live export
 *    (T-039's `notifications.targetRole` bump);
 *  - the schemas directory is wired into the TEST ASSETS so Robolectric's
 *    MigrationTestHelper can `createDatabase(name, 12)` from the committed
 *    history — every future schema bump now gets a real upgrade test for
 *    free, and a bump that forgets to commit its schema JSON fails HERE
 *    instead of on a user's device.
 *
 * Behaviour under test (the v12 → v13 upgrade on a real SQLite file):
 *  1. every pre-existing notifications row SURVIVES the upgrade;
 *  2. the new `targetRole` column appears, is nullable TEXT, and
 *     pre-existing rows keep NULL (additive, no default — direct/tenant
 *     broadcasts keep NULL, exactly as the migration intends);
 *  3. post-upgrade writes through the new column round-trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSchemaUpgradeT046GapTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ElImtiyazDatabase::class.java,
    )

    // ── The v12 → v13 upgrade on a real SQLite file ────────────────────────

    @Test
    fun `v12 to v13 - every notifications row survives the upgrade`() {
        val db = helper.createDatabase(DB, 12)
        // The v12 notifications shape: NO targetRole column.
        db.execSQL(
            "INSERT INTO notifications (id, tenantId, title, body, type, priority, source, sourceLabel, entityType, entityId, targetUserId, isRead, createdAt) " +
                "VALUES ('n-1', 't1', 'Direct row', '', 'info', 'medium', 'system', '', NULL, NULL, 'profile-1', 0, '2026-08-01T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO notifications (id, tenantId, title, body, type, priority, source, sourceLabel, entityType, entityId, targetUserId, isRead, createdAt) " +
                "VALUES ('n-2', 't1', 'Role broadcast (pre-v13 shape)', '', 'warning', 'high', 'manual', '', NULL, NULL, NULL, 1, '2026-08-02T00:00:00Z')",
        )
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 13, true, ElImtiyazDatabase.MIGRATION_12_13)
        upgraded.query("SELECT COUNT(*) FROM notifications").use { c ->
            c.moveToFirst()
            assertEquals("both rows must survive the upgrade", 2, c.getInt(0))
        }
        // The read state set BEFORE the upgrade is preserved too.
        upgraded.query("SELECT isRead FROM notifications WHERE id = 'n-2'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("the pre-upgrade read state must survive", 1, c.getInt(0))
        }
        upgraded.close()
    }

    @Test
    fun `v12 to v13 - targetRole appears as nullable TEXT and pre-existing rows keep NULL`() {
        val db = helper.createDatabase(DB, 12)
        db.execSQL(
            "INSERT INTO notifications (id, tenantId, title, body, type, priority, source, sourceLabel, entityType, entityId, targetUserId, isRead, createdAt) " +
                "VALUES ('n-1', 't1', 'Old row', '', 'info', 'medium', 'system', '', NULL, NULL, NULL, 0, '2026-08-01T00:00:00Z')",
        )
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 13, true, ElImtiyazDatabase.MIGRATION_12_13)
        upgraded.query("SELECT name, type, [notnull] FROM pragma_table_info('notifications') WHERE name = 'targetRole'").use { c ->
            assertTrue("the targetRole column must exist after the upgrade", c.moveToFirst())
            assertEquals("TEXT", c.getString(1))
            assertEquals("the column is nullable — pre-v13 rows have no role to record", 0, c.getInt(2))
        }
        upgraded.query("SELECT targetRole FROM notifications WHERE id = 'n-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertNull("pre-existing rows keep NULL (additive column, no default)", c.getString(0))
        }
        // Post-upgrade writes through the new column round-trip.
        upgraded.execSQL("UPDATE notifications SET targetRole = 'teacher' WHERE id = 'n-1'")
        upgraded.query("SELECT targetRole FROM notifications WHERE id = 'n-1'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("teacher", c.getString(0))
        }
        upgraded.close()
    }

    @Test
    fun `v12 to v13 - the upgrade validates against the committed 13_json schema`() {
        // runMigrationsAndValidate(13) compares the post-migration schema
        // against the EXPORTED 13.json (table-by-table, column-by-column,
        // index-by-index). If the migration and the entity drift apart, this
        // fails HERE — that is the whole point of exporting schemas.
        val db = helper.createDatabase(DB, 12)
        db.close()
        val upgraded = helper.runMigrationsAndValidate(DB, 13, true, ElImtiyazDatabase.MIGRATION_12_13)
        assertTrue(upgraded.isOpen)
        upgraded.close()
    }

    // ── The export machinery is pinned (cannot be silently removed) ────────

    @Test
    fun `schema history is committed and the export wiring stays in place`() {
        val schemaDir = File("schemas/com.example.infrastructure.room.ElImtiyazDatabase")
        assertTrue("app/schemas/<db>/ must exist and be COMMITTED", schemaDir.isDirectory)
        for (v in listOf(12, 13)) {
            assertTrue(
                "schemas/$v.json must be committed (MigrationTestHelper needs the history)",
                File(schemaDir, "$v.json").isFile,
            )
        }
        val dbSrc = File("src/main/java/com/example/infrastructure/room/ElImtiyazDatabase.kt").readText()
        assertTrue(
            "exportSchema must stay true — a future bump without its exported schema cannot be upgrade-tested",
            Regex("exportSchema\\s*=\\s*true").containsMatchIn(dbSrc),
        )
        val gradle = File("build.gradle.kts").readText()
        assertTrue(
            "the ksp room.schemaLocation arg must stay configured",
            gradle.contains("room.schemaLocation"),
        )
        assertTrue(
            "the schemas directory must stay wired into the DEBUG sourceSet assets " +
                "(Robolectric resolves assets from the debug merged assets — see the comment in build.gradle.kts)",
            gradle.contains("""getByName("debug").assets.srcDir("${'$'}projectDir/schemas")"""),
        )
        assertFalse(
            "the schemas directory must never be gitignored",
            File("../.gitignore").readText().let { src ->
                src.lines().any { it.trim() == "app/schemas" || it.trim().endsWith("/schemas") || it.trim() == "schemas/" }
            },
        )
    }

    companion object {
        private const val DB = "t046-gap-upgrade-test.db"
    }
}
