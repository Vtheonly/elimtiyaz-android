package com.example.ui.designsystem.components.feedback

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * Indeterminate shimmer-style linear loader. Used for in-progress operations
 * with unknown duration.
 */
@Composable
fun ElLinearLoader(
    modifier: Modifier = Modifier,
    color: Color = ElTheme.colors.primary,
    height: Int = 4,
) {
    val transition = rememberInfiniteTransition(label = "el-loader")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Restart),
        label = "shift",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(50))
            .background(ElTheme.colors.surfaceVariant),
    ) {
        val gradient = Brush.linearGradient(
            colors = listOf(Color.Transparent, color, Color.Transparent),
            start = Offset(shift * -300f, 0f),
            end = Offset(shift * 300f + 300f, 0f),
        )
        Box(modifier = Modifier.fillMaxWidth().height(height.dp).background(gradient))
    }
}

/**
 * Centered loading block — spinner + optional message. For full-screen loading.
 */
@Composable
fun ElLoadingBlock(
    modifier: Modifier = Modifier,
    message: String? = "Loading…",
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        ElSpinner(size = 36)
        if (message != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = ElTheme.colors.textSecondary,
                style = ElTheme.typography.bodyMedium,
            )
        }
    }
}
