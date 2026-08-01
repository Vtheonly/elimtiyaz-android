package com.example.ui.features.settings

internal fun languageLabel(code: String): String = when (code) {
    "ar" -> "العربية"
    "en" -> "English"
    else -> "Français"
}

/** Display label → ISO 639-1 code. */
