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
 * T-348 (MATIERE-500 / ADR-018) — the v15 → v16 Room upgrade on a real
 * SQLite file (the RoomSchemaUpgradeT181Test convention).
 *
 * Behaviour under test:
 *  1. every pre-existing assessments + subjects row SURVIVES the upgrade;
 *  2. the new `cc` column appears, is nullable REAL, and pre-existing
 *     rows keep NULL (not entered — bit-identical meaning);
 *  3. the new `coefficientCc` columns appear on BOTH assessments and
 *     subjects, are NOT NULL with SQL DEFAULT 0.0, and pre-existing rows
 *     read back exactly 0.0 (the cc component is EXCLUDED — the legacy
 *     (D1+D2+2×Ex)/4 average is unchanged);
 *  4. post-upgrade writes through the new columns round-trip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomSchemaUpgradeT348Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ElImtiyazDatabase::class.java,
    )

    private companion object {
        const val DB = "migration-t348-test.db"

        // The v15 assessments shape: the canonical marks + the 0041 per-
        // component snapshots — NO cc / coefficientCc.
        const val INSERT_ASSESSMENT =
            "INSERT INTO assessments (id, tenantId, studentId, subjectId, classId, term, academicYear, " +
                "devoir1, devoir2, examen, coefficient, isExtracurricular, subjectAverage, " +
                "enteredBy, enteredAt, coefficientDevoir1, coefficientDevoir2, coefficientExamen) " +
                "VALUES ('a-1', 't1', 'stu-1', 'subj-ar', 'cls-1', 'T1', '2026-2027', " +
                "12.5, 13.0, 11.0, 3.0, 0, 11.875, 'u-1', '2026-09-01T00:00:00Z', 1.0, 1.0, 2.0)"

        // The v15 subjects shape: the directory row — NO coefficientCc.
        const val INSERT_SUBJECT =
            "INSERT INTO subjects (id, tenantId, code, name, category, coefficient, weeklyHours, " +
                "isExtracurricular, isActive, level, passingGrade, " +
                "coefficientDevoir1, coefficientDevoir2, coefficientExamen) " +
                "VALUES ('subj-ar', 't1', 'AR', 'Arabe', 'scolarite', 3.0, 4.0, 0, 1, 'primaire', 10.0, 1.0, 1.0, 2.0)"
    }

    @Test
    fun `v15 to v16 - every pre-existing row survives the upgrade`() {
        val db = helper.createDatabase(DB, 15)
        db.execSQL(INSERT_ASSESSMENT)
        db.execSQL(INSERT_SUBJECT)
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 16, true, ElImtiyazDatabase.MIGRATION_15_16)
        for (table in listOf("assessments", "subjects")) {
            upgraded.query("SELECT COUNT(*) FROM $table").use { cursor ->
                cursor.moveToFirst()
                assertEquals("every pre-existing $table row must survive", 1, cursor.getInt(0))
            }
        }
        upgraded.close()
    }

    @Test
    fun `v15 to v16 - cc is nullable REAL and pre-existing rows keep NULL`() {
        val db = helper.createDatabase(DB, 15)
        db.execSQL(INSERT_ASSESSMENT)
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 16, true, ElImtiyazDatabase.MIGRATION_15_16)
        upgraded.query(
            "SELECT name, type FROM pragma_table_info('assessments') WHERE name = 'cc'",
        ).use { cursor ->
            assertTrue("the cc column must exist after the upgrade", cursor.moveToFirst())
            assertEquals("cc", cursor.getString(0))
            assertEquals("REAL", cursor.getString(1))
        }
        upgraded.query("SELECT cc FROM assessments WHERE id = 'a-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue("pre-existing rows keep NULL (cc not entered)", cursor.isNull(0))
        }
        upgraded.close()
    }

    @Test
    fun `v15 to v16 - coefficientCc defaults to 0 on both tables`() {
        val db = helper.createDatabase(DB, 15)
        db.execSQL(INSERT_ASSESSMENT)
        db.execSQL(INSERT_SUBJECT)
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 16, true, ElImtiyazDatabase.MIGRATION_15_16)
        for (table in listOf("assessments", "subjects")) {
            upgraded.query(
                "SELECT name, type, [dflt_value] FROM pragma_table_info('$table') WHERE name = 'coefficientCc'",
            ).use { cursor ->
                assertTrue("coefficientCc must exist on $table after the upgrade", cursor.moveToFirst())
                assertEquals("coefficientCc", cursor.getString(0))
                assertEquals("REAL", cursor.getString(1))
                assertEquals("the SQL DEFAULT pins the migration's 0.0", "0.0", cursor.getString(2))
            }
            upgraded.query("SELECT coefficientCc FROM $table").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(
                    "pre-existing $table rows read back exactly 0.0 (cc excluded — the legacy average unchanged)",
                    0.0,
                    cursor.getDouble(0),
                    0.0,
                )
                assertFalse(cursor.isNull(0))
            }
        }
        upgraded.close()
    }

    @Test
    fun `v15 to v16 - post-upgrade writes through the new columns round-trip`() {
        val db = helper.createDatabase(DB, 15)
        db.execSQL(INSERT_ASSESSMENT)
        db.close()

        val upgraded = helper.runMigrationsAndValidate(DB, 16, true, ElImtiyazDatabase.MIGRATION_15_16)
        upgraded.execSQL("UPDATE assessments SET cc = 15.25, coefficientCc = 1.0 WHERE id = 'a-1'")
        upgraded.query("SELECT cc, coefficientCc FROM assessments WHERE id = 'a-1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(15.25, cursor.getDouble(0), 0.0)
            assertEquals(1.0, cursor.getDouble(1), 0.0)
        }
        upgraded.close()
    }
}
