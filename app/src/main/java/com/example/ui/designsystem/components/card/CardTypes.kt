package com.example.ui.designsystem.components.card

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElCardShape
import com.example.ui.designsystem.theme.ElCardShapeSmall
import androidx.compose.ui.graphics.Shape

/** Visual variant of an [ElCard]. */
enum class ElCardVariant {
    /** Flat surface, structural border. */
    OUTLINED,
    /** Elevated with tinted shadow. */
    ELEVATED,
    /** Filled surfaceVariant. */
    FILLED,
    /** Brand gradient background. */
    GRADIENT,
    /** Glassmorphism (translucent + border). */
    GLASS,
}

/** Size bucket of an [ElCard] — controls interior padding. */
enum class ElCardSize { COMPACT, STANDARD, COMFORTABLE }

/** Resolves an [ElCardSize] to interior padding. */
internal fun cardPadding(size: ElCardSize): PaddingValues = when (size) {
    ElCardSize.COMPACT     -> PaddingValues(12.dp)
    ElCardSize.STANDARD    -> PaddingValues(16.dp)
    ElCardSize.COMFORTABLE -> PaddingValues(24.dp)
}

/** Picks the default shape for a card based on its size. */
internal fun defaultCardShape(size: ElCardSize): Shape = when (size) {
    ElCardSize.COMPACT -> ElCardShapeSmall
    else -> ElCardShape
}
