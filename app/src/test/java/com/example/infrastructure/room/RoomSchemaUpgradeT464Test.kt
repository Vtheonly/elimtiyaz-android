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
 * T-464 / MEDIA-300 (135th session) — the v18 → v19 Room upgrade on a real
 * SQLite file (the RoomSchemaUpgradeT463Test convention): the chat messages
 * READ cache gains the `attachmentsJson` column (the server's attachments
 * jsonb array, stringify-on-write/parse-on-read).
 *
 * Behaviour under test:
 *  1. every pre-existing chat_messages row SURVIVES the upgrade with its
 *     content intact, and reads as attachmentsJson='[]' (the migration's
 *     DEFAULT) until the next online refresh replaces the cache;
 *  2. the attachmentsJson column exists after the upgrade and a row with
 *     the server's JSON round-trips through the entity mapping.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSchemaUpgradeT464Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ElImtiyazDatabase::class.java,
    )

    private companion object {
        const val DB = "migration-t464-test.db"
    }

    @Test
    fun `v18 to v19 - cached messages survive and default to no attachments`() {
        val db = helper.createDatabase(DB, 18)
        db.execSQL(
            "INSERT INTO chat_messages (id, tenantId, channelId, authorId, body, sentAt, readByJson, " +
                "deletedAt, editedAt, parentMessageId) " +
                "VALUES ('m-1', 't1', 'ch-1', 'u1', 'Bonjour', '2026-09-01T10:00:00Z', '[]', NULL, NULL, NULL)",
        )
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 19, true, ElImtiyazDatabase.MIGRATION_18_19)
        upgraded.query(
            "SELECT id, body, attachmentsJson FROM chat_messages WHERE id = 'm-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("m-1", cursor.getString(0))
            assertEquals("Bonjour", cursor.getString(1))
            // The migration DEFAULT: cached rows read as "no attachments"
            // until the next online refresh (replace-style cache).
            assertEquals("[]", cursor.getString(2))
        }
        upgraded.close()
    }

    @Test
    fun `v18 to v19 - the attachmentsJson column round-trips through the entity mapping`() {
        val db = helper.createDatabase(DB, 18)
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 19, true, ElImtiyazDatabase.MIGRATION_18_19)
        upgraded.execSQL(
            "INSERT INTO chat_messages (id, tenantId, channelId, authorId, body, sentAt, readByJson, attachmentsJson, " +
                "deletedAt, editedAt, parentMessageId) " +
                "VALUES ('m-2', 't1', 'ch-1', 'u1', 'Voici le releve', '2026-10-03T10:00:00Z', '[]', " +
                "'[{\"file_name\":\"releve.pdf\",\"storage_path\":\"t1/ch-1/123-releve.pdf\",\"mime_type\":\"application/pdf\",\"size_bytes\":204800}]', " +
                "NULL, NULL, NULL)",
        )
        upgraded.query("SELECT attachmentsJson FROM chat_messages WHERE id = 'm-2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getString(0).contains("releve.pdf"))
        }
        upgraded.close()
    }
}
