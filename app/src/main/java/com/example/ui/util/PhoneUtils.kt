package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object PhoneUtils {
    /**
     * Sanitizes any phone number format (e.g. "+213 555 12 34 56", "0770 26 23 54")
     * and directly launches the system dialer ready to call with one tap.
     */
    fun dial(context: Context, rawPhone: String?) {
        if (rawPhone.isNullOrBlank()) {
            Toast.makeText(context, "Numéro de téléphone non renseigné", Toast.LENGTH_SHORT).show()
            return
        }
        val cleanNumber = rawPhone.filter { it.isDigit() || it == '+' }
        if (cleanNumber.isBlank()) {
            Toast.makeText(context, "Numéro de téléphone invalide : $rawPhone", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val uri = Uri.fromParts("tel", cleanNumber, null)
            val intent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible de lancer l'appel : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Formats phone number into international format and opens WhatsApp chat directly.
     */
    fun openWhatsApp(context: Context, rawPhone: String?) {
        if (rawPhone.isNullOrBlank()) {
            Toast.makeText(context, "Numéro WhatsApp non renseigné", Toast.LENGTH_SHORT).show()
            return
        }
        val clean = rawPhone.filter { it.isDigit() }
        val formatted = if (clean.startsWith("0")) "213${clean.substring(1)}" else clean
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$formatted")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Impossible d'ouvrir WhatsApp : ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}