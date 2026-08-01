package com.example.ui.designsystem.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * Standard structural border using theme tokens.
 * Call from a composable context.
 */
@Composable
fun Modifier.elBorder(
    width: Dp? = null,
    color: Color? = null,
    shape: Shape,
): Modifier {
    val w = width ?: ElTheme.borders.thin
    val c = color ?: ElTheme.colors.outline
    return this.border(w, c, shape)
}

/**
 * Gradient background modifier using a brush from the theme.
 * Clips to a flat rectangle; pass a clipped modifier upstream for shaped backgrounds.
 */
fun Modifier.elGradientBackground(brush: Brush): Modifier =
    this.clip(RoundedCornerShape(0.dp)).background(brush)
