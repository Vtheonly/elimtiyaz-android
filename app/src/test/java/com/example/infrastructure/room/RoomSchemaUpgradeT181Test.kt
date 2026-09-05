package com.example.infrastructure.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T-181 (T-173 part b / NOTIF-200) — the v13 → v14 Room upgrade on a real
 * SQLite file (the RoomSchemaUpgradeT046GapTest convention).
 *
 * Behaviour under test:
 *  1. every pre-existing notifications row SURVIVES the v13 → v14 upgrade;
 *  2. the new `dismissedAt` column appears, is nullable TEXT, and
 *     pre-existing rows keep NULL (additive, no default — "active when last
 *     pulled", exactly as the migration intends);
 *  3. post-upgrade writes through the new column round-trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSchemaUpgradeT181Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ElImtiyazDatabase::class.java,
    )

    private companion object {
        const val DB = "migration-t181-test.db"
    }

    @Test
    fun `v13 to v14 - every notifications row survives the upgrade`() {
        val db = helper.createDatabase(DB, 13)
        // The v13 notifications shape: targetRole exists, NO dismissedAt.
        db.execSQL(
            "INSERT INTO notifications (id, tenantId, title, body, type, priority, source, sourceLabel, entityType, entityId, targetUserId, targetRole, isRead, createdAt) " +
                "VALUES ('n-1', 't1', 'Direct row', '', 'info', 'medium', 'system', '', NULL, NULL, 'profile-1', NULL, 0, '2026-09-01T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO notifications (id, tenantId, title, body, type, priority, source, sourceLabel, entityType, entityId, targetUserId, targetRole, isRead, createdAt) " +
                "VALUES ('n-2', 't1', 'Role broadcast', '', 'alert', 'high', 'overdue_scan', '', NULL, NULL, NULL, 'financial_officer', 0, '2026-09-02T00:00:00Z')",
        )
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 14, true, ElImtiyazDatabase.MIGRATION_13_14)
        val count = upgraded.query("SELECT COUNT(*) FROM notifications").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assertEquals("both pre-existing rows must survive the upgrade", 2, count)
        upgraded.close()
    }

    @Test
    fun `v13 to v14 - dismissedAt is nullable TEXT and pre-existing rows keep NULL`() {
        val db = helper.createDatabase(DB, 13)
        db.execSQL(
            "INSERT INTO notifications (id, tenantId, title, body, type, priority, source, sourceLabel, entityType, entityId, targetUserId, targetRole, isRead, createdAt) " +
                "VALUES ('n-1', 't1', 'Pre-upgrade row', '', 'info', 'medium', 'system', '', NULL, NULL, 'profile-1', NULL, 1, '2026-09-01T00:00:00Z')",
        )
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 14, true, ElImtiyazDatabase.MIGRATION_13_14)
        upgraded.query(
            "SELECT name, type FROM pragma_table_info('notifications') WHERE name = 'dismissedAt'",
        ).use { cursor ->
            assertTrue("the dismissedAt column must exist after the upgrade", cursor.moveToFirst())
            assertEquals("dismissedAt", cursor.getString(0))
            assertEquals("TEXT", cursor.getString(1))
        }
        upgraded.query("SELECT dismissedAt FROM notifications WHERE id = 'n-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "pre-existing rows keep NULL (active when last pulled)",
                null,
                if (cursor.isNull(0)) null else cursor.getString(0),
            )
        }
        upgraded.close()
    }

    @Test
    fun `v13 to v14 - post-upgrade writes through dismissedAt round-trip`() {
        val db = helper.createDatabase(DB, 13)
        db.execSQL(
            "INSERT INTO notifications (id, tenantId, title, body, type, priority, source, sourceLabel, entityType, entityId, targetUserId, targetRole, isRead, createdAt) " +
                "VALUES ('n-1', 't1', 'Row to stamp', '', 'info', 'medium', 'system', '', NULL, NULL, 'profile-1', NULL, 0, '2026-09-01T00:00:00Z')",
        )
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 14, true, ElImtiyazDatabase.MIGRATION_13_14)
        upgraded.execSQL("UPDATE notifications SET dismissedAt = '2026-09-04T02:00:00Z' WHERE id = 'n-1'")
        upgraded.query("SELECT dismissedAt FROM notifications WHERE id = 'n-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-09-04T02:00:00Z", cursor.getString(0))
        }
        upgraded.close()
    }
}
