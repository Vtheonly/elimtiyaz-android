package com.example.infrastructure.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.infrastructure.room.ElImtiyazDatabase
import com.example.infrastructure.room.HomeworkEntity
import com.example.infrastructure.room.NotificationEntity
import com.example.infrastructure.supabase.AssessmentDto
import com.example.infrastructure.supabase.AttendanceRecordDto
import com.example.infrastructure.supabase.HomeworkDto
import com.example.infrastructure.supabase.NotificationDto
import com.example.infrastructure.supabase.toEntity
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * T-039 / HOMEWORK-103 + NOTIF-105 — Android pull-sync completeness.
 *
 * The defects:
 *  - HOMEWORK-103: `pullAll` fetched ONLY the financial cluster
 *    (parents/students/payments/ledger/installments + org tables). The
 *    canonical academic tables — `homework` (migration 0029),
 *    `attendance_records` + `assessments` (migration 0041) — were never
 *    pulled, so homework/roll-call/grades created on the DESKTOP never
 *    appeared on Android: cross-platform visibility was one-way only.
 *    Every pull also upserted ROW BY ROW (O(N) Room round-trips).
 *  - NOTIF-105: `pullNotifications` pulled ALL server-visible notifications
 *    with no per-user filter, and NOTHING was ever evicted — role-broadcast
 *    rows stayed in Room forever across role changes (a user whose role
 *    changed kept seeing the old role's broadcasts).
 *
 * Fix under test:
 *  1. pullAll pulls the academic cluster (homework / attendance_records /
 *     assessments) and batch-upserts every kind (single Room round-trip);
 *  2. pulled homework rows delete their legacy `hwk-` prefixed local twins
 *     (pre-T-024 local rows that could never reach the server — T-024);
 *  3. pullNotifications mirrors the server's `notifications_select` RLS
 *     policy (migration 0019) branch-for-branch: direct rows for the
 *     profile id, role-broadcasts for ANY held role (fresh
 *     `current_user_roles()` RPC — the Session models only ONE role), and
 *     tenant broadcasts (NULL/NULL) only for the staff trio 0019 names;
 *  4. `evictNotVisibleTo` removes every row the user can no longer see —
 *     the NOTIF-105 stale-cache fix — using the SAME visibility predicate
 *     as the RLS policy (pinned on a real SQLite file).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PullCompletenessT039Test {

    private lateinit var context: Context
    private lateinit var db: ElImtiyazDatabase

    private fun repoSrc(): String =
        File("src/main/java/com/example/infrastructure/sync/PullSyncRepository.kt").readText()

    private fun daosSrc(): String =
        File("src/main/java/com/example/infrastructure/room/LocalDaos.kt").readText()

    private fun dbSrc(): String =
        File("src/main/java/com/example/infrastructure/room/ElImtiyazDatabase.kt").readText()

    private fun moduleSrc(): String =
        File("src/main/java/com/example/di/DatabaseModule.kt").readText()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, ElImtiyazDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ── HOMEWORK-103 — the academic cluster enters the pull cycle ──────────

    @Test
    fun `pullAll wires homework, attendance and assessments into the cycle`() {
        val block = Regex("private suspend fun doPullAll[\\s\\S]*?\\n    \\}")
            .find(repoSrc())?.value ?: error("doPullAll not found")
        for (call in listOf("pullHomework()", "pullAttendance()", "pullAssessments()")) {
            assertTrue(
                "doPullAll must call $call — desktop/other-device academic writes must reach this device",
                block.contains(call),
            )
        }
        for (v in listOf("hwk", "att", "asm")) {
            assertTrue(
                "the pull total must include the academic cluster ($v)",
                Regex("val total = [\\s\\S]*\\+ $v").containsMatchIn(block),
            )
        }
    }

    @Test
    fun `academic pulls read the canonical tables and batch-upsert`() {
        val src = repoSrc()
        val hwk = Regex("suspend fun pullHomework[\\s\\S]*?\n    }").find(src)?.value
            ?: error("pullHomework not found")
        assertTrue(
            "pullHomework must read the canonical homework table (migration 0029)",
            hwk.contains("""from("homework")"""),
        )
        assertTrue("pullHomework must batch-upsert (single Room round-trip)", hwk.contains("upsertAll"))

        val att = Regex("suspend fun pullAttendance[\\s\\S]*?\n    }").find(src)?.value
            ?: error("pullAttendance not found")
        assertTrue(
            "pullAttendance must read the canonical attendance_records table (migration 0041)",
            att.contains("""from("attendance_records")"""),
        )
        assertTrue(att.contains("upsertAll"))

        val asm = Regex("suspend fun pullAssessments[\\s\\S]*?\n    }").find(src)?.value
            ?: error("pullAssessments not found")
        assertTrue(
            "pullAssessments must read the canonical assessments table (migration 0041)",
            asm.contains("""from("assessments")"""),
        )
        assertTrue(asm.contains("upsertAll"))
    }

    @Test
    fun `no per-row upsert loops remain in the pull repository`() {
        val src = repoSrc()
        assertFalse(
            "every pull path must batch-upsert; per-row `for (dto in …) upsert(` loops are gone",
            Regex("for\\s*\\(\\s*dto\\s+in[\\s\\S]*?upsert\\(").containsMatchIn(src),
        )
        assertTrue(
            "the departments pull must batch the whole list (was a per-row listOf wrapper)",
            src.contains("db.departmentDao().upsertAll(dtoList.map { it.toEntity() })"),
        )
    }

    // ── Legacy homework prefix twins (T-024 interplay) ──────────────────────

    @Test
    fun `pull deletes the legacy hwk-prefixed twin of every pulled homework row`() {
        val hwk = Regex("suspend fun pullHomework[\\s\\S]*?\n    }")
            .find(repoSrc())?.value ?: error("pullHomework not found")
        assertTrue(
            "after upserting the server row, its legacy hwk- prefixed local twin must be deleted",
            hwk.contains("deleteLegacyPrefixedCopy(it.id)"),
        )
    }

    @Test
    fun `legacy prefixed twin deletion - real SQLite semantics`() = runBlocking {
        val bareId = "11111111-1111-1111-1111-111111111111"
        db.homeworkDao().upsertAll(
            listOf(
                hwkRow("hwk-$bareId", "Old local row"),
                hwkRow(bareId, "Server row"),
            ),
        )
        db.homeworkDao().deleteLegacyPrefixedCopy(bareId)
        assertNull("the legacy prefixed twin must be gone", db.homeworkDao().getById("hwk-$bareId"))
        assertEquals(
            "the bare-UUID server row must survive (the same logical assignment, deduped)",
            "Server row",
            db.homeworkDao().getById(bareId)?.title,
        )
    }

    // ── Academic pull mappers (canonical shapes round-trip) ─────────────────

    @Test
    fun `homework mapper - attachments jsonb becomes a JSON string, absent becomes empty array`() {
        val withFiles = HomeworkDto(
            id = "h1", tenantId = "t1", classId = "c1", subjectId = "s1",
            subjectName = "Maths", teacherId = "t-1", teacherName = "M. Ali",
            title = "Devoir 1", description = "Chapitre 1", dueDate = "2026-09-15",
            academicYear = "2026-2027",
            attachments = Json.parseToJsonElement("""[{"name":"fiche.pdf"}]"""),
            pushedAt = "2026-09-01T10:00:00Z", createdAt = "2026-09-01T09:00:00Z",
        ).toEntity()
        assertEquals("""[{"name":"fiche.pdf"}]""", withFiles.attachmentsJson)
        assertEquals("2026-2027", withFiles.academicYear)
        assertEquals("2026-09-01T10:00:00Z", withFiles.pushedAt)

        val bare = HomeworkDto(
            id = "h2", tenantId = "t1", classId = "c1", subjectId = "s1",
            subjectName = "Maths", teacherId = "t-1", teacherName = "M. Ali",
            title = "Devoir 2", description = "Chapitre 2", dueDate = "2026-09-22",
        ).toEntity()
        assertEquals("attachments default must be an empty JSON array", "[]", bare.attachmentsJson)
    }

    @Test
    fun `attendance mapper - record_date wins, legacy date is the fallback`() {
        val dto = AttendanceRecordDto(
            id = "a1", tenantId = "t1", studentId = "st1", classId = "c1",
            date = "2020-01-01", recordDate = "2026-09-01",
            session = "morning", status = "present",
            arrivalTime = "08:02", note = null, recordedBy = "u1",
            createdAt = "2026-09-01T08:05:00Z",
        ).toEntity()
        assertEquals("the 0041 column record_date must be preferred", "2026-09-01", dto.date)
        assertEquals("present", dto.status)
        assertEquals("morning", dto.session)
        assertEquals("08:02", dto.arrivalTime)
        assertEquals("the server row has no recorded-by NAME — stays empty for pulled rows", "", dto.recordedBy_name)

        val legacy = AttendanceRecordDto(
            id = "a2", tenantId = "t1", studentId = "st1", classId = "c1",
            date = "2026-08-31", session = "afternoon", status = "late",
        ).toEntity()
        assertEquals("legacy rows without record_date fall back to date", "2026-08-31", legacy.date)
    }

    @Test
    fun `assessment mapper - term wire mapping, coerced 1-3, coefficient defaults`() {
        fun dto(term: Int) = AssessmentDto(
            id = "as-$term", tenantId = "t1", studentId = "st1", subjectId = "su1",
            classId = "c1", term = term, academicYear = "2026-2027",
            devoir1 = 15.0, devoir2 = 16.0, examen = 17.0, subjectAverage = 16.25,
        ).toEntity()
        assertEquals("T1", dto(1).term)
        assertEquals("T2", dto(2).term)
        assertEquals("T3", dto(3).term)
        assertEquals("the DB stores INTEGER terms; out-of-range values coerce like the server (GREATEST(1, LEAST(3, …)))", "T3", dto(9).term)
        assertEquals("T1", dto(0).term)

        val e = dto(1)
        assertEquals("2026-2027", e.academicYear)
        assertEquals(15.0, e.devoir1!!, 0.001)
        assertEquals("0041 defaults: c1=1.0, c2=1.0, examen=2.0", 1.0, e.coefficientDevoir1, 0.001)
        assertEquals(1.0, e.coefficientDevoir2, 0.001)
        assertEquals(2.0, e.coefficientExamen, 0.001)
    }

    // ── NOTIF-105 — filter mirrors the 0019 RLS policy ──────────────────────

    @Test
    fun `notification pull resolves the FULL role set via the canonical RPC, not the single session role`() {
        val block = Regex("suspend fun pullNotifications[\\s\\S]*?\n    }")
            .find(repoSrc())?.value ?: error("pullNotifications not found")
        assertTrue(
            "roles must be re-resolved fresh via current_user_roles() — the same RPC the 0019 policy uses",
            block.contains("""rpc("current_user_roles")"""),
        )
        assertTrue(
            "the session's single role is only the FALLBACK when the RPC is unreachable",
            Regex("ifEmpty\\s*\\{\\s*listOf\\(session\\.role\\.code\\)\\s*\\}").containsMatchIn(block),
        )
        assertTrue(
            "the filter must match direct rows by the PROFILE id (target_user_id = user_profiles.id)",
            block.contains("""eq("target_user_id", session.userId)"""),
        )
        assertTrue(
            "role-broadcasts must match ANY held role (isIn), not just the primary one",
            block.contains("""isIn("target_role", roles)"""),
        )
        assertTrue(
            "eviction must run on every successful pull",
            block.contains("evictNotVisibleTo("),
        )
        // The signed-out pull returns zero WITHOUT touching the network or Room.
        assertTrue(
            "a signed-out pull must return Ok(0) immediately",
            Regex("return@withContext Result\\.Ok\\(0\\)").containsMatchIn(block),
        )
    }

    @Test
    fun `staff broadcast trio mirrors the 0019 notifications_select policy`() {
        val src = repoSrc()
        assertTrue(src.contains("super_admin"))
        assertTrue(src.contains("financial_officer"))
        assertTrue(src.contains("support_staff"))
        val set = Regex("STAFF_BROADCAST_ROLES: Set<String> =\\s*setOf\\(([\\s\\S]*?)\\)")
            .find(src)?.groupValues?.get(1) ?: error("STAFF_BROADCAST_ROLES not found")
        assertEquals(
            "the tenant-broadcast visibility trio must mirror 0019 exactly (3 roles)",
            3,
            Regex("\"[a-z_]+\"").findAll(set).count(),
        )
        assertFalse(
            "teachers do NOT see tenant broadcasts under 0019 — must not be added here",
            "teacher" in set,
        )
    }

    @Test
    fun `notification mapper preserves targetRole for eviction`() {
        val dto = NotificationDto(
            id = "n1", tenantId = "t1", kind = "info", title = "Réunion",
            body = "Réunion demain", priority = "medium", source = "manual",
            targetUserId = null, targetRole = "teacher",
        ).toEntity()
        assertEquals("teacher", dto.targetRole)

        val direct = NotificationDto(
            id = "n2", tenantId = "t1", kind = "info", title = "Direct",
            targetUserId = "profile-1", targetRole = null,
        ).toEntity()
        assertNull(direct.targetRole)
    }

    // ── NOTIF-105 — eviction semantics on a real SQLite file ────────────────

    private fun notif(id: String, targetUserId: String?, targetRole: String?) = NotificationEntity(
        id = id, tenantId = "t1", title = id, body = "", type = "info",
        priority = "medium", source = "system", sourceLabel = "", entityType = null,
        entityId = null, targetUserId = targetUserId, targetRole = targetRole,
        isRead = false, createdAt = "2026-09-01T00:00:00Z",
    )

    @Test
    fun `eviction - role change keeps my direct rows and my roles, evicts everything else`() = runBlocking {
        db.notificationDao().upsertAll(
            listOf(
                notif("direct-me", "me", null),
                notif("direct-other", "someone-else", null),
                notif("bc-teacher", null, "teacher"),
                notif("bc-manager", null, "manager"),
                notif("bc-tenant", null, null),
            ),
        )
        // User "me" now holds only TEACHER (0019 trio NOT included).
        db.notificationDao().evictNotVisibleTo("me", listOf("teacher"), 0)

        val remaining = db.notificationDao().listAll().map { it.id }.toSet()
        assertEquals(
            "visible set = my direct rows + my role's broadcasts (tenant broadcast hidden for non-staff)",
            setOf("direct-me", "bc-teacher"),
            remaining,
        )
    }

    @Test
    fun `eviction - multi-role users keep broadcasts for EVERY held role`() = runBlocking {
        db.notificationDao().upsertAll(
            listOf(
                notif("bc-teacher", null, "teacher"),
                notif("bc-financial", null, "financial_officer"),
                notif("bc-driver", null, "driver"),
            ),
        )
        // A teacher + financial_officer user (the Session models only the
        // primary role — the pull resolves the full set via the RPC).
        db.notificationDao().evictNotVisibleTo("me", listOf("teacher", "financial_officer"), 1)

        val remaining = db.notificationDao().listAll().map { it.id }.toSet()
        assertEquals(
            "both held roles' broadcasts survive; the unheld role is evicted",
            setOf("bc-teacher", "bc-financial"),
            remaining,
        )
    }

    @Test
    fun `eviction - staff trio keeps tenant broadcasts, non-staff loses them`() = runBlocking {
        db.notificationDao().upsertAll(listOf(notif("bc-tenant", null, null)))

        db.notificationDao().evictNotVisibleTo("me", listOf("teacher"), 0)
        assertTrue(
            "0019 hides NULL/NULL broadcasts from teachers — the cache must evict them",
            db.notificationDao().listAll().isEmpty(),
        )

        db.notificationDao().upsertAll(listOf(notif("bc-tenant", null, null)))
        db.notificationDao().evictNotVisibleTo("me", listOf("support_staff"), 1)
        assertEquals(
            "0019 shows NULL/NULL broadcasts to the staff trio (support_staff here)",
            listOf("bc-tenant"),
            db.notificationDao().listAll().map { it.id },
        )
    }

    @Test
    fun `eviction - unread state and read rows of MINE are never touched`() = runBlocking {
        db.notificationDao().upsertAll(
            listOf(notif("direct-me", "me", null).copy(isRead = true)),
        )
        db.notificationDao().evictNotVisibleTo("me", listOf("teacher"), 0)
        val row = db.notificationDao().listAll().first()
        assertTrue("my rows survive with their state intact", row.isRead)
    }

    // ── Room v13 — the targetRole column makes eviction possible ────────────

    @Test
    fun `room v13 - notifications carries the targetRole column and the migration is registered`() {
        // The column exists on the REAL schema (fresh open = compiled v13).
        val cursor = db.openHelper.readableDatabase
            .query("PRAGMA table_info(notifications)")
        val cols = mutableListOf<String>()
        while (cursor.moveToNext()) cols.add(cursor.getString(1))
        cursor.close()
        assertTrue("the notifications table must carry targetRole (Room v13)", "targetRole" in cols)

        // The explicit 12→13 migration exists and adds exactly that column.
        val migration = Regex("val MIGRATION_12_13[\\s\\S]*?\\n        \\}")
            .find(dbSrc())?.value ?: error("MIGRATION_12_13 not found")
        assertTrue(
            "the migration must ALTER TABLE notifications ADD COLUMN targetRole TEXT",
            migration.contains("ALTER TABLE notifications ADD COLUMN targetRole TEXT"),
        )
        assertTrue(
            "DatabaseModule must register MIGRATION_12_13 (REG-002 discipline)",
            moduleSrc().contains("ElImtiyazDatabase.MIGRATION_12_13"),
        )
        assertTrue(
            "the DAO eviction must consult the role list, not a single role name",
            daosSrc().contains("targetRole IN (:roles)"),
        )
    }
}

/** Minimal [HomeworkEntity] factory for the Room tests. */
private fun hwkRow(id: String, title: String) = HomeworkEntity(
    id = id, tenantId = "t1", classId = "c1", subjectId = "s1",
    subjectName = "Maths", teacherId = "t-1", teacherName = "M. Ali",
    title = title, description = "", dueDate = "2026-09-15",
    attachmentsJson = "[]", createdAt = "2026-09-01T09:00:00Z",
)
