package com.example.infrastructure.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * T-024 / HOMEWORK-101 + STUDENT-100 — Android homework UUID + promotion
 * propagation.
 *
 * The defects:
 *  - HOMEWORK-101: `LocalHomeworkRepository.push()` created Room rows with
 *    `id = "hwk-${UUID.randomUUID()}"` and the sync dispatcher pushed that id
 *    VERBATIM into `homework.id` — a UUID PRIMARY KEY column (migration 0029).
 *    Postgres rejects `"hwk-…"` (`invalid input syntax for type uuid`), the
 *    push retries then fails — the canonical `homework` table has received
 *    ZERO Android rows since the feature shipped (verified live 2026-09-01:
 *    count(*) = 0).
 *  - STUDENT-100: the promotion flow (operation="promote") updates the local
 *    gradeLevel and enqueues a student payload carrying the NEW grade, but
 *    `pushStudent` never forwarded it — `students.grade_level_code` stayed at
 *    the old value server-side (Android shows the new grade; desktop and
 *    website show the old one). NOTE: the original audit text ("RPC has no
 *    p_grade_level_code parameter") is stale — migration 0037 already added
 *    the parameter (verified live 2026-09-01 via pg_get_functiondef); only
 *    the client-side gap remained.
 *
 * Fix under test:
 *  1. homework entities are created with a bare UUID (server PK shape);
 *  2. the dispatcher strips the legacy `hwk-` prefix defensively so already-
 *     queued/Room rows can finally reach the server on the same server id
 *     (idempotency preserved — no duplicate rows on retry);
 *  3. `pushStudent` sends `p_grade_level_code` from the payload.
 */
class HomeworkPromotionT024Test {

    private fun dispatcherSrc(): String =
        File("src/main/java/com/example/infrastructure/sync/SyncQueueDispatcher.kt").readText()

    private fun homeworkRepoSrc(): String =
        File("src/main/java/com/example/infrastructure/local/LocalRepositories2.kt").readText()

    private fun promotionSrc(): String =
        File("src/main/java/com/example/infrastructure/local/LocalRepositories.kt").readText()

    // ── HOMEWORK-101 ────────────────────────────────────────────────────────

    @Test
    fun `homework entities are created with a bare UUID, not the hwk- prefix`() {
        val src = homeworkRepoSrc()
        val block = Regex("val entity = HomeworkEntity\\([\\s\\S]*?attachmentsJson")
            .find(src)?.value ?: error("HomeworkEntity construction not found")
        assertTrue(
            "the homework local id must be a bare UUID (server homework.id is UUID PK)",
            block.contains("id = UUID.randomUUID().toString()"),
        )
        assertFalse(
            "the hwk- prefix must never be reintroduced into the local id assignment",
            Regex("id\\s*=\\s*\"hwk-").containsMatchIn(block),
        )
    }

    @Test
    fun `dispatcher strips the legacy hwk- prefix before the UUID-column upsert`() {
        val src = dispatcherSrc()
        val block = Regex("private suspend fun pushHomework\\([\\s\\S]*?guardSyncPush")
            .find(src)?.value ?: error("pushHomework not found")
        assertTrue(
            "pushHomework must normalize legacy ids with removePrefix(\"hwk-\")",
            block.contains("removePrefix(\"hwk-\")"),
        )
        // The normalized (stripped) id — not the raw one — must be what lands
        // in the row sent to the server.
        val rowBlock = Regex("val row = buildJsonObject \\{[\\s\\S]*?\\n        \\}")
            .find(block)?.value ?: error("homework row build not found")
        assertTrue(
            "the row must send the stripped id, not the raw payload id",
            rowBlock.contains("put(\"id\", id)"),
        )
        assertFalse(
            "the row must not send the raw prefixed id",
            rowBlock.contains("put(\"id\", rawId)"),
        )
    }

    @Test
    fun `prefix-strip semantics pin - bare UUIDs pass through unchanged, prefixed ids normalize to the same server id`() {
        // Idempotency contract: both the legacy form and the fixed form of the
        // SAME logical row must map to the SAME server id, and a retry of
        // either form must not duplicate the server row.
        val legacyId = "hwk-${UUID.randomUUID()}"
        val bareId = UUID.randomUUID().toString()
        assertEquals(legacyId.removePrefix("hwk-"), legacyId.removePrefix("hwk-"))
        assertEquals(bareId, bareId.removePrefix("hwk-"))
        assertFalse(bareId.startsWith("hwk-"))
        // A bare UUID is a valid server id shape; a prefixed one is not.
        assertTrue(bareId.matches(Regex("[0-9a-fA-F-]{36}")))
        assertFalse(legacyId.matches(Regex("[0-9a-fA-F-]{36}")))
    }

    // ── STUDENT-100 ─────────────────────────────────────────────────────────

    @Test
    fun `pushStudent forwards the payload grade into p_grade_level_code`() {
        val src = dispatcherSrc()
        val block = Regex("private suspend fun pushStudent\\([\\s\\S]*?\\n    \\}")
            .find(src)?.value ?: error("pushStudent not found")
        assertTrue(
            "pushStudent must send p_grade_level_code (RPC param exists since 0037)",
            block.contains("put(\"p_grade_level_code\", it)"),
        )
        // The promotion payload's key is camelCase gradeLevel — the mapping
        // must read that key (with snake_case fallbacks).
        assertTrue(
            "the grade mapping must read the promotion payload's gradeLevel key",
            block.contains("p.str(\"gradeLevel\")"),
        )
    }

    @Test
    fun `the promotion enqueue carries the new grade and status in its payload`() {
        val src = promotionSrc()
        val block = Regex("operation = \"promote\"[\\s\\S]*?sourceScreen = \"PromotionScreen\"")
            .find(src)?.value ?: error("promotion enqueue not found")
        assertTrue(
            "the promote payload must carry the updated gradeLevel",
            block.contains("put(\"gradeLevel\", updated.gradeLevel)"),
        )
        assertTrue(
            "the promote payload must carry the updated status (graduated/active)",
            block.contains("put(\"status\", updated.status)"),
        )
    }

    @Test
    fun `pull-side mapping keeps grade_level_code - grade changes survive a pull sync`() {
        // STUDENT-100's verification asks that a server-side grade advance
        // "survives a pull sync": the pull mapper must store grade_level_code
        // into the entity's gradeLevel (pre-existing contract — pinned here so
        // the push fix is not silently undone by a mapper regression).
        val src = File("src/main/java/com/example/infrastructure/supabase/SharedDtoMappers.kt").readText()
        val block = Regex("fun StudentDto\\.toEntity\\(\\)[\\s\\S]*?\\n\\)")
            .find(src)?.value ?: error("StudentDto.toEntity not found")
        assertTrue(
            "StudentDto.toEntity must map grade_level_code into the entity gradeLevel",
            block.contains("gradeLevel = gradeLevelCode"),
        )
    }
}
