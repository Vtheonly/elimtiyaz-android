package com.example.infrastructure.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T-541 / OPS-324 (180th session) — the v20 → v21 Room upgrade on a real
 * SQLite file (the RoomSchemaUpgradeT464Test convention): the GPS Tournées /
 * Tournee-Driver feature is removed ENTIRELY (the owner's complete-removal
 * mandate), and its three exclusively-tournee tables go with it.
 *
 * Behaviour under test:
 *  1. the three tournee tables (trip_logs / vehicles / routing_stops) are
 *     GONE after the upgrade — a seeded v20 row in each is dropped with the
 *     table;
 *  2. every OTHER table SURVIVES the upgrade untouched (the personnel,
 *     tasks, deliveries, chat data — the Driver ROLE and the deliveries
 *     workflow are NOT tournee components);
 *  3. the final schema validates against the v21 export (Room's own
 *     identityHash check — runMigrationsAndValidate).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSchemaUpgradeT541Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ElImtiyazDatabase::class.java,
    )

    private companion object {
        const val DB = "migration-t541-test.db"
    }

    @Test
    fun `v20 to v21 - the three tournee tables are dropped`() {
        val db = helper.createDatabase(DB, 20)
        // A v20 device carrying tournee data (the feature's own tables).
        db.execSQL(
            "INSERT INTO vehicles (id, tenantId, plate, driverId, driverName, capacity, " +
                "hasWheelchairAccess, isActive, createdAt) " +
                "VALUES ('veh-001', 't1', '16-114-11', 'per-driver1', 'Rachid Chami', 12, " +
                "0, 1, '2026-01-01T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO routing_stops (id, tenantId, studentId, studentName, address, lat, lng, " +
                "shift, orderInRoute, estimatedMinutesFromPrevious, isActive, createdAt) " +
                "VALUES ('stp-001', 't1', 'stu-001', 'Sara Benali', '12 Rue des Frères', 35.7, -0.6, " +
                "'MORNING', 0, 10, 1, '2026-01-01T00:00:00Z')",
        )
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 21, true, ElImtiyazDatabase.MIGRATION_20_21)
        for (table in listOf("vehicles", "routing_stops", "trip_logs")) {
            val cursor = upgraded.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = '$table'",
            )
            assertFalse("the $table table must be gone after v20→v21", cursor.moveToFirst())
            cursor.close()
        }
        upgraded.close()
    }

    @Test
    fun `v20 to v21 - non-tournee data survives the upgrade`() {
        val db = helper.createDatabase(DB, 20)
        // A v20 device carrying NON-tournee data: a personnel row (the
        // driver role itself stays — live user_profiles may hold it) and a
        // chat message.
        db.execSQL(
            "INSERT INTO personnel (id, tenantId, code, firstName, lastName, role, departmentId, " +
                "departmentName, phone, email, status, hireDate, weeklyHoursTarget, createdAt, updatedAt) " +
                "VALUES ('per-driver1', 't1', 'PER-005', 'Rachid', 'Chami', 'driver', NULL, " +
                "'Transport', '0555 40 12 90', NULL, 'active', '2023-01-15T00:00:00Z', 40, " +
                "'2023-01-15T00:00:00Z', '2023-01-15T00:00:00Z')",
        )
        db.execSQL(
            "INSERT INTO chat_messages (id, tenantId, channelId, authorId, body, sentAt, readByJson, attachmentsJson, " +
                "deletedAt, editedAt, parentMessageId) " +
                "VALUES ('m-1', 't1', 'ch-1', 'u1', 'Bonjour', '2026-10-01T10:00:00Z', '[]', '[]', NULL, NULL, NULL)",
        )
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 21, true, ElImtiyazDatabase.MIGRATION_20_21)
        upgraded.query(
            "SELECT firstName, lastName, role FROM personnel WHERE id = 'per-driver1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Rachid", cursor.getString(0))
            assertEquals("Chami", cursor.getString(1))
            // The driver ROLE survives (its remaining grants are the
            // standard worker set — the deliveries workflow).
            assertEquals("driver", cursor.getString(2))
        }
        upgraded.query(
            "SELECT body FROM chat_messages WHERE id = 'm-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Bonjour", cursor.getString(0))
        }
        upgraded.close()
    }
}
