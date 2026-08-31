package com.example.infrastructure.supabase

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T-064 regression suite — Android config-dialog + client-provider security.
 *
 * SEC-004: the SupabaseConfigDialog showed the anon key in PLAIN TEXT and
 * told end users about the "Google AI Studio" secrets panel (internal
 * toolchain leak in user-facing text).
 * SEC-005: SupabaseClientProvider.build() fell back to the PUBLIC
 * `https://demo.supabase.co` endpoint with key "demo-key" when unconfigured
 * (both in the URL/key normalization AND the exception handler) — an
 * unconfigured app pinged a live third-party endpoint on every cold start.
 *
 * The fix: the key field is masked (PasswordVisualTransformation + a
 * show/hide toggle), the helper text mentions only .env, and the inert
 * fallback host is `supabase.unconfigured.invalid` (RFC 2606 reserved TLD —
 * can never resolve, so unconfigured builds make ZERO network calls to any
 * real host). The composable is UI (needs composition to exercise); the
 * wiring is pinned by source-scan, the fallback constants asserted
 * directly, and the demo-host absence scanned across the module.
 */
class SupabaseConfigSecurityT064Test {

    @Test
    fun `the inert fallback host is RFC-2606 reserved and unresolvable`() {
        val url = SupabaseClientProvider.INERT_FALLBACK_URL
        assertTrue("must use the reserved .invalid TLD: $url", url.endsWith(".invalid"))
        assertFalse("must not reference any real supabase host", url.contains("supabase.co"))
        assertFalse("must not reference demo hosts", url.contains("demo"))
    }

    @Test
    fun `the inert fallback key is a dummy, never a real-looking credential`() {
        val key = SupabaseClientProvider.INERT_FALLBACK_KEY
        assertFalse(key.isBlank())
        assertFalse("must not be the old demo-key literal", key.equals("demo-key", ignoreCase = true))
    }

    @Test
    fun `the provider no longer references the public demo endpoint`() {
        val src = readMainSource("infrastructure/supabase/SupabaseClientProvider.kt")
        assertFalse(
            "SEC-005: demo.supabase.co must not appear as a fallback literal",
            src.contains("\"https://demo.supabase.co\""),
        )
        // The old VALUE-fallbacks must be gone. ("demo-key" still appears in
        // isPlaceholderKey — that is the DETECTION list, which is wanted.)
        assertFalse(
            "the old key fallback assignment must be gone",
            src.contains("else \"demo-key\"") || src.contains("supabaseKey = \"demo-key\""),
        )
        // The exception path must ALSO use the inert host.
        assertTrue("the catch branch must build the inert client", src.contains("supabaseUrl = inertUrl"))
    }

    @Test
    fun `the config dialog masks the anon key with a show-hide toggle`() {
        val src = readMainSource("ui/features/settings/SupabaseConfigDialog.kt")
        assertTrue(
            "SEC-004: PasswordVisualTransformation must be applied",
            src.contains("PasswordVisualTransformation()"),
        )
        assertTrue(
            "SEC-004: a visibility toggle must exist",
            src.contains("keyVisible"),
        )
    }

    @Test
    fun `the config dialog no longer leaks the build toolchain`() {
        val src = readMainSource("ui/features/settings/SupabaseConfigDialog.kt")
        assertFalse(
            "SEC-004: user-facing text must not mention the internal toolchain",
            src.contains("Google AI Studio"),
        )
        assertTrue(
            "guidance should point at the .env file instead",
            src.contains(".env"),
        )
    }

    // ── SEC-005 residual scope: NetworkTimeouts placeholder detection ────
    // (was hyphen-only; the .env.example values use YOUR_PROJECT + "-here"
    // suffixes that slipped through equals()-based checks.)

    @Test
    fun `env-example placeholder pair is detected as unconfigured`() {
        // Exact values from .env.example (CROSS-003/T-092 documented them):
        // https://YOUR_PROJECT.supabase.co + your-anon-key-here.
        assertTrue(
            "underscore YOUR_PROJECT must be detected (old check was hyphen-only)",
            NetworkTimeouts.looksLikePlaceholderConfig(
                "https://YOUR_PROJECT.supabase.co", "your-anon-key-here",
            ),
        )
        assertTrue(
            "hyphen your-project + your_anon_key_here must be detected",
            NetworkTimeouts.looksLikePlaceholderConfig(
                "https://your-project.supabase.co", "your_anon_key_here",
            ),
        )
    }

    @Test
    fun `real credentials are detected as configured`() {
        assertFalse(
            "a real project URL + JWT anon key must pass",
            NetworkTimeouts.looksLikePlaceholderConfig(
                "https://hkvkefubghbbotgnteir.supabase.co",
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSJ9.sig",
            ),
        )
    }

    @Test
    fun `blank, non-https, demo and inert pairs are all unconfigured`() {
        assertTrue(NetworkTimeouts.looksLikePlaceholderConfig("", "eyJabc"))
        assertTrue(NetworkTimeouts.looksLikePlaceholderConfig("https://real.supabase.co", ""))
        assertTrue(NetworkTimeouts.looksLikePlaceholderConfig("http://real.supabase.co", "eyJabc"))
        assertTrue(NetworkTimeouts.looksLikePlaceholderConfig("https://demo.supabase.co", "eyJabc"))
        // The inert fallback host/key from SupabaseClientProvider (T-064):
        assertTrue(NetworkTimeouts.looksLikePlaceholderConfig(
            "https://supabase.unconfigured.invalid", "inert-unconfigured-key",
        ))
        assertTrue(NetworkTimeouts.looksLikePlaceholderConfig(
            "https://real.supabase.co", "demo-key",
        ))
    }

    @Test
    fun `quoted env values are unwrapped before detection`() {
        // The gradle secrets plugin can leave surrounding quotes in the value.
        assertTrue(NetworkTimeouts.looksLikePlaceholderConfig(
            "\"https://YOUR_PROJECT.supabase.co\"", "\"your-anon-key-here\"",
        ))
        assertFalse(NetworkTimeouts.looksLikePlaceholderConfig(
            "\"https://real.supabase.co\"", "\"eyJhbGciOiJI.real-key\"",
        ))
    }

    private fun readMainSource(relativeUnderSrcMainJava: String): String {
        val relative = "src/main/java/com/example/$relativeUnderSrcMainJava"
        val cwd = File(System.getProperty("user.dir") ?: ".")
        val inModule = File(cwd, relative)
        if (inModule.isFile) return inModule.readText()
        val inRepoRoot = File(cwd.parentFile ?: cwd, relative)
        if (inRepoRoot.isFile) return inRepoRoot.readText()
        error("Source file not found from either ${cwd.absolutePath} or ${cwd.parentFile}: $relative")
    }
}
