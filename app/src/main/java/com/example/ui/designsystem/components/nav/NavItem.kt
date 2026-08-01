package com.example.ui.designsystem.components.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.noRippleClickable
import com.example.ui.designsystem.theme.ElTheme
import androidx.compose.ui.semantics.Role

/**
 * Renders a single destination item inside a bottom bar.
 * Pill background when selected, muted icon/label otherwise.
 */
@Composable
internal fun NavItem(
    destination: ElNavDestination,
    selected: Boolean,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .height(56.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(c.primary.copy(alpha = 0.10f)) else Modifier)
            .noRippleClickable(role = Role.Tab, onClick = onNavigate)
            .padding(vertical = 6.dp),
    ) {
        NavIcon(destination = destination, selected = selected)
        Spacer(Modifier.height(2.dp))
        Text(
            text = destination.label,
            color = if (selected) c.primary else c.textMuted,
            style = ElTheme.typography.labelSmall,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** The icon with optional badge overlay. */
@Composable
private fun NavIcon(destination: ElNavDestination, selected: Boolean) {
    val c = ElTheme.colors
    Box(contentAlignment = Alignment.TopEnd) {
        Icon(
            imageVector = if (selected) destination.selectedIcon else destination.icon,
            contentDescription = destination.label,
            tint = if (selected) c.primary else c.textSecondary,
            modifier = Modifier.size(22.dp),
        )
        if (destination.badge != null) {
            Box(
                modifier = Modifier
                    .offset(x = 6.dp, y = (-4).dp)
                    .clip(CircleShape)
                    .background(c.danger)
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            ) {
                Text(
                    text = destination.badge,
                    color = c.textOnColor,
                    style = ElTheme.textStyles.badge,
                )
            }
        }
    }
}
