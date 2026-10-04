package com.example.infrastructure.room

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.Permission
import com.example.core.Result
import com.example.core.Role
import com.example.core.Session
import com.example.domain.repository.AuthRepository
import com.example.infrastructure.supabase.ReleveEntryDto
import com.example.infrastructure.supabase.SupabaseClientProvider
import com.example.infrastructure.supabase.toEntity
import com.example.infrastructure.sync.PullSyncRepository
import com.example.session.SessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * T-494 (DATA-059) — the demo-posture split + the eviction contract, on a
 * REAL Room instance (the DatabaseSeeder end-to-end + the pull layer's
 * evictDemoRowsAfterPull):
 *
 *  1. SEED GATING — a configured build (demoAllowed=false) seeds ONLY the
 *     real catalogs (pricing/subjects/classes); the demo content (mock
 *     workers, demo families, routing, timesheets, the academic history)
 *     is demo-sandbox-only — the owner's next fresh install never sees a
 *     fabricated worker again;
 *  2. EVICTION SAFETY — after the pull-cycle eviction, every demo row is
 *     gone and every REAL row (server UUID ids, locally-created shapes)
 *     SURVIVES: the exact-id lists, the parent-scoped deletes, and the two
 *     UUID-impossible LIKE patterns (asm-%-sub-% / att-seed-%).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DemoSeedGatingT494Test {

    private lateinit var db: ElImtiyazDatabase
    private lateinit var seeder: DatabaseSeeder
    private lateinit var pullRepo: PullSyncRepository

    private class NoopAuthRepository : AuthRepository {
        override suspend fun signIn(email: String, password: String): Result<Session> =
            Result.Err(com.example.core.Errors.unknown("noop"))
        override suspend fun signOut(): Result<Unit> = Result.Ok(Unit)
        override suspend fun refreshSession(): Result<Session?> = Result.Ok(null)
        override suspend fun changePassword(currentPassword: String, newPassword: String): Result<Unit> = Result.Ok(Unit)
        override fun observeSession(): Flow<Session?> = flowOf(null)
    }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, ElImtiyazDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seeder = DatabaseSeeder(db)
        val sm = SessionManager(NoopAuthRepository())
        pullRepo = PullSyncRepository(db, SupabaseClientProvider(context), sm)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ─── 1. the seed gating ──────────────────────────────────────────────

    @Test
    fun `a configured build seeds the catalogs but no demo content`() = runTest {
        seeder.seedIfEmpty(demoAllowed = false)

        // The catalogs — Android's local sources of truth.
        assertNotNull(db.pricingConfigDao().getActive())
        // The grade key is the LEVEL code ("1ap"), not the row id ("glt-1ap").
        assertNotNull(db.pricingConfigDao().getTuitionByGrade("1ap"))

        // ZERO demo rows on the configured build.
        assertEquals(0, db.personnelDao().countActive())
        assertEquals(0, db.parentDao().count())
        assertEquals(0, db.studentDao().countActive())
        assertEquals(0, db.releveEntryDao().countAll())
        assertEquals(0, db.assessmentDao().count())
        assertEquals(0, db.attendanceDao().countAll())
    }

    @Test
    fun `the demo sandbox still seeds everything`() = runTest {
        seeder.seedIfEmpty(demoAllowed = true)

        assertEquals(5, db.personnelDao().countActive())
        assertEquals(3, db.parentDao().count())
        assertEquals(6, db.studentDao().countActive())
        assertEquals(10, db.releveEntryDao().countAll())
        // The demo academic history lands too (demo students present).
        org.junit.Assert.assertTrue(db.assessmentDao().count() > 0)
        org.junit.Assert.assertTrue(db.attendanceDao().countAll() > 0)
    }

    // ─── 2. the eviction safety contract ─────────────────────────────────

    @Test
    fun `the eviction removes every demo row and keeps every real row`() = runTest {
        // The demo content — the pre-T-494 device's state.
        seeder.seedIfEmpty(demoAllowed = true)
        val demoAssessmentCount = db.assessmentDao().count()
        org.junit.Assert.assertTrue(demoAssessmentCount > 0)

        // ── REAL rows in every cluster a demo deletion could over-reach ──
        // A real (UUID) worker + department.
        db.personnelDao().upsert(
            PersonnelEntity(
                "3f2a1b8c-1111-2222-3333-444455556666", "tenant-real", "PER-100",
                "Real", "Worker", "teacher", null, "Pédagogie", null, null, "active",
                "2020-09-01T00:00:00Z", 35, "now", "now",
            ),
        )
        db.departmentDao().upsertAll(
            listOf(
                DepartmentEntity("9a8b7c6d-1111-2222-3333-444455556666", "tenant-real", "Direction réelle", "réelle", null, null, null, null),
            ),
        )
        // A real family (a UUID parent, the batchRegister shapes: ins-<uuid>-,
        // a generateEntryId ledger row, a pay-<uuid> payment).
        db.parentDao().upsert(
            ParentEntity(
                "11111111-2222-3333-4444-555566667777", "tenant-real", "PAR-2026-REAL",
                "Karim", "Réel", "Karim Réel", "+213 555 00 00 00", null, null, null, null,
                "ville_boumerdes", "fr", null, true, null, false, "1234567",
                null, null, "now", "now",
            ),
        )
        db.studentDao().upsert(
            StudentEntity(
                "aaaa0000-1111-2222-3333-444455556666", "tenant-real", "ELV-2026-900001",
                "11111111-2222-3333-4444-555566667777", "Élève", "Réel", null, "M", "2015-01-01",
                "2026-09-01T00:00:00Z", "primaire", "4ap", "cls-4ap", null, null, "active",
                createdAt = "now", updatedAt = "now",
            ),
        )
        db.ledgerEntryDao().upsert(
            LedgerEntryEntity(
                id = "led-1a2b3c4d-7e8f-9a0b", tenantId = "tenant-real",
                accountId = "parent:11111111-2222-3333-4444-555566667777:category:tuition:student:aaaa0000-1111-2222-3333-444455556666",
                parentId = "11111111-2222-3333-4444-555566667777",
                studentId = "aaaa0000-1111-2222-3333-444455556666",
                category = "tuition", amount = 1_000_000L, type = "charge",
                sourceType = "installment", sourceId = "reg-real-1", method = null,
                receiptNumber = null, paymentStatus = null, reversesId = null,
                description = "Real charge", actorId = "usr-real", actorName = "Real",
                at = "2026-09-15T00:00:00Z", metadataJson = "{}",
            ),
        )
        db.installmentDao().upsert(
            InstallmentEntity(
                id = "ins-aaaa0000-1111-2222-3333-444455556666-t1", tenantId = "tenant-real",
                parentId = "11111111-2222-3333-4444-555566667777",
                studentId = "aaaa0000-1111-2222-3333-444455556666",
                category = "tuition", label = "Tranche 1", amountDue = 1_000_000L,
                amountPaid = 0L, amountPending = 0L, dueDate = "2026-09-15", paidDate = null,
                status = "pending", academicCycle = "2026-2027", customSchedule = false,
                customScheduleNote = null, createdAt = "now", updatedAt = "now",
            ),
        )
        db.paymentDao().upsert(
            PaymentEntity(
                id = "pay-1a2b3c4d-1111-2222-3333-444455556666", tenantId = "tenant-real",
                receiptNumber = "REC-2026-900001", parentId = "11111111-2222-3333-4444-555566667777",
                studentId = null, amount = 500_000L, method = "cash", status = "paid",
                category = "tuition", installmentId = null, proofUrl = null, checkNumber = null,
                checkBankName = null, checkIssueDate = null, checkClearanceDate = null,
                transferReference = null, transferSourceBank = null, notes = null,
                collectedBy = "usr-real", collectedBy_name = "Real",
                collectedAt = "now", createdAt = "now", updatedAt = "now",
            ),
        )
        // A pulled (UUID) timesheet for the real worker.
        db.releveEntryDao().upsertAll(
            listOf(
                ReleveEntryDto(
                    id = "c1d2e3f4-1111-2222-3333-444455556666",
                    tenantId = "tenant-real",
                    personnelId = "3f2a1b8c-1111-2222-3333-444455556666",
                    activityType = "course",
                    description = "Mathématiques 4AP",
                    clockInAt = "2026-10-05T08:00:00Z",
                    durationMinutes = 120,
                    recordedBy = "usr-real",
                ).toEntity(),
            ),
        )
        // A LOCAL grade (asm-<UUID>) + a LOCAL attendance row (att-<UUID>) —
        // the shapes the LIKE patterns must NOT touch.
        db.assessmentDao().upsert(
            AssessmentEntity(
                id = "asm-1a2b3c4d-1111-2222-3333-444455556666", tenantId = "tenant-real",
                studentId = "aaaa0000-1111-2222-3333-444455556666", subjectId = "sub-math",
                classId = "cls-4ap", term = "t1", academicYear = "2026-2027",
                devoir1 = 14.0, devoir2 = 16.0, examen = 15.0, cc = null,
                coefficient = 1.0, subjectAverage = 15.0,
                enteredBy = "usr-real", enteredAt = "now",
            ),
        )
        db.attendanceDao().upsert(
            AttendanceEntity(
                id = "att-1a2b3c4d-1111-2222-3333-444455556666", tenantId = "tenant-real",
                studentId = "aaaa0000-1111-2222-3333-444455556666", classId = "cls-4ap",
                date = "2026-10-05", session = "morning", status = "present",
                arrivalTime = null, note = null, recordedBy = "usr-real",
                recordedBy_name = "Real", recordedAt = "now",
            ),
        )

        // THE EVICTION (the pull layer runs this after every cycle on a
        // configured build — invoked directly; the configured-posture gate
        // is pinned by the wiring scan).
        pullRepo.evictDemoRowsAfterPull()

        // ── every DEMO row is gone ──
        for (id in DemoSeedIds.PERSONNEL) assertNull("demo worker $id must go", db.personnelDao().getById(id))
        for (id in DemoSeedIds.DEPARTMENTS) assertNull("demo department $id must go", db.departmentDao().getById(id))
        for (id in DemoSeedIds.PARENTS) assertNull("demo parent $id must go", db.parentDao().getById(id))
        for (id in DemoSeedIds.STUDENTS) assertNull("demo student $id must go", db.studentDao().getById(id))
        assertNull(db.releveEntryDao().getById("rel-t1-1"))
        assertNull(db.releveEntryDao().getById("rel-t2-4"))
        assertNull(db.paymentDao().getById("pay-001"))
        assertNull(db.ledgerEntryDao().getById("led-pay-001"))
        assertNull(db.ledgerEntryDao().getById("led-credit-par-001"))
        assertNull(db.vehicleDao().getById("veh-001"))
        assertNull(db.vehicleDao().getById("veh-002"))
        assertEquals(0, db.routingStopDao().getAll().size)
        // The SEEDED grades are all gone — only the REAL local grade survives.
        assertEquals(1, db.assessmentDao().count())
        // The SEEDED attendance rows are all gone — only the REAL one survives.
        assertEquals(1, db.attendanceDao().countAll())

        // ── every REAL row SURVIVES ──
        assertNotNull(db.personnelDao().getById("3f2a1b8c-1111-2222-3333-444455556666"))
        assertNotNull(db.departmentDao().getById("9a8b7c6d-1111-2222-3333-444455556666"))
        assertNotNull(db.parentDao().getById("11111111-2222-3333-4444-555566667777"))
        assertNotNull(db.studentDao().getById("aaaa0000-1111-2222-3333-444455556666"))
        assertNotNull(db.ledgerEntryDao().getById("led-1a2b3c4d-7e8f-9a0b"))
        assertNotNull(db.installmentDao().getById("ins-aaaa0000-1111-2222-3333-444455556666-t1"))
        assertNotNull(db.paymentDao().getById("pay-1a2b3c4d-1111-2222-3333-444455556666"))
        assertNotNull(db.releveEntryDao().getById("c1d2e3f4-1111-2222-3333-444455556666"))
        assertNotNull(db.assessmentDao().getById("asm-1a2b3c4d-1111-2222-3333-444455556666"))
        assertNotNull(db.attendanceDao().getById("att-1a2b3c4d-1111-2222-3333-444455556666"))
    }

    @Test
    fun `the eviction is idempotent - a second run is a no-op`() = runTest {
        seeder.seedIfEmpty(demoAllowed = true)
        pullRepo.evictDemoRowsAfterPull()
        val personnelAfterFirst = db.personnelDao().countActive()
        pullRepo.evictDemoRowsAfterPull()
        assertEquals(personnelAfterFirst, db.personnelDao().countActive())
    }
}
