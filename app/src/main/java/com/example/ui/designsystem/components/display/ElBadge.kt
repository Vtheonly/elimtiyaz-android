package com.example.ui.designsystem.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElPillShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Unified badge — for status, counts, tags.
 * Three styles × eight tones. Pill shape, tiny bold text, optional dot or icon.
 */
@Composable
fun ElBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: ElBadgeTone = ElBadgeTone.PRIMARY,
    style: ElBadgeStyle = ElBadgeStyle.SOFT,
    icon: ImageVector? = null,
    dot: Boolean = false,
) {
    val c = ElTheme.colors
    val p = c.badgePalette(tone)

    val (bg, fg, borderColor) = resolveBadgeColors(style, p, c.textOnColor, c.isDark)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(ElPillShape)
            .background(bg)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, ElPillShape) else Modifier)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        if (dot) {
            Row(
                modifier = Modifier
                    .padding(end = 5.dp)
                    .size(6.dp)
                    .clip(ElPillShape)
                    .background(fg),
            ) {}
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.padding(end = 4.dp).size(12.dp),
            )
        }
        Text(
            text = text,
            color = fg,
            style = ElTheme.textStyles.badge,
        )
    }
}

/** Resolves (bg, fg, border) for a badge based on style + palette. */
private fun resolveBadgeColors(
    style: ElBadgeStyle,
    palette: BadgePalette,
    textOnColor: Color,
    isDark: Boolean,
): Triple<Color, Color, Color?> = when (style) {
    ElBadgeStyle.SOLID -> Triple(palette.border, textOnColor, null)
    ElBadgeStyle.SOFT -> Triple(
        palette.bg.copy(alpha = if (isDark) 0.20f else 0.14f),
        palette.fg,
        null,
    )
    ElBadgeStyle.OUTLINED -> Triple(Color.Transparent, palette.fg, palette.border)
}
