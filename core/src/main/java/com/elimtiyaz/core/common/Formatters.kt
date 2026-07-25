package com.elimtiyaz.core.common

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.math.ln
import kotlin.math.pow

/**
 * Formatting helpers shared by all feature modules. Centralised so currency,
 * dates and ID formats stay consistent with the master plan §20.02 French
 * terminology.
 */
object Formatters {

    /** Algerian Dinar — the platform's default currency per master plan. */
    fun currency(amount: Double, currencyCode: String = "DZD"): String {
        // e.g. "12 500 DZD" — fr-FR grouping with non-breaking space
        val grouped = String.format(java.util.Locale.FRANCE, "%,.0f", amount)
        return "$grouped $currencyCode"
    }

    fun compactCurrency(amount: Double): String = when {
        amount >= 1_000_000 -> "${String.format(java.util.Locale.US, "%.1fM", amount / 1_000_000)}"
        amount >= 1_000     -> "${String.format(java.util.Locale.US, "%.1fK", amount / 1_000)}"
        else                -> amount.toInt().toString()
    }

    fun date(iso: String): String {
        val instant = Instant.parse(iso)
        val tz = TimeZone.currentSystemDefault()
        val dt = instant.toLocalDateTime(tz).date
        return "${dt.dayOfMonth.toString().padStart(2, '0')}/${dt.monthNumber.toString().padStart(2, '0')}/${dt.year}"
    }

    fun dateTime(iso: String): String {
        val instant = Instant.parse(iso)
        val tz = TimeZone.currentSystemDefault()
        val dt = instant.toLocalDateTime(tz)
        return "${dt.dayOfMonth.toString().padStart(2, '0')}/${dt.monthNumber.toString().padStart(2, '0')}/${dt.year} " +
                "${dt.hour.toString().padStart(2, '0')}:${dt.minute.toString().padStart(2, '0')}"
    }

    /** Receipt number e.g. REC-2025-000123. */
    fun receiptNumber(year: Int, seq: Int): String =
        "REC-$year-${seq.toString().padStart(6, '0')}"

    /** Initialise-style parent code e.g. PAR-2018-A4F9. */
    fun parentCode(year: Int, suffix: String): String = "PAR-$year-$suffix"

    /** Student code e.g. ELV-2025-001234. */
    fun studentCode(year: Int, seq: Int): String =
        "ELV-$year-${seq.toString().padStart(6, '0')}"

    fun initials(first: String, last: String): String =
        "${first.firstOrNull() ?: ""}${last.firstOrNull() ?: ""}".uppercase().ifEmpty { "?" }

    fun fullName(first: String?, last: String?, fallback: String = "—"): String =
        listOfNotNull(first, last).joinToString(" ").ifBlank { fallback }

    /** Compact file size, used by attachment viewers. */
    fun fileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val unit = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
        val pre = "KMGTPE"[unit - 1]
        return String.format(java.util.Locale.US, "%.1f %sB", bytes / 1024.0.pow(unit.toDouble()), pre)
    }

    fun nowIso(): String = Clock.System.now().toString()
    fun today(): LocalDate = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    fun localDateFromIso(iso: String): LocalDate = Instant.parse(iso).toLocalDateTime(TimeZone.currentSystemDefault()).date
    fun isoFromLocal(date: LocalDate): String = date.atStartOfDayIn(TimeZone.UTC).toString()

    fun LocalDateTime.toIso(): String = toInstant(TimeZone.currentSystemDefault()).toString()
}
