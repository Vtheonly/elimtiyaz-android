package com.example.ui.designsystem.overlays

import androidx.compose.ui.graphics.Color

/** Tone of an [ElToast] — drives the accent bar color. */
enum class ElToastTone { NEUTRAL, SUCCESS, WARNING, DANGER, INFO }

/** Resolves a [tone] to its accent color using the active theme colors. */
internal fun com.example.ui.designsystem.theme.ElColors.toastAccent(tone: ElToastTone): Color = when (tone) {
    ElToastTone.NEUTRAL -> primary
    ElToastTone.SUCCESS -> success
    ElToastTone.WARNING -> warning
    ElToastTone.DANGER -> danger
    ElToastTone.INFO -> info
}
