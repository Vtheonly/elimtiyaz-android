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
 * T-463 / CHAT-300 (135th session) — the v17 → v18 Room upgrade on a real
 * SQLite file (the RoomSchemaUpgradeT102v2Test convention): the chat
 * channels READ cache gains the server-derived `scope` column
 * ("internal" | "portal" — which chat system the channel belongs to,
 * hub migration 0135).
 *
 * Behaviour under test:
 *  1. every pre-existing chat_channels row SURVIVES the upgrade with its
 *     content intact, and reads as scope='internal' (the migration's
 *     DEFAULT) until the next online refresh replaces the cache;
 *  2. the scope column exists after the upgrade and accepts both values;
 *  3. a row written with scope='portal' round-trips (the cache stores the
 *     server's derived value verbatim).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSchemaUpgradeT463Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ElImtiyazDatabase::class.java,
    )

    private companion object {
        const val DB = "migration-t463-test.db"
    }

    @Test
    fun `v17 to v18 - cached channels survive and default to internal scope`() {
        val db = helper.createDatabase(DB, 17)
        db.execSQL(
            "INSERT INTO chat_channels (id, tenantId, code, name, channelType, memberIdsJoined, " +
                "description, departmentId, archivedAt, lastMessageAt, lastMessagePreview, createdBy, createdAt) " +
                "VALUES ('ch-1', 't1', 'dm-a', 'Salon equipe', 'group', 'u1,u2', " +
                "NULL, NULL, NULL, '2026-09-01T10:00:00Z', 'reunion', 'u1', '2026-08-01T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO chat_messages (id, tenantId, channelId, authorId, body, sentAt, readByJson, " +
                "deletedAt, editedAt, parentMessageId) " +
                "VALUES ('m-1', 't1', 'ch-1', 'u1', 'Bonjour', '2026-09-01T10:00:00Z', '[]', NULL, NULL, NULL)",
        )
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 18, true, ElImtiyazDatabase.MIGRATION_17_18)

        // The pre-existing row survives the upgrade, content intact.
        upgraded.query(
            "SELECT id, name, lastMessagePreview, scope FROM chat_channels WHERE id = 'ch-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("ch-1", cursor.getString(0))
            assertEquals("Salon equipe", cursor.getString(1))
            assertEquals("reunion", cursor.getString(2))
            // The migration DEFAULT: cached rows read as internal until the
            // next online refresh (replace-style cache) rewrites the truth.
            assertEquals("internal", cursor.getString(3))
        }
        // The cached messages survive too (the upgrade touches only
        // chat_channels).
        upgraded.query("SELECT COUNT(*) FROM chat_messages").use { cursor ->
            cursor.moveToFirst()
            assertEquals(1, cursor.getInt(0))
        }
        upgraded.close()
    }

    @Test
    fun `v17 to v18 - the scope column accepts both chat systems`() {
        val db = helper.createDatabase(DB, 17)
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 18, true, ElImtiyazDatabase.MIGRATION_17_18)
        upgraded.execSQL(
            "INSERT INTO chat_channels (id, tenantId, code, name, channelType, scope, memberIdsJoined, " +
                "description, departmentId, archivedAt, lastMessageAt, lastMessagePreview, createdBy, createdAt) " +
                "VALUES ('ch-p', 't1', 'dm-parent', 'Administration', 'direct', 'portal', 'u1,p1', " +
                "NULL, NULL, NULL, NULL, NULL, 'p1', '2026-10-01T00:00:00Z')",
        )
        upgraded.query("SELECT scope FROM chat_channels WHERE id = 'ch-p'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("portal", cursor.getString(0))
        }
        upgraded.close()
    }
}
