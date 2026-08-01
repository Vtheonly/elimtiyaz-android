package com.example.ui.designsystem.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.designsystem.theme.ElAvatarShape
import com.example.ui.designsystem.theme.ElPillShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Unified avatar — image, initials, or icon fallback. Optional status dot.
 */
@Composable
fun ElAvatar(
    modifier: Modifier = Modifier,
    imageUrl: String? = null,
    initials: String? = null,
    icon: ImageVector? = null,
    size: ElAvatarSize = ElAvatarSize.M,
    accentColor: Color = ElTheme.colors.primary,
    statusDot: Color? = null,
) {
    val c = ElTheme.colors
    val dp = avatarDp(size)
    Box(modifier = modifier.size(dp + 4.dp), contentAlignment = Alignment.Center) {
        AvatarSurface(
            imageUrl = imageUrl,
            initials = initials,
            icon = icon,
            size = size,
            accentColor = accentColor,
            diameter = dp,
        )
        if (statusDot != null) {
            StatusDot(
                color = statusDot,
                diameter = dp,
                surfaceColor = c.surface,
            )
        }
    }
}

/** The circular avatar surface with image / initials / icon content. */
@Composable
private fun AvatarSurface(
    imageUrl: String?,
    initials: String?,
    icon: ImageVector?,
    size: ElAvatarSize,
    accentColor: Color,
    diameter: androidx.compose.ui.unit.Dp,
) {
    val c = ElTheme.colors
    Box(
        modifier = Modifier
            .size(diameter)
            .clip(ElAvatarShape)
            .background(Brush.linearGradient(listOf(accentColor.copy(alpha = 0.85f), accentColor)))
            .border(2.dp, c.surface, ElAvatarShape),
        contentAlignment = Alignment.Center,
    ) {
        when {
            imageUrl != null -> AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(diameter).clip(ElAvatarShape),
            )
            initials != null -> Text(
                text = initials.take(2).uppercase(),
                color = c.textOnColor,
                style = ElTheme.typography.labelLarge.copy(fontSize = avatarTextSize(size)),
            )
            icon != null -> Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.textOnColor,
                modifier = Modifier.size(diameter / 2),
            )
        }
    }
}

/** The optional status dot anchored to the bottom-end of the avatar. */
@Composable
private fun BoxScope.StatusDot(color: Color, diameter: androidx.compose.ui.unit.Dp, surfaceColor: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(diameter / 3.5f)
            .clip(ElPillShape)
            .background(color)
            .border(2.dp, surfaceColor, ElPillShape),
    )
}
