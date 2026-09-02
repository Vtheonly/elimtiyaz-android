package com.example.ui.features.chat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-102-follow-up / ANDR-CHAT-200 — chat WIRING source scans.
 *
 * Pins the structural decisions a unit test cannot reach:
 *   1. the routes exist and are RBAC-gated on USE_CHAT;
 *   2. the AppNavHost wires both screens through rbacGate;
 *   3. the DI binds ChatRepository to the Supabase implementation (NOT the
 *      Local* layer — chat is online-only v1);
 *   4. the dashboard exposes the Messagerie quick action;
 *   5. NO channel-creation UI exists on Android (ADR-008: staff create
 *      channels from the desktop's parent-detail drawer; the Android app
 *      is read + reply only, same as the parent portal).
 */
class ChatWiringScanTest {

    private fun read(rel: String): String {
        val candidates = listOf(
            File(rel),
            File("../$rel"),
            File("app/src/main/java/$rel"),
            File("../app/src/main/java/$rel"),
        )
        val found = candidates.firstOrNull { it.exists() }
            ?: error("Cannot locate $rel (cwd=${File(".").absolutePath})")
        return found.readText()
    }

    @Test
    fun `chat routes exist and are permission-gated`() {
        val routes = read("com/example/ui/navigation/Routes.kt")
        assertTrue(routes.contains("@Serializable object Chat : Route"))
        assertTrue(routes.contains("@Serializable data class ChatDetail("))
        val perms = read("com/example/ui/navigation/Routes.kt")
        assertTrue(perms.contains("Routes.Chat::class to Permission.USE_CHAT"))
        assertTrue(perms.contains("Routes.ChatDetail::class to Permission.USE_CHAT"))
    }

    @Test
    fun `AppNavHost wires both chat screens through rbacGate`() {
        val nav = read("com/example/ui/navigation/AppNavHost.kt")
        assertTrue(nav.contains("composable<Routes.Chat>"))
        assertTrue(nav.contains("composable<Routes.ChatDetail>"))
        assertTrue(nav.contains("rbacGate(navController, Routes.Chat::class)"))
        assertTrue(nav.contains("rbacGate(navController, Routes.ChatDetail::class)"))
        assertTrue(nav.contains("onNavigateToChat = { navController.navigate(Routes.Chat) }"))
    }

    @Test
    fun `DI binds ChatRepository to the Supabase implementation`() {
        val di = read("com/example/di/SupabaseModule.kt")
        assertTrue(di.contains("fun provideChatRepository"))
        assertTrue(di.contains("SupabaseChatRepository"))
    }

    @Test
    fun `the dashboard exposes the Messagerie quick action`() {
        val quick = read("com/example/ui/features/dashboard/DashboardQuickActionsRow.kt")
        assertTrue(quick.contains("\"Messagerie\""))
        val hub = read("com/example/ui/features/dashboard/DashboardHubScreen.kt")
        assertTrue(hub.contains("onNavigateToChat"))
        val main = read("com/example/ui/features/main/MainScreen.kt")
        assertTrue(main.contains("onNavigateToChat: () -> Unit"))
    }

    @Test
    fun `NO channel-creation UI on Android (ADR-008 - read and reply only)`() {
        val repo = read("com/example/domain/repository/ChatRepository.kt")
        assertFalse("the chat repository must NOT expose channel creation", repo.contains("createChannel"))
        assertFalse(repo.contains("create_direct_channel"))
        val screen = read("com/example/ui/features/chat/ChatScreen.kt")
        assertFalse("the channel list must not render a create-channel action", screen.contains("Créer"))
    }

    @Test
    fun `the chat repository queries the canonical tables with website semantics`() {
        val repo = read("com/example/infrastructure/supabase/SupabaseChatRepository.kt")
        // membership via array-contains on member_ids (the website's .contains)
        assertTrue(repo.contains("FilterOperator.CS"))
        // archived hidden + last-activity ordering (CHAT-104, migration 0061)
        assertTrue(repo.contains("\"archived_at\", FilterOperator.IS, null"))
        assertTrue(repo.contains("order(\"last_message_at\", Order.DESCENDING, false)"))
        // messages: deleted hidden, oldest first
        assertTrue(repo.contains("\"deleted_at\", FilterOperator.IS, null"))
        assertTrue(repo.contains("order(\"sent_at\", Order.ASCENDING"))
        // send = direct insert; markRead = append own receipt (0051)
        assertTrue(repo.contains("from(\"chat_messages\").insert(row)"))
        assertTrue(repo.contains("update(patch)"))
    }
}
