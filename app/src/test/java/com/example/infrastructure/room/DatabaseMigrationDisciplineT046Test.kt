package com.example.infrastructure.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * T-046 / ARCH-004 + REG-002 — database migration discipline.
 *
 * The defect: the production Room database carried
 * `fallbackToDestructiveMigration(true)` — a forgotten migration on any
 * future schema bump would SILENTLY WIPE the local source of truth (every
 * parent, student, payment, ledger entry on the device) instead of failing.
 * Eight of the nine existing migrations are themselves fix-ups for past
 * regressions (REG-002) — the process already leans on migrations; the
 * fallback undermined all of them.
 *
 * Fix under test: the destructive fallback is REMOVED from DatabaseModule.
 * A missing migration now fails LOUDLY (IllegalStateException at open) —
 * the correct posture for an offline-first app where Room is the primary
 * store. This suite proves, on a real (Robolectric) SQLite file:
 *
 *   1. a normal open → write → reopen cycle PRESERVES data (no reset);
 *   2. an unresolvable version transition (downgrade 13 → 12, no path
 *      registered) now throws IllegalStateException — the loud failure that
 *      the fallback used to swallow with a destructive reset;
 *   3. source-scan pins: no `fallbackToDestructiveMigration` anywhere in
 *      main sources, and the full explicit chain 3_4 … 11_12 stays
 *      registered in DatabaseModule.
 *
 * Recorded gap (why TESTED, not VERIFIED): a true v11 → v12 upgrade-path
 * data-preservation test needs Room's MigrationTestHelper, which requires
 * exported schema history (exportSchema = true + schemas/ committed). This
 * repo has never exported schemas; enabling that is its own follow-up and
 * is registered in the task entry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DatabaseMigrationDisciplineT046Test {

    private lateinit var context: Context
    private val dbName = "t046-discipline-test.db"

    /**
     * The compiled @Database version. Every schema bump (T-039 → 13, …)
     * must CONSCIOUSLY update this constant — that is the discipline this
     * suite enforces (a bumped version without its migration registered in
     * [buildDb] + DatabaseModule fails loud tests, never silently).
     */
    private val compiledVersion = 14

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getDatabasePath(dbName).delete()
        File(context.getDatabasePath(dbName).parentFile, "$dbName-journal").delete()
        File(context.getDatabasePath(dbName).parentFile, "$dbName-wal").delete()
        File(context.getDatabasePath(dbName).parentFile, "$dbName-shm").delete()
    }

    @After
    fun tearDown() {
        context.getDatabasePath(dbName).delete()
    }

    private fun buildDb() = Room.databaseBuilder(context, ElImtiyazDatabase::class.java, dbName)
        .addMigrations(
            ElImtiyazDatabase.MIGRATION_3_4,
            ElImtiyazDatabase.MIGRATION_4_5,
            ElImtiyazDatabase.MIGRATION_5_6,
            ElImtiyazDatabase.MIGRATION_6_7,
            ElImtiyazDatabase.MIGRATION_7_8,
            ElImtiyazDatabase.MIGRATION_8_9,
            ElImtiyazDatabase.MIGRATION_9_10,
            ElImtiyazDatabase.MIGRATION_10_11,
            ElImtiyazDatabase.MIGRATION_11_12,
            ElImtiyazDatabase.MIGRATION_12_13,
            ElImtiyazDatabase.MIGRATION_13_14,
        )
        .allowMainThreadQueries()
        .build()

    @Test
    fun `fresh open - write - reopen preserves data (no silent reset)`() {
        val db = buildDb()
        db.openHelper.writableDatabase // force open at the compiled version
        val version = db.openHelper.readableDatabase.version
        assertEquals("the compiled @Database version must stay $compiledVersion", compiledVersion, version)

        val dao = db.parentDao()
        val now = java.time.Instant.now().toString()
        val row = ParentEntity(
            id = "par-t046-1", tenantId = "tenant-t046", code = "PAR-TEST-0001",
            firstName = "Test", lastName = "Parent", displayName = "Test Parent",
            phone = "0555000000", whatsapp = null, email = null, occupation = null,
            address = null, transportDestination = null, preferredLanguage = "fr",
            avatarUrl = null, isActive = true, cityTier = null,
            isFinanciallyRestricted = false, activationCode = null,
            nationalId = null, relationship = null, createdAt = now, updatedAt = now,
        )
        kotlinx.coroutines.runBlocking { dao.upsert(row) }

        // Close and reopen through the SAME builder path a second app run uses.
        db.close()
        val db2 = buildDb()
        val reloaded = kotlinx.coroutines.runBlocking { db2.parentDao().getById("par-t046-1") }
        assertNotNull("data must survive a close/reopen cycle", reloaded)
        assertEquals("Parent", reloaded?.lastName)
        db2.close()
    }

    @Test
    fun `unresolvable version transition fails LOUDLY instead of wiping (fallback gone)`() {
        // Simulate a future install whose DB file is at a NEWER version than
        // the app knows (compiledVersion + 1 > compiledVersion) with no
        // registered path.
        val raw = context.openOrCreateDatabase(dbName, Context.MODE_PRIVATE, null)
        raw.version = compiledVersion + 1
        raw.close()

        val db = buildDb()
        val thrown = assertThrows(IllegalStateException::class.java) {
            db.openHelper.writableDatabase
        }
        // The old fallback would have silently dropped all tables and opened
        // cleanly. Room now refuses loudly — the whole point of T-046.
        val msg = thrown.message.orEmpty()
        assertTrue(
            "Room must complain about the missing migration path: $msg",
            msg.contains("migration", ignoreCase = true) || msg.contains("downgrade", ignoreCase = true),
        )
        db.close()
    }

    @Test
    fun `source-scan - no destructive fallback anywhere in main sources`() {
        val module = File("src/main/java/com/example/di/DatabaseModule.kt").readText()
        assertFalse(
            "DatabaseModule must not CALL fallbackToDestructiveMigration",
            Regex("fallbackToDestructiveMigration\\s*\\(").containsMatchIn(module),
        )
        // The full explicit chain stays registered (REG-002 discipline).
        for (m in listOf(
            "MIGRATION_3_4", "MIGRATION_4_5", "MIGRATION_5_6", "MIGRATION_6_7",
            "MIGRATION_7_8", "MIGRATION_8_9", "MIGRATION_9_10", "MIGRATION_10_11",
            "MIGRATION_11_12", "MIGRATION_12_13", "MIGRATION_13_14",
        )) {
            assertTrue(
                "the explicit migration chain must keep $m registered",
                module.contains("ElImtiyazDatabase.$m"),
            )
        }
        // No other main source may reintroduce the fallback.
        val mainRoot = File("src/main/java")
        val offenders = mainRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .mapNotNull { f ->
                if (Regex("fallbackToDestructiveMigration\\s*\\(").containsMatchIn(f.readText())) f.path else null
            }
            .toList()
        assertTrue(
            "the fallbackToDestructiveMigration( call must not reappear in: $offenders",
            offenders.isEmpty(),
        )
    }
}
