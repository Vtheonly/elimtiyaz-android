package com.example.ui.designsystem.components.button

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/** Visual variant of an [ElButton]. */
enum class ElButtonVariant {
    /** Filled gradient — primary CTA. */
    PRIMARY,
    /** Filled solid — secondary CTA, less prominent than primary. */
    SECONDARY,
    /** Filled tonal — uses primaryContainer. */
    TONAL,
    /** Outlined — border only. */
    OUTLINED,
    /** Ghost — text/icon only, no surface. */
    GHOST,
    /** Danger — filled red, destructive actions. */
    DANGER,
}

/** Size bucket of an [ElButton]. */
enum class ElButtonSize { SMALL, MEDIUM, LARGE }

/** Resolves an [ElButtonSize] to interior padding. */
internal fun buttonPadding(size: ElButtonSize): PaddingValues = when (size) {
    ElButtonSize.SMALL  -> PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ElButtonSize.MEDIUM -> PaddingValues(horizontal = 18.dp, vertical = 12.dp)
    ElButtonSize.LARGE  -> PaddingValues(horizontal = 24.dp, vertical = 16.dp)
}

/** Resolves an [ElButtonSize] to its label text style. */
@androidx.compose.runtime.Composable
internal fun buttonTextStyle(size: ElButtonSize): TextStyle = when (size) {
    ElButtonSize.SMALL  -> ElTheme.typography.labelMedium
    ElButtonSize.MEDIUM -> ElTheme.typography.labelLarge
    ElButtonSize.LARGE  -> ElTheme.typography.titleMedium
}

/** Resolves an [ElButtonSize] to its icon pixel size. */
internal fun buttonIconSize(size: ElButtonSize): Int = when (size) {
    ElButtonSize.SMALL  -> 14
    ElButtonSize.MEDIUM -> 18
    ElButtonSize.LARGE  -> 22
}

/** Resolves an [ElButtonSize] to its minimum touch-target height. */
internal fun buttonMinHeight(size: ElButtonSize) = when (size) {
    ElButtonSize.SMALL  -> 32.dp
    ElButtonSize.MEDIUM -> 44.dp
    ElButtonSize.LARGE  -> 56.dp
}
