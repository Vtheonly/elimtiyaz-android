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
 * T-460 pass J (F-11, the session-surface consolidation) UPDATED this pin:
 * the Personnel hub's "Session" tab is now a REDIRECT to ProfileScreen (no
 * sign-out path of its own), so the canonical-path population is exactly
 * {ProfileViewModel, SettingsViewModel}. The MainScreen assertion flipped
 * from "must define signOut()" to "must NOT define signOut()" — the guard
 * against a third parallel path growing back.
 */
class ProfileSignOutCanonicalPathT460Test {

    private fun source(relPath: String): String {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(cwd, "src/main/java/com/example/$relPath"),
            File(cwd.parentFile ?: cwd, "src/main/java/com/example/$relPath"),
        )
        return candidates.first { it.exists() }.readText()
    }

    private fun profileScreenSource() = source("ui/features/profile/ProfileScreen.kt")
    private fun mainScreenSource() = source("ui/features/main/MainScreen.kt")
    private fun settingsViewModelSource() = source("ui/features/settings/SettingsViewModel.kt")
    private fun signOutScreenSource() = source("ui/features/personnel/SignOutScreen.kt")

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
    fun `SettingsViewModel sign-out keeps the same canonical order`() {
        val src = settingsViewModelSource()
        val signOutIdx = src.indexOf("fun signOut(")
        assertTrue("SettingsViewModel must define signOut()", signOutIdx >= 0)
        val body = src.substring(signOutIdx, minOf(signOutIdx + 600, src.length))
        val authCall = body.indexOf("authRepository.signOut()")
        val sessionClear = body.indexOf("sessionManager.setSession(null)")
        assertTrue(authCall in 0 until sessionClear)
    }

    @Test
    fun `MainScreen has NO sign-out path of its own (the F-11 consolidation - the Personnel tab redirects)`() {
        val src = mainScreenSource()
        assertTrue(
            "MainScreen's ViewModel must not define signOut() — the single session surface is ProfileScreen (issue #3 F-11); a third parallel path must not grow back",
            "fun signOut(" !in src,
        )
    }

    @Test
    fun `the SignOutScreen redirect targets the Profile surface and carries no sign-out of its own`() {
        val src = signOutScreenSource()
        assertTrue(
            "SignOutScreen must navigate to the Profile surface (the consolidation's redirect contract)",
            "onNavigateToProfile" in src,
        )
        assertTrue(
            "SignOutScreen must not define a sign-out path (the single sign-out lives on ProfileScreen; the docblock's canonical-path description is fine)",
            "onSignOut" !in src && "fun signOut(" !in src,
        )
        assertTrue(
            "the redirect shows the FRENCH role label (not the raw role code — the F-11 vocabulary fix)",
            "roleLabel(session.role)" in src,
        )
    }
}
