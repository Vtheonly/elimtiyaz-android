package com.example.ui.designsystem.components.feedback

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElCardShape
import com.example.ui.designsystem.theme.ElPillShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * A single shimmering placeholder block. Used inside skeleton composables.
 */
@Composable
fun ElSkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(8.dp),
) {
    val transition = rememberInfiniteTransition(label = "el-skel")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(
        modifier = modifier
            .clip(shape)
            .background(ElTheme.colors.surfaceVariant)
            .alpha(alpha),
    )
}

/** Circle skeleton — for avatars. */
@Composable
fun ElSkeletonCircle(sizeDp: Int = 40, modifier: Modifier = Modifier) {
    ElSkeletonBox(modifier = modifier.size(sizeDp.dp), shape = CircleShape)
}

/** Text-line skeleton — width-fraction of parent. */
@Composable
fun ElSkeletonLine(
    modifier: Modifier = Modifier,
    widthFraction: Float = 1f,
    height: Int = 12,
) {
    ElSkeletonBox(
        modifier = modifier
            .fillMaxWidth(widthFraction)
            .height(height.dp),
        shape = ElPillShape,
    )
}

/** Skeleton of a card with avatar + 2 text lines. */
@Composable
fun ElSkeletonCard(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ElCardShape)
            .background(ElTheme.colors.surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ElSkeletonCircle(40)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            ElSkeletonLine(widthFraction = 0.6f, height = 14)
            Spacer(Modifier.height(6.dp))
            ElSkeletonLine(widthFraction = 0.4f, height = 10)
        }
    }
}
