package com.example.ui.util

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
 */
object PhoneUtils {
    /**
     * Sanitizes any phone number format (e.g. "+213 555 12 34 56", "0770 26 23 54")
     * and directly launches the system dialer ready to call with one tap.
     */
    fun dial(context: Context, rawPhone: String?, toast: ElToastHostState? = null) {
        if (rawPhone.isNullOrBlank()) {
            toast?.show("Numéro de téléphone non renseigné", ElToastTone.WARNING)
            return
        }
        val cleanNumber = rawPhone.filter { it.isDigit() || it == '+' }
        if (cleanNumber.isBlank()) {
            toast?.show("Numéro de téléphone invalide : $rawPhone", ElToastTone.WARNING)
            return
        }
        try {
            val uri = Uri.fromParts("tel", cleanNumber, null)
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            toast?.showError("Impossible de lancer l'appel : ${e.message}")
        }
    }

    /**
     * Formats phone number into international format and opens WhatsApp chat directly.
     *
     * T-320: an optional essage] prefills the chat's composition box via the
     * `wa.me/{num}?text=` deep link (URL-encoded). Backward compatible — all
     * existing call sites keep working with the default null.
     */
    fun openWhatsApp(context: Context, rawPhone: String?, message: String? = null, toast: ElToastHostState? = null) {
        if (rawPhone.isNullOrBlank()) {
            toast?.show("Numéro WhatsApp non renseigné", ElToastTone.WARNING)
            return
        }
        val clean = rawPhone.filter { it.isDigit() }
        val formatted = if (clean.startsWith("0")) "213${clean.substring(1)}" else clean
        try {
            val uri = if (message.isNullOrBlank()) {
                Uri.parse("https://wa.me/$formatted")
            } else {
                Uri.parse("https://wa.me/$formatted?text=${Uri.encode(message)}")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            toast?.showError("Impossible d'ouvrir WhatsApp : ${e.message}")
        }
    }
}
