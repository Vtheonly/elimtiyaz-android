package com.example.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class SemanticColors(
    val success: Color,
    val onSuccess: Color,
    val warning: Color,
    val onWarning: Color,
    val info: Color,
    val onInfo: Color,
)

val LocalSemanticColors = staticCompositionLocalOf {
    SemanticColors(
        success = SuccessGreen, onSuccess = Color.White,
        warning = WarningOrange, onWarning = Color.White,
        info = InfoBlue, onInfo = Color.White,
    )
}
