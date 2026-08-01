package com.example.ui.designsystem.components.input

import androidx.compose.ui.graphics.Color
import com.example.ui.designsystem.theme.ElBorders
import com.example.ui.designsystem.theme.ElColors

/** Background color for the field surface based on state. */
internal fun fieldBackground(state: ElTextFieldState, surfaceVariant: Color): Color = when (state) {
    ElTextFieldState.DISABLED -> surfaceVariant.copy(alpha = 0.5f)
    else -> surfaceVariant
}

/** Border color for the field based on state. */
internal fun fieldBorderColor(state: ElTextFieldState, colors: ElColors): Color = when (state) {
    ElTextFieldState.ERROR -> colors.danger
    ElTextFieldState.FOCUSED -> colors.primary
    else -> colors.outline
}

/** Border width for the field based on state. */
internal fun fieldBorderWidth(state: ElTextFieldState, borders: ElBorders) = when (state) {
    ElTextFieldState.FOCUSED -> borders.thick
    else -> borders.thin
}
