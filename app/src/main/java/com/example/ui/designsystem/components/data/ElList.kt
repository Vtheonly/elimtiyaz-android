package com.example.ui.designsystem.components.data

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElAvatarSize
import com.example.ui.designsystem.components.display.ElBadge
import com.example.ui.designsystem.components.display.ElBadgeStyle
import com.example.ui.designsystem.components.display.ElBadgeTone
import com.example.ui.designsystem.theme.ElTheme

/**
 * Unified list item — avatar + title + subtitle + trailing slot.
 * Used everywhere from rosters to settings to inbox rows.
 */
@Composable
fun ElListItem(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leadingIcon: ImageVector? = null,
    leadingAvatarUrl: String? = null,
    leadingInitials: String? = null,
    leadingTint: Color = ElTheme.colors.primary,
    trailingText: String? = null,
    trailingIcon: ImageVector? = null,
    trailingBadge: String? = null,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    showDivider: Boolean = true,
) {
    val c = ElTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .then(if (selected) Modifier.background(c.primary.copy(alpha = 0.08f)) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ListLeading(
                icon = leadingIcon,
                avatarUrl = leadingAvatarUrl,
                initials = leadingInitials,
                tint = leadingTint,
            )
            ListText(title = title, subtitle = subtitle, modifier = Modifier.weight(1f))
            ListTrailing(
                badge = trailingBadge,
                text = trailingText,
                icon = trailingIcon,
            )
        }
        if (showDivider) ListDivider()
    }
}

/** Leading slot: avatar (if URL or initials) else icon-in-circle. */
@Composable
private fun ListLeading(
    icon: ImageVector?,
    avatarUrl: String?,
    initials: String?,
    tint: Color,
) {
    when {
        avatarUrl != null || initials != null -> {
            ElAvatar(
                imageUrl = avatarUrl,
                initials = initials,
                size = ElAvatarSize.S,
                accentColor = tint,
            )
            Spacer(Modifier.width(12.dp))
        }
        icon != null -> {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
        }
    }
}

/** Title + subtitle column. */
@Composable
private fun ListText(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    val c = ElTheme.colors
    Column(modifier = modifier) {
        Text(
            text = title,
            color = c.textPrimary,
            style = ElTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = c.textSecondary,
                style = ElTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Trailing slot: badge / text / icon. */
@Composable
private fun ListTrailing(
    badge: String?,
    text: String?,
    icon: ImageVector?,
) {
    val c = ElTheme.colors
    if (badge != null) {
        Spacer(Modifier.width(8.dp))
        ElBadge(text = badge, tone = ElBadgeTone.PRIMARY, style = ElBadgeStyle.SOLID)
    }
    if (text != null) {
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = c.textMuted,
            style = ElTheme.typography.labelMedium,
        )
    }
    if (icon != null) {
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = c.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The thin divider at the bottom of a list item. */
@Composable
private fun ListDivider() {
    val c = ElTheme.colors
    Spacer(Modifier.height(12.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(c.outlineVariant),
    )
}
