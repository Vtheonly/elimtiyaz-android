package com.example.ui.designsystem.foundation

/**
 * El-Imtiyaz Design System — Money / Number Formatting Helpers.
 *
 * The ledger deals with money as `Long` centimes (smallest unit). Display
 * uses the French/Algerian convention: thin-space thousands separators
 * and a comma decimal separator, with the currency code trailing.
 *
 * Examples:
 *   elMoneyFormat(123456L, "DZD")        → "1 234,56 DZD"
 *   elMoneyFormat(123456L, "DZD", false) → "1 234,56"
 *   elMoneyParse("1 234,56")             → 123456L
 *   elMoneyParse("abc")                  → 0L
 *   elThousandsFormat(1234567L)          → "1 234 567"
 *   elPercentFormat(0.875)               → "87,5 %"
 *
 * These helpers are NOT locale-aware by design — the app explicitly uses
 * French/Algerian display conventions regardless of the device locale so
 * that financial figures are consistent for staff accountants.
 */

/** Non-breaking thin space used as the thousands separator. */
private const val THIN_SPACE = '\u202F'

/**
 * Format a Long-centimes amount as "1 234,56 DZD" (French/Algerian convention).
 *
 * @param cents         Amount in centimes (1 DZD = 100 centimes).
 * @param currency      ISO 4217 currency code to append when [showCurrency] is true.
 * @param showCurrency  When true, appends the [currency] code after the amount.
 * @return The formatted amount, e.g. "1 234,56 DZD" or "-1 234,56".
 */
fun elMoneyFormat(
    cents: Long,
    currency: String = "DZD",
    showCurrency: Boolean = true,
): String {
    val negative = cents < 0
    val absCents = if (negative) -cents else cents
    val whole = absCents / 100
    val fraction = absCents % 100

    val wholePart = elThousandsFormat(whole)
    val fractionPart = fraction.toString().padStart(2, '0')

    val amount = "$wholePart,$fractionPart"
    val signed = if (negative) "-$amount" else amount
    return if (showCurrency) "$signed $currency" else signed
}

/**
 * Parse a user-typed amount string back to Long centimes.
 *
 * Accepts:
 *  - French/Algerian format: "1 234,56" or "1234,56"
 *  - English format: "1,234.56" or "1234.56"
 *  - Bare integers: "1234" → 123400 cents
 *
 * Strips thousands separators (spaces, commas, dots used as separators),
 * recognizes the last `,` or `.` as the decimal separator, and parses the
 * fractional part as centimes (truncated to 2 digits).
 *
 * @return The parsed centimes, or 0 on parse failure.
 */
fun elMoneyParse(text: String): Long {
    if (text.isBlank()) return 0L

    // Track the sign and trim currency symbols / spaces.
    val trimmed = text.trim()
    val negative = trimmed.startsWith("-")
    val cleaned = trimmed
        .removePrefix("-")
        .trim()
        .removeSuffix("DZD")
        .removeSuffix("DA")
        .trim()

    if (cleaned.isEmpty()) return 0L

    // Identify the decimal separator — last ',' or '.' in the string.
    val lastComma = cleaned.lastIndexOf(',')
    val lastDot = cleaned.lastIndexOf('.')
    val decimalIndex = maxOf(lastComma, lastDot)

    val (wholePartRaw, fractionPartRaw) = if (decimalIndex >= 0) {
        cleaned.substring(0, decimalIndex) to cleaned.substring(decimalIndex + 1)
    } else {
        cleaned to ""
    }

    // Strip thousand separators from the whole part (space, comma, dot).
    val wholeDigits = wholePartRaw.filter { it.isDigit() }
    if (wholeDigits.isEmpty() && fractionPartRaw.isEmpty()) return 0L

    val wholeLong = wholeDigits.ifBlank { "0" }.toLongOrNull() ?: 0L

    // Fractional part — first two digits become centimes, anything beyond is
    // truncated (we don't round to avoid surprises when editing).
    val fractionDigits = fractionPartRaw.filter { it.isDigit() }
    val fractionCents = when {
        fractionDigits.isEmpty() -> 0L
        fractionDigits.length == 1 -> fractionDigits.toLong() * 10L
        else -> fractionDigits.take(2).toLong()
    }

    val total = wholeLong * 100L + fractionCents
    return if (negative) -total else total
}

/**
 * Format a Long with thousands separators (thin space).
 *
 *   elThousandsFormat(0L)        → "0"
 *   elThousandsFormat(1234567L)  → "1 234 567"
 *   elThousandsFormat(-1234567L) → "-1 234 567"
 */
fun elThousandsFormat(value: Long): String {
    val negative = value < 0
    val abs = if (negative) -value else value
    val digits = abs.toString()
    if (digits.length <= 3) return if (negative) "-$digits" else digits

    val sb = StringBuilder()
    val firstGroupSize = digits.length % 3
    if (firstGroupSize > 0) {
        sb.append(digits, 0, firstGroupSize)
        if (digits.length > firstGroupSize) sb.append(THIN_SPACE)
    }
    var i = firstGroupSize
    while (i < digits.length) {
        sb.append(digits, i, i + 3)
        i += 3
        if (i < digits.length) sb.append(THIN_SPACE)
    }
    return if (negative) "-$sb" else sb.toString()
}

/**
 * Format a percentage with N decimal places.
 *
 *   elPercentFormat(0.875)        → "87,5 %"
 *   elPercentFormat(0.875, 2)     → "87,50 %"
 *   elPercentFormat(1.0)          → "100,0 %"
 *
 * @param ratio    A 0..1 ratio (e.g. 0.875 for 87.5 %). Values > 1 are
 *                 formatted as-is (e.g. 1.5 → "150,0 %").
 * @param decimals Number of decimal places (default 1).
 */
fun elPercentFormat(ratio: Double, decimals: Int = 1): String {
    val percentage = ratio * 100.0
    val formatted = "%.${decimals.coerceAtLeast(0)}f".format(percentage)
    // Use comma as the decimal separator (French/Algerian convention).
    return "${formatted.replace('.', ',')} %"
}
