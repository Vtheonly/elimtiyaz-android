package com.example.ui.features.settings

import androidx.compose.runtime.Composable

internal fun languageCodeFromLabel(label: String): String = when (label) {
    "العربية" -> "ar"
    "English" -> "en"
    else -> "fr"
}
