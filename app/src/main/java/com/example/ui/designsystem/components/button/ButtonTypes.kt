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

/** Resolves an [ElButtonSize] to interior padding. F-19(a): the token read
 *  requires a composable context (the size→token resolver pattern). */
@androidx.compose.runtime.Composable
internal fun buttonPadding(size: ElButtonSize): PaddingValues = when (size) {
    ElButtonSize.SMALL  -> PaddingValues(horizontal = ElTheme.spacing.md, vertical = ElTheme.spacing.sm)
    ElButtonSize.MEDIUM -> PaddingValues(horizontal = 18.dp, vertical = ElTheme.spacing.md)
    ElButtonSize.LARGE  -> PaddingValues(horizontal = ElTheme.spacing.xl, vertical = ElTheme.spacing.lg)
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
@androidx.compose.runtime.Composable
internal fun buttonMinHeight(size: ElButtonSize) = when (size) {
    ElButtonSize.SMALL  -> ElTheme.spacing.xxl
    ElButtonSize.MEDIUM -> 44.dp
    ElButtonSize.LARGE  -> 56.dp
}
