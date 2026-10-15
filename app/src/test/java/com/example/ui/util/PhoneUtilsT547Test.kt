package com.example.ui.util

import android.content.Intent
import androidx.activity.ComponentActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * T-547 / CALL-523 (the 181st session) — the calling-path contracts, pinned.
 *
 * The owner's mandate: "Check call buttons, phone-number handling, Android
 * permissions, and the intent used to initiate calls. Ensure tapping a call
 * action opens the appropriate phone or dialer interface with the correct
 * number … Preserve the existing interface and intended calling behavior."
 *
 *  a. THE NUMBER HANDLING — [PhoneUtils.sanitizeDialNumber]: digits + a
 *     LEADING `+` survive; every other character (spaces, dashes, parens,
 *     mid-number plus signs) is dropped; blank/separator-only input yields
 *     null (the "Numéro de téléphone invalide" guard's input).
 *  b. THE WHATSAPP LINK — [PhoneUtils.whatsappLink]: the local `0` prefix
 *     internationalises to 213 (Algeria), an already-international number
 *     passes through, and the optional message is URL-encoded onto the
 *     wa.me deep link.
 *  c. THE DIAL INTENT — ACTION_DIAL + `tel:` + FLAG_ACTIVITY_NEW_TASK, the
 *     preserved pre-T-547 contract (no CALL_PHONE permission path, no
 *     ACTION_CALL behavior change): tapping call opens the system DIALER
 *     with the CORRECT sanitized number pre-filled.
 *  d. THE HONEST GUARDS — a blank/invalid number starts NO activity (the
 *     no-op-with-warning contract, now with the toast at every site).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneUtilsT547Test {

    // ─── a. the number handling ─────────────────────────────────────────

    @Test
    fun `sanitize keeps digits and a leading international plus`() {
        assertEquals("+213555123456", PhoneUtils.sanitizeDialNumber("+213 555 12 34 56"))
        assertEquals("0770262354", PhoneUtils.sanitizeDialNumber("0770 26 23 54"))
        assertEquals("0770262354", PhoneUtils.sanitizeDialNumber("0770-26-23-54"))
        assertEquals("0555123456", PhoneUtils.sanitizeDialNumber("(0) 555 12 34 56"))
    }

    @Test
    fun `sanitize drops mid-number plus signs but keeps a leading one`() {
        // A stray + that is NOT the international prefix is not dialable.
        assertEquals("055512", PhoneUtils.sanitizeDialNumber("055+512"))
        assertEquals("+15551234", PhoneUtils.sanitizeDialNumber("+1 (555) 123-4"))
    }

    @Test
    fun `sanitize returns null for absent or undialable input`() {
        assertNull(PhoneUtils.sanitizeDialNumber(null))
        assertNull(PhoneUtils.sanitizeDialNumber(""))
        assertNull(PhoneUtils.sanitizeDialNumber("   "))
        assertNull(PhoneUtils.sanitizeDialNumber("—"))
        assertNull(PhoneUtils.sanitizeDialNumber("++"))
    }

    // ─── b. the WhatsApp link ───────────────────────────────────────────

    @Test
    fun `whatsapp link internationalizes the local zero prefix to 213`() {
        assertEquals("https://wa.me/213770262354", PhoneUtils.whatsappLink("0770 26 23 54"))
        // An already-international number passes through.
        assertEquals("https://wa.me/213555123456", PhoneUtils.whatsappLink("+213 555 12 34 56"))
        assertEquals("https://wa.me/33612345678", PhoneUtils.whatsappLink("+33 6 12 34 56 78"))
    }

    @Test
    fun `whatsapp link url-encodes the prefilled message`() {
        assertEquals(
            "https://wa.me/213770262354?text=Bonjour%20M.%20BENALI",
            PhoneUtils.whatsappLink("0770262354", "Bonjour M. BENALI"),
        )
        assertNull(PhoneUtils.whatsappLink(null, "message"))
        assertNull(PhoneUtils.whatsappLink("   ", "message"))
    }

    // ─── c. the dial intent ─────────────────────────────────────────────

    @Test
    fun `dial launches ACTION_DIAL with the sanitized tel uri and NEW_TASK`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).get()
        PhoneUtils.dial(activity, "+213 555 12 34 56")
        val started = shadowOf(activity).nextStartedActivity
        assertEquals("the dialer action is ACTION_DIAL (no CALL_PHONE permission path)", Intent.ACTION_DIAL, started.action)
        assertEquals("the number is the SANITIZED tel: uri", "tel:+213555123456", started.data.toString())
        assertTrue(
            "the NEW_TASK flag is preserved (the pre-T-547 contract)",
            started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0,
        )
    }

    @Test
    fun `dial local format numbers reach the dialer unchanged in digits`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).get()
        PhoneUtils.dial(activity, "0770 26 23 54")
        val started = shadowOf(activity).nextStartedActivity
        assertEquals("tel:0770262354", started.data.toString())
    }

    // ─── d. the honest guards ───────────────────────────────────────────

    @Test
    fun `a blank or invalid number starts no activity`() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).get()
        PhoneUtils.dial(activity, null)
        PhoneUtils.dial(activity, "")
        PhoneUtils.dial(activity, "—")
        assertNull(
            "no dialer launch for absent/undialable numbers (the warning toast is the feedback)",
            shadowOf(activity).nextStartedActivity,
        )
    }
}
