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
 * T-102 chat v2 (133rd session, ANDR-CHAT-200 residual) — the v16 → v17
 * Room upgrade on a real SQLite file (the RoomSchemaUpgradeT181Test
 * convention).
 *
 * Behaviour under test:
 *  1. every pre-existing notifications row SURVIVES the v16 → v17 upgrade
 *     (the chat cache is purely additive — no existing table is touched);
 *  2. the new chat_channels / chat_messages tables exist after the upgrade
 *     with the expected columns and accept writes;
 *  3. post-upgrade chat rows round-trip through the DAO mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSchemaUpgradeT102v2Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ElImtiyazDatabase::class.java,
    )

    private companion object {
        const val DB = "migration-t102v2-test.db"
    }

    @Test
    fun `v16 to v17 - every notifications row survives the upgrade`() {
        val db = helper.createDatabase(DB, 16)
        db.execSQL(
            "INSERT INTO notifications (id, tenantId, title, body, type, priority, source, sourceLabel, entityType, entityId, targetUserId, targetRole, isRead, dismissedAt, createdAt) " +
                "VALUES ('n-1', 't1', 'Pre-upgrade alert', '', 'alert', 'high', 'overdue_scan', '', NULL, NULL, 'profile-1', NULL, 0, NULL, '2026-09-01T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO notifications (id, tenantId, title, body, type, priority, source, sourceLabel, entityType, entityId, targetUserId, targetRole, isRead, dismissedAt, createdAt) " +
                "VALUES ('n-2', 't1', 'Dismissed row', '', 'info', 'medium', 'system', '', NULL, NULL, NULL, 'financial_officer', 1, '2026-09-02T00:00:00Z', '2026-09-02T00:00:00Z')",
        )
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 17, true, ElImtiyazDatabase.MIGRATION_16_17)
        upgraded.query("SELECT COUNT(*) FROM notifications").use { cursor ->
            cursor.moveToFirst()
            assertEquals("both pre-existing rows must survive the upgrade", 2, cursor.getInt(0))
        }
        upgraded.close()
    }

    @Test
    fun `v16 to v17 - the chat cache tables appear and accept writes`() {
        val db = helper.createDatabase(DB, 16)
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 17, true, ElImtiyazDatabase.MIGRATION_16_17)
        // The expected chat_channels shape (the entity's column set).
        for (col in listOf(
            "id", "tenantId", "code", "name", "channelType", "memberIdsJoined",
            "description", "departmentId", "archivedAt", "lastMessageAt",
            "lastMessagePreview", "createdBy", "createdAt",
        )) {
            upgraded.query(
                "SELECT name FROM pragma_table_info('chat_channels') WHERE name = '$col'",
            ).use { cursor ->
                assertTrue("chat_channels.$col must exist after the upgrade", cursor.moveToFirst())
            }
        }
        for (col in listOf(
            "id", "tenantId", "channelId", "authorId", "body", "sentAt",
            "readByJson", "deletedAt", "editedAt", "parentMessageId",
        )) {
            upgraded.query(
                "SELECT name FROM pragma_table_info('chat_messages') WHERE name = '$col'",
            ).use { cursor ->
                assertTrue("chat_messages.$col must exist after the upgrade", cursor.moveToFirst())
            }
        }
        upgraded.execSQL(
            "INSERT INTO chat_channels (id, tenantId, code, name, channelType, memberIdsJoined, description, departmentId, archivedAt, lastMessageAt, lastMessagePreview, createdBy, createdAt) " +
                "VALUES ('ch-1', 't1', 'CHAT-001', 'Famille Benali', 'direct', 'profile-1,profile-2', NULL, NULL, NULL, '2026-10-01T09:00:00Z', 'Bonjour', 'profile-1', '2026-09-01T00:00:00Z')",
        )
        upgraded.execSQL(
            "INSERT INTO chat_messages (id, tenantId, channelId, authorId, body, sentAt, readByJson, deletedAt, editedAt, parentMessageId) " +
                "VALUES ('m-1', 't1', 'ch-1', 'profile-2', 'Bonjour', '2026-10-01T09:00:00Z', '[{\"user_id\":\"profile-2\",\"read_at\":\"2026-10-01T09:00:00Z\"}]', NULL, NULL, NULL)",
        )
        upgraded.query("SELECT COUNT(*) FROM chat_channels").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        upgraded.close()
    }

    @Test
    fun `v16 to v17 - post-upgrade chat rows round-trip through the entity mapping`() {
        val db = helper.createDatabase(DB, 16)
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 17, true, ElImtiyazDatabase.MIGRATION_16_17)
        upgraded.execSQL(
            "INSERT INTO chat_channels (id, tenantId, code, name, channelType, memberIdsJoined, description, departmentId, archivedAt, lastMessageAt, lastMessagePreview, createdBy, createdAt) " +
                "VALUES ('ch-1', 't1', 'CHAT-001', 'Famille Benali', 'direct', 'profile-1,profile-2', NULL, NULL, NULL, NULL, NULL, NULL, NULL)",
        )
        upgraded.query("SELECT memberIdsJoined FROM chat_channels WHERE id = 'ch-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                "the uuid[] join round-trips exactly",
                "profile-1,profile-2",
                cursor.getString(0),
            )
        }
        upgraded.close()
    }
}
