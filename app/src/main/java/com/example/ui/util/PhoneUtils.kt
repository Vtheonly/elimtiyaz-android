package com.example.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.ui.designsystem.overlays.ElToastHostState
import com.example.ui.designsystem.overlays.ElToastTone

/**
 * T-460 H2 (issue #3 F-18): the platform toasts → the DS toast layer.
 * The feedback channel is now an OPTIONAL [ElToastHostState] passed by the
 * (composable) callers — `LocalElToast.current` at the call site. The dial /
 * WhatsApp intents themselves are unchanged (they still take the activity
 * context); only the error feedback moved onto the design system.
 *
 * T-547 / CALL-523 (the 181st session — the owner's calling audit): the
 * intent contracts are now PINNED and the failure paths are HONEST —
 *  - `dial` stays ACTION_DIAL + `tel:` + NEW_TASK (the dialer contract: it
 *    needs NO runtime permission and it opens the system dialer with the
 *    number pre-filled — the owner's "opens the appropriate phone or dialer
 *    interface with the correct number"). Direct ACTION_CALL/CALL_PHONE is
 *    deliberately NOT used (a Play-Store-scrutinised permission + a behavior
 *    change the mandate forbids: "Preserve the existing interface and
 *    intended calling behavior").
 *  - A dedicated [ActivityNotFoundException] branch replaces the old generic
 *    catch for the real-world "no dialer app on this device" case (the
 *    Waydroid harness) with a plain-French message instead of the raw
 *    system text ("No Activity found to handle Intent…").
 *  - The number handling is extracted into pure, TESTABLE functions:
 *    [sanitizeDialNumber] (digits + a leading `+`, everything else dropped)
 *    and [whatsappLink] (the 0→213 internationalisation + the optional
 *    prefilled text) — both pinned by PhoneUtilsT547Test.
 */
object PhoneUtils {

    /** The honest message when the device has no dialer app at all. */
    private const val NO_DIALER_MESSAGE =
        "Aucune application d'appel disponible sur cet appareil."

    /**
     * Sanitizes any phone number format into a dialable form: only the
     * DIGITS and a leading `+` survive (e.g. "+213 555 12 34 56" →
     * "+213555123456", "0770 26 23 54" → "0770262354"). A `+` that is not
     * the international prefix is dropped. Returns null when nothing
     * dialable remains (blank / only separators / only plus signs) — a
     * number MUST contain at least one digit.
     */
    fun sanitizeDialNumber(rawPhone: String?): String? {
        if (rawPhone.isNullOrBlank()) return null
        val digits = rawPhone.filter { it.isDigit() }
        if (digits.isBlank()) return null
        val hasLeadingPlus = rawPhone.trim().startsWith("+")
        return if (hasLeadingPlus) "+$digits" else digits
    }

    /**
     * Builds the wa.me deep link: digits only, the local `0` prefix is
     * internationalised to Algeria (213), and an optional message
     * pre-fills the composition box (URL-encoded). Returns null when no
     * digits exist.
     */
    fun whatsappLink(rawPhone: String?, message: String? = null): String? {
        if (rawPhone.isNullOrBlank()) return null
        val clean = rawPhone.filter { it.isDigit() }
        if (clean.isBlank()) return null
        val formatted = if (clean.startsWith("0")) "213${clean.substring(1)}" else clean
        return if (message.isNullOrBlank()) {
            "https://wa.me/$formatted"
        } else {
            "https://wa.me/$formatted?text=${Uri.encode(message)}"
        }
    }

    /**
     * Sanitizes any phone number format (e.g. "+213 555 12 34 56", "0770 26 23 54")
     * and directly launches the system dialer ready to call with one tap.
     */
    fun dial(context: Context, rawPhone: String?, toast: ElToastHostState? = null) {
        // The two honest guard messages (preserved verbatim from the
        // pre-T-547 contract): absent vs present-but-invalid.
        if (rawPhone.isNullOrBlank()) {
            toast?.show("Numéro de téléphone non renseigné", ElToastTone.WARNING)
            return
        }
        val cleanNumber = sanitizeDialNumber(rawPhone)
        if (cleanNumber == null) {
            toast?.show("Numéro de téléphone invalide : $rawPhone", ElToastTone.WARNING)
            return
        }
        try {
            // T-547: the canonical documented form (developer.android.com's
            // Dial a phone pattern) — Uri.parse keeps the leading `+`
            // visible in the tel: URI (RFC 3966 global-number form), which
            // every dialer parses; the previous Uri.fromParts form encoded
            // it as %2B (equivalent for compliant dialers, but the parse
            // form is the ecosystem-standard one web tel: links produce).
            val uri = Uri.parse("tel:$cleanNumber")
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // T-547: the honest "no dialer on this device" case (the raw
            // system message was cryptic — "No Activity found to handle
            // Intent { act=android.intent.action.DIAL }").
            toast?.showError(NO_DIALER_MESSAGE)
        } catch (e: Exception) {
            toast?.showError("Impossible de lancer l'appel : ${e.message}")
        }
    }

    /**
     * Formats phone number into international format and opens WhatsApp chat directly.
     *
     * T-320: an optional [message] prefills the chat's composition box via the
     * `wa.me/{num}?text=` deep link (URL-encoded). Backward compatible — all
     * existing call sites keep working with the default null.
     */
    fun openWhatsApp(context: Context, rawPhone: String?, message: String? = null, toast: ElToastHostState? = null) {
        if (rawPhone.isNullOrBlank()) {
            toast?.show("Numéro WhatsApp non renseigné", ElToastTone.WARNING)
            return
        }
        val link = whatsappLink(rawPhone, message)
        if (link == null) {
            toast?.show("Numéro WhatsApp invalide : $rawPhone", ElToastTone.WARNING)
            return
        }
        try {
            val uri = Uri.parse(link)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // T-547: no browser AND no WhatsApp on the device.
            toast?.showError("Aucune application disponible pour ouvrir WhatsApp.")
        } catch (e: Exception) {
            toast?.showError("Impossible d'ouvrir WhatsApp : ${e.message}")
        }
    }
}
