package com.example.ui.designsystem.foundation

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElElevationSpec
import com.example.ui.designsystem.theme.ElTheme

/**
 * A tinted, layered shadow matching the bold geometric elevation spec.
 * Light theme shadows are tinted with the primary color for a premium feel;
 * dark theme shadows are deep black.
 *
 * Pass [color] explicitly to override the theme shadow color.
 */
fun Modifier.elShadow(
    spec: ElElevationSpec,
    shape: Shape = RoundedCornerShape(24.dp),
    color: Color? = null,
): Modifier = composed {
    if (spec.alpha <= 0f) return@composed this
    val resolvedColor = color ?: ElTheme.shadowColor
    this.drawBehind {
        val paint = android.graphics.Paint().apply {
            this.color = resolvedColor.toArgb()
            alpha = (spec.alpha * 255f).toInt().coerceIn(0, 255)
            maskFilter = android.graphics.BlurMaskFilter(
                spec.blur.toPx(),
                android.graphics.BlurMaskFilter.Blur.NORMAL,
            )
        }
        val cornerRadius = cornerRadiusOf(shape, this)
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRoundRect(
                0f,
                spec.y.toPx(),
                size.width,
                size.height + spec.y.toPx(),
                cornerRadius,
                cornerRadius,
                paint,
            )
        }
    }
}

/** Compose-native shadow fallback for cases where blur drawing is unavailable. */
fun Modifier.elShadowFallback(
    elevation: Dp,
    shape: Shape = RoundedCornerShape(24.dp),
): Modifier = this.shadow(elevation, shape, clip = false)

/** Extracts the corner radius from a [RoundedCornerShape], 0 for other shapes. */
private fun cornerRadiusOf(shape: Shape, scope: androidx.compose.ui.graphics.drawscope.DrawScope): Float =
    when (shape) {
        is RoundedCornerShape -> shape.topStart.toPx(scope.size, scope)
        else -> 0f
    }
