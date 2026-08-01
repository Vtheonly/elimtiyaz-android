package com.example.ui.designsystem.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * Frosted-glass surface — translucent tint + thin border.
 * Used for premium overlays and floating bars.
 */
@Composable
fun Modifier.elGlass(shape: Shape): Modifier {
    val colors = ElTheme.colors
    return this
        .clip(shape)
        .background(colors.glassTint)
        .border(1.dp, colors.glassBorder, shape)
}
