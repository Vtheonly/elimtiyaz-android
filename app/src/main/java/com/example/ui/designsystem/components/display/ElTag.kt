package com.example.ui.designsystem.components.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.theme.ElPillShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Compact status tag — pill-shaped with a subtle background tint driven by
 * [ElTagTone]. Replaces the legacy `ui.components.ElTag` for screens
 * adopting the modern design system.
 *
 * @param text     Tag text.
 * @param modifier Outer modifier.
 * @param tone     Background / foreground tone. Defaults to [ElTagTone.NEUTRAL].
 * @param size     SM (10sp) for inline badges, MD (12sp) for standalone chips.
 * @param icon     Optional leading icon.
 */
@Composable
fun ElTag(
    text: String,
    modifier: Modifier = Modifier,
    tone: ElTagTone = ElTagTone.NEUTRAL,
    size: ElTagSize = ElTagSize.SM,
    icon: (@Composable () -> Unit)? = null,
) {
    val c = ElTheme.colors
    val (bg, fg) = tone.resolveColors(c)

    val horizontalPadding = if (size == ElTagSize.SM) 8.dp else 12.dp
    val verticalPadding = if (size == ElTagSize.SM) 3.dp else 6.dp
    val fontSize = if (size == ElTagSize.SM) 10.sp else 12.sp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(ElPillShape)
            .background(bg)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = fg,
            style = ElTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Convenience overload accepting an [ImageVector] for the leading icon —
 * keeps call sites short when callers just want a default-tinted icon.
 */
@Composable
fun ElTag(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: ElTagTone = ElTagTone.NEUTRAL,
    size: ElTagSize = ElTagSize.SM,
) {
    val (_, fg) = tone.resolveColors(ElTheme.colors)
    ElTag(
        text = text,
        modifier = modifier,
        tone = tone,
        size = size,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(if (size == ElTagSize.SM) 12.dp else 14.dp),
            )
        },
    )
}

/** Resolves a [tone] to its (background, foreground) pair via the theme colors. */
private fun ElTagTone.resolveColors(
    c: com.example.ui.designsystem.theme.ElColors,
): Pair<Color, Color> = when (this) {
    ElTagTone.NEUTRAL  -> c.surfaceVariant.copy(alpha = 0.7f) to c.textSecondary
    ElTagTone.INFO     -> c.infoContainer        to c.info
    ElTagTone.SUCCESS  -> c.successContainer     to c.success
    ElTagTone.WARNING  -> c.warningContainer     to c.warning
    ElTagTone.DANGER   -> c.dangerContainer      to c.danger
}
