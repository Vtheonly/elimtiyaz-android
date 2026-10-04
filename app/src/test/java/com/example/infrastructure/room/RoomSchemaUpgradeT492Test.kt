package com.example.infrastructure.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T-492 / SYNC-302 — the v19 → v20 Room upgrade on a real SQLite file (the
 * RoomSchemaUpgradeT464Test convention): the expenses table gains the
 * server's proof-uploader attribution + the anomaly note (the columns
 * `expense_tickets` has carried since migration 0008 — receipt_uploaded_by /
 * receipt_uploaded_at / anomaly_flags_json), so PULLED settled tickets render
 * the full 4-stage timeline and the anomaly banner keeps its explanation.
 *
 * Behaviour under test:
 *  1. every pre-existing expenses row SURVIVES the upgrade with its content
 *     intact, and the three new columns read NULL (the honest pre-pull
 *     state — no fabricated uploader or note);
 *  2. the columns exist after the upgrade and a pulled-shape row round-trips.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSchemaUpgradeT492Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ElImtiyazDatabase::class.java,
    )

    private companion object {
        const val DB = "migration-t492-test.db"
    }

    @Test
    fun `v19 to v20 - local expenses survive and read as no-uploader`() {
        val db = helper.createDatabase(DB, 19)
        db.execSQL(
            "INSERT INTO expenses (id, tenantId, requestCode, title, description, amount, category, payee, " +
                "status, submittedBy, submittedByName, submittedAt, approvedBy, approvedAt, disbursedAt, " +
                "settledAt, proofUrl, urgency, anomalyScore, notes, createdAt, updatedAt, finalSpentAmount) " +
                "VALUES ('exp-local-1', 't1', 'EXP-2026-001', 'Fournitures', 'Classe 4AP', 1250000, 'supplies', " +
                "'Librairie En-Nour', 'submitted', 'usr-1', 'Yacine', '2026-10-01T10:00:00Z', NULL, NULL, NULL, " +
                "NULL, NULL, 'normal', 0.0, NULL, '2026-10-01T10:00:00Z', '2026-10-01T10:00:00Z', NULL)",
        )
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 20, true, ElImtiyazDatabase.MIGRATION_19_20)
        upgraded.query(
            "SELECT requestCode, proofUploadedBy, proofUploadedAt, anomalyNote FROM expenses WHERE id = 'exp-local-1'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("EXP-2026-001", cursor.getString(0))
            // The honest pre-pull state: NULL, never a fabricated attribution.
            assertNull(cursor.getString(1))
            assertNull(cursor.getString(2))
            assertNull(cursor.getString(3))
        }
        upgraded.close()
    }

    @Test
    fun `v19 to v20 - a pulled settled ticket round-trips the new columns`() {
        val db = helper.createDatabase(DB, 19)
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 20, true, ElImtiyazDatabase.MIGRATION_19_20)
        upgraded.execSQL(
            "INSERT INTO expenses (id, tenantId, requestCode, title, description, amount, category, payee, " +
                "status, submittedBy, submittedByName, submittedAt, approvedBy, approvedAt, disbursedAt, " +
                "settledAt, proofUrl, urgency, anomalyScore, notes, createdAt, updatedAt, finalSpentAmount, " +
                "proofUploadedBy, proofUploadedAt, anomalyNote) " +
                "VALUES ('uuid-from-server', 't1', 'EXP-2026-A1B2C3', 'Réparation climatisation', 'Salle 12', " +
                "500000000, 'maintenance', 'Climat Oran Services', 'settled', 'usr-2', 'Nadia', " +
                "'2026-09-20T09:00:00Z', 'usr-1', '2026-09-21T09:00:00Z', '2026-09-22T09:00:00Z', " +
                "'2026-09-23T09:00:00Z', 't1/receipts/123.pdf', 'high', 0.0, 'Approuvé', " +
                "'2026-09-20T09:00:00Z', '2026-09-23T09:00:00Z', 498000000, 'usr-1', '2026-09-23T09:00:00Z', " +
                "'Montant 20% au-dessus de la moyenne catégorie')",
        )
        upgraded.query(
            "SELECT proofUploadedBy, proofUploadedAt, anomalyNote, finalSpentAmount FROM expenses WHERE id = 'uuid-from-server'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("usr-1", cursor.getString(0))
            assertEquals("2026-09-23T09:00:00Z", cursor.getString(1))
            assertEquals("Montant 20% au-dessus de la moyenne catégorie", cursor.getString(2))
            assertEquals(498000000L, cursor.getLong(3))
        }
        upgraded.close()
    }
}
