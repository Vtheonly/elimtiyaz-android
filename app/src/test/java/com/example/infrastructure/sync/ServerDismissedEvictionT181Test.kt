package com.example.infrastructure.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-181 (T-173 part b / NOTIF-200) — the Android Room `dismissedAt` gap.
 *
 * The defect (registered in the T-173 "Left" note): the server-side alert
 * lifecycle (T-172 — the run-overdue-scan EF resolves overdue alerts by
 * setting `dismissed_at` once the installment is paid) had an Android GAP:
 * Room had NO `dismissedAt` column and NO dismissal eviction, so rows
 * resolved server-side lingered in the local cache FOREVER (the T-172 pull
 * filter `dismissed_at IS NULL` only stops NEW dismissed rows from ENTERING
 * the cache; `evictNotVisibleTo` covers visibility, not dismissal). The
 * Android feed kept showing alerts the server had already resolved.
 *
 * The fix has three parts, verified here by source scan (the
 * PullCompletenessT039Test convention) + JSON round-trip:
 *   1. Room migration v13 → v14 adds the nullable `notifications.dismissedAt`
 *      TEXT column (the real SQLite upgrade path is pinned by
 *      RoomSchemaUpgradeT181Test's MigrationTestHelper).
 *   2. NotificationDto decodes `dismissed_at` and the mapper stores it on
 *      the entity (active pulls keep null — the T-172 filter guarantees
 *      only non-dismissed rows arrive).
 *   3. pullNotifications() evicts rows the server has since dismissed: the
 *      stale candidates (local ids absent from the fresh active pull) are
 *      re-queried ONCE (chunked, URL-bounded) and those now carrying
 *      `dismissed_at` are deleted locally — desktop parity (its repository
 *      filters dismissed_at IS NULL on EVERY read).
 */
class ServerDismissedEvictionT181Test {

    private fun repoSrc(): String {
        val path = File("src/main/java/com/example/infrastructure/sync/PullSyncRepository.kt")
        return path.readText()
    }

    private fun entitySrc(): String {
        return File("src/main/java/com/example/infrastructure/room/LocalEntities.kt").readText()
    }

    private fun dbSrc(): String {
        return File("src/main/java/com/example/infrastructure/room/ElImtiyazDatabase.kt").readText()
    }

    private fun daoSrc(): String {
        return File("src/main/java/com/example/infrastructure/room/LocalDaos.kt").readText()
    }

    private fun dtoSrc(): String {
        return File("src/main/java/com/example/infrastructure/supabase/SharedDtos.kt").readText()
    }

    private fun mapperSrc(): String {
        return File("src/main/java/com/example/infrastructure/supabase/SharedDtoMappers.kt").readText()
    }

    private fun diSrc(): String {
        return File("src/main/java/com/example/di/DatabaseModule.kt").readText()
    }

    private fun pullBlock(): String {
        return Regex("suspend fun pullNotifications[\\s\\S]*?\n    }")
            .find(repoSrc())?.value ?: error("pullNotifications not found")
    }

    // ── 1. The Room schema bump ─────────────────────────────────────────────

    @Test
    fun `room version is 14 with an explicit 13-to-14 migration adding dismissedAt`() {
        assertTrue("the database version must be bumped to 14", dbSrc().contains("version = 14,"))
        val migration = Regex("MIGRATION_13_14[\\s\\S]*?\\n        \\}")
            .find(dbSrc())?.value ?: error("MIGRATION_13_14 not found")
        assertTrue(
            "the v13-to-v14 migration must add the nullable dismissedAt TEXT column",
            migration.contains("ALTER TABLE notifications ADD COLUMN dismissedAt TEXT"),
        )
    }

    @Test
    fun `the 13-to-14 migration is registered in DatabaseModule (no silent destructive fallback)`() {
        assertTrue(
            "MIGRATION_13_14 must be wired into addMigrations — the T-046 discipline forbids the destructive fallback",
            diSrc().contains("ElImtiyazDatabase.MIGRATION_13_14"),
        )
    }

    @Test
    fun `NotificationEntity carries the nullable dismissedAt field`() {
        val entity = Regex("data class NotificationEntity[\\s\\S]*?\\n\\)")
            .find(entitySrc())?.value ?: error("NotificationEntity not found")
        assertTrue(entity.contains("val dismissedAt: String? = null"))
    }

    // ── 2. The DTO + mapper (decode and store the server state) ─────────────

    @Test
    fun `NotificationDto decodes dismissed_at and the mapper stores it on the entity`() {
        val dto = Regex("data class NotificationDto[\\s\\S]*?\\n\\)")
            .find(dtoSrc())?.value ?: error("NotificationDto not found")
        assertTrue(
            "the DTO must decode the server's dismissed_at column",
            dto.contains("""@SerialName("dismissed_at") val dismissedAt: String? = null"""),
        )
        val mapper = Regex("fun NotificationDto\\.toEntity\\(\\)[\\s\\S]*?\\n\\)")
            .find(mapperSrc())?.value ?: error("NotificationDto.toEntity not found")
        assertTrue(
            "the mapper must persist the decoded dismissal timestamp on the entity",
            mapper.contains("dismissedAt = dismissedAt"),
        )
    }

    // ── 3. The pull-side eviction ────────────────────────────────────────────

    @Test
    fun `pullNotifications evicts rows the server has dismissed since the last pull`() {
        val block = pullBlock()
        assertTrue(
            "the eviction must run on every successful pull",
            block.contains("evictServerDismissed("),
        )
        assertTrue(
            "stale candidates = local ids ABSENT from the fresh active pull",
            block.contains("filter { it !in pulledIds }"),
        )
        assertTrue(
            "the candidates are re-queried against the server (one targeted round-trip)",
            block.contains("""isIn("id", chunk)"""),
        )
        assertTrue(
            "only rows whose server-side dismissed_at is set are deleted",
            block.contains("filter { it.dismissedAt != null }"),
        )
        // The eviction must come AFTER the upsert + visibility eviction so it
        // sees the post-pull local state.
        val upsertIdx = block.indexOf("upsertAll(")
        val visibleIdx = block.indexOf("evictNotVisibleTo(")
        val dismissedIdx = block.indexOf("evictServerDismissed(")
        assertTrue(upsertIdx in 0 until dismissedIdx)
        assertTrue(visibleIdx in 0 until dismissedIdx)
    }

    @Test
    fun `the stale-candidate re-query is chunked to keep the PostgREST URL bounded`() {
        val block = pullBlock()
        assertTrue(
            "ids must be chunked (50/query) — a 200-uuid IN filter would blow the URL limit",
            block.contains("chunked(50)"),
        )
    }

    @Test
    fun `the DAO exposes the batch eviction query`() {
        val dao = Regex("interface NotificationDao[\\s\\S]*?\\n\\}")
            .find(daoSrc())?.value ?: error("NotificationDao not found")
        assertTrue(
            "the DAO needs a delete-by-ids query for the server-dismissed eviction",
            dao.contains("DELETE FROM notifications WHERE id IN (:ids)"),
        )
        assertTrue(dao.contains("evictServerDismissed(ids: List<String>)"))
    }

    @Test
    fun `the T-172 entry filter is preserved (new dismissed rows still never enter Room)`() {
        val block = pullBlock()
        assertTrue(
            "the T-172 dismissed_at IS NULL pull filter must remain",
            block.contains("""filter("dismissed_at", FilterOperator.IS, null)"""),
        )
        assertFalse(
            "the eviction must not delete rows merely absent from the 200-row window — only CONFIRMED dismissed ones",
            Regex("evictServerDismissed\\(staleCandidates\\)").containsMatchIn(block),
        )
    }

    // ── 4. JSON round-trip: the DTO decodes dismissed_at ────────────────────

    @Test
    fun `NotificationDto JSON round-trip carries dismissed_at`() {
        val json = """
            {"id":"n-1","kind":"alert","title":"Échéance impayée","priority":"high",
             "source":"overdue_scan","target_user_id":null,"target_role":null,
             "is_read":false,"dismissed_at":"2026-09-04T02:00:00Z",
             "created_at":"2026-09-01T08:00:00Z"}
        """.trimIndent()
        val dto = kotlinx.serialization.json.Json.decodeFromString<
            com.example.infrastructure.supabase.NotificationDto>(json)
        assertEquals("n-1", dto.id)
        assertEquals("2026-09-04T02:00:00Z", dto.dismissedAt)
        // The eviction predicate: this row would be deleted locally.
        assertTrue(dto.dismissedAt != null)
    }

    @Test
    fun `active rows (null dismissed_at) survive the predicate`() {
        val json = """
            {"id":"n-2","kind":"alert","title":"Échéance impayée","priority":"high",
             "source":"overdue_scan","is_read":false}
        """.trimIndent()
        val dto = kotlinx.serialization.json.Json.decodeFromString<
            com.example.infrastructure.supabase.NotificationDto>(json)
        assertEquals(null, dto.dismissedAt)
        assertFalse(dto.dismissedAt != null)
    }
}
