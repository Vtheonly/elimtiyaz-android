package com.example.core

/**
 * PII masking — mirrors desktop `src/domain/pii-mask.ts`. Reversible.
 * Masking order is critical: IBAN first (longest digit run), then phone,
 * then email, then NN (10 digits — would otherwise grab parts of IBAN),
 * then parent names, then student names. Same value reuses same placeholder.
 */
object PiiMask {
    const val PHONE_PREFIX = "PHONE"
    const val EMAIL_PREFIX = "EMAIL"
    const val IBAN_PREFIX = "IBAN"
    const val NN_PREFIX = "NN"
    const val PARENT_PREFIX = "PARENT"
    const val STUDENT_PREFIX = "STUDENT"

    data class Result(val masked: String, val replacements: Map<String, String>)
    data class Options(val parentNames: List<String> = emptyList(), val studentNames: List<String> = emptyList())

    fun maskPII(text: String, options: Options = Options()): Result {
        val replacements = mutableMapOf<String, String>()
        val counters = mutableMapOf(PHONE_PREFIX to 0, EMAIL_PREFIX to 0, IBAN_PREFIX to 0, NN_PREFIX to 0, PARENT_PREFIX to 0, STUDENT_PREFIX to 0)
        val seen = mutableMapOf<String, String>()

        fun nextPlaceholder(prefix: String, original: String): String {
            seen[original]?.let { return it }
            val n = (counters[prefix] ?: 0) + 1
            counters[prefix] = n
            val placeholder = "[${prefix}_$n]"
            replacements[placeholder] = original
            seen[original] = placeholder
            return placeholder
        }

        var masked = text
        masked = Regex("""DZ\d{2}(?:\s?\d{4}){5}""").replace(masked) { nextPlaceholder(IBAN_PREFIX, it.value) }
        masked = Regex("""(?:(?:\+|00)?213|0)[\s\-.]?(?:[5-7][\s\-.]?\d{2}[\s\-.]?\d{3}[\s\-.]?\d{2,3}|[5-7]\d{8})""").replace(masked) { nextPlaceholder(PHONE_PREFIX, it.value) }
        masked = Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}""").replace(masked) { nextPlaceholder(EMAIL_PREFIX, it.value) }
        masked = Regex("""(?<!\d)\d{10}(?!\d)""").replace(masked) { nextPlaceholder(NN_PREFIX, it.value) }
        for (name in options.parentNames.filter { it.isNotBlank() }.sortedByDescending { it.length }) {
            if (masked.contains(name)) masked = masked.replace(name, nextPlaceholder(PARENT_PREFIX, name))
        }
        for (name in options.studentNames.filter { it.isNotBlank() }.sortedByDescending { it.length }) {
            if (masked.contains(name)) masked = masked.replace(name, nextPlaceholder(STUDENT_PREFIX, name))
        }
        return Result(masked = masked, replacements = replacements.toMap())
    }

    fun unmaskPII(masked: String, replacements: Map<String, String>): String {
        var result = masked
        for ((placeholder, original) in replacements) result = result.replace(placeholder, original)
        return result
    }
}
