package com.example.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-460 / android issue #3, finding F-01 — the ProfileScreen sign-out
 * canonical-path regression pin.
 *
 * The audit found that ProfileViewModel.signOut() called only
 * `sessionManager.setSession(null)` while the Personnel hub's sign-out went
 * through `authRepository.signOut()` (which deactivates this device's FCM
 * tokens BEFORE revoking the JWT — the AGENTS.md §3 FCM lifecycle contract).
 * A signed-out device kept receiving push notifications when the user signed
 * out from the Profile screen.
 *
 * This source-anchored suite pins the fixed order: BOTH sign-out surfaces
 * (ProfileViewModel and MainScreen's ViewModel) must call
 * authRepository.signOut() before clearing the local session.
 */
class ProfileSignOutCanonicalPathT460Test {

    private fun profileScreenSource(): String {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(cwd, "src/main/java/com/example/ui/features/profile/ProfileScreen.kt"),
            File(cwd.parentFile ?: cwd, "src/main/java/com/example/ui/features/profile/ProfileScreen.kt"),
        )
        return candidates.first { it.exists() }.readText()
    }

    private fun mainScreenSource(): String {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(cwd, "src/main/java/com/example/ui/features/main/MainScreen.kt"),
            File(cwd.parentFile ?: cwd, "src/main/java/com/example/ui/features/main/MainScreen.kt"),
        )
        return candidates.first { it.exists() }.readText()
    }

    @Test
    fun `ProfileViewModel sign-out deactivates FCM before clearing the session`() {
        val src = profileScreenSource()
        val signOutIdx = src.indexOf("fun signOut(")
        assertTrue("ProfileViewModel must define signOut()", signOutIdx >= 0)
        val body = src.substring(signOutIdx, minOf(signOutIdx + 900, src.length))
        val authCall = body.indexOf("authRepository.signOut()")
        val sessionClear = body.indexOf("sessionManager.setSession(null)")
        assertTrue(
            "the sign-out body must call authRepository.signOut() (FCM deactivation + JWT revoke)",
            authCall >= 0,
        )
        assertTrue(
            "the sign-out body must still clear the local session",
            sessionClear >= 0,
        )
        assertTrue(
            "authRepository.signOut() must run BEFORE sessionManager.setSession(null) (the canonical order — the AGENTS.md §3 FCM lifecycle contract)",
            authCall < sessionClear,
        )
    }

    @Test
    fun `MainScreen's sign-out keeps the same canonical order (the two paths must not diverge again)`() {
        val src = mainScreenSource()
        val signOutIdx = src.indexOf("fun signOut(")
        assertTrue("MainScreen's ViewModel must define signOut()", signOutIdx >= 0)
        val body = src.substring(signOutIdx, minOf(signOutIdx + 600, src.length))
        val authCall = body.indexOf("authRepository.signOut()")
        val sessionClear = body.indexOf("sessionManager.setSession(null)")
        assertTrue(authCall in 0 until sessionClear)
    }
}
