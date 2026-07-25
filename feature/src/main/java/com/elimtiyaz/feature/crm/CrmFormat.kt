package com.elimtiyaz.feature.crm

import com.elimtiyaz.core.common.Formatters

/**
 * Internal formatting helpers used across the CRM screens.
 *
 * Centralised here so the screens can stick to `Formatters.currency(...)` /
 * `Formatters.date(...)` for ISO datetime strings while gracefully handling
 * plain yyyy-MM-dd dates (used by `Student.birthDate` and similar fields).
 */
internal object CrmFormat {

    /**
     * Format an ISO datetime string OR a plain yyyy-MM-dd date string into
     * "dd/MM/yyyy" using [Formatters.date] when possible.
     */
    fun date(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return try {
            Formatters.date(iso)
        } catch (_: Throwable) {
            // Fallback for plain yyyy-MM-dd values (no time component)
            runCatching {
                val date = kotlinx.datetime.LocalDate.parse(iso.substringBefore("T"))
                "${date.dayOfMonth.toString().padStart(2, '0')}/" +
                    "${date.monthNumber.toString().padStart(2, '0')}/${date.year}"
            }.getOrDefault(iso)
        }
    }

    /** Currency wrapper — delegates to [Formatters.currency]. */
    fun currency(amount: Double): String = Formatters.currency(amount)
}
