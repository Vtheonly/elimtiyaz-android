package com.example.ui.designsystem.components.display

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import com.example.ui.designsystem.theme.ElTheme

/** Size bucket of an [ElAvatar]. */
enum class ElAvatarSize { XS, S, M, L, XL }

/** Resolves an [ElAvatarSize] to its diameter in dp. F-19(a): the token read
 *  requires a composable context (the size→token resolver pattern). */
@androidx.compose.runtime.Composable
internal fun avatarDp(size: ElAvatarSize) = when (size) {
    ElAvatarSize.XS -> ElTheme.spacing.xl
    ElAvatarSize.S  -> ElTheme.spacing.xxl
    ElAvatarSize.M  -> 40.dp
    ElAvatarSize.L  -> 56.dp
    ElAvatarSize.XL -> 72.dp
}

/** Resolves an [ElAvatarSize] to its initials font size in sp. */
internal fun avatarTextSize(size: ElAvatarSize): TextUnit = when (size) {
    ElAvatarSize.XS -> TextUnit(10f, TextUnitType.Sp)
    ElAvatarSize.S  -> TextUnit(12f, TextUnitType.Sp)
    ElAvatarSize.M  -> TextUnit(14f, TextUnitType.Sp)
    ElAvatarSize.L  -> TextUnit(18f, TextUnitType.Sp)
    ElAvatarSize.XL -> TextUnit(24f, TextUnitType.Sp)
}
