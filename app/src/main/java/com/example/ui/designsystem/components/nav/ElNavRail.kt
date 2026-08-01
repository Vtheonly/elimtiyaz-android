package com.example.ui.designsystem.components.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.noRippleClickable
import com.example.ui.designsystem.theme.ElTheme

/**
 * Side navigation rail — for tablets / landscape. Same destinations as
 * [ElBottomBar], laid out vertically.
 */
@Composable
fun ElNavRail(
    destinations: List<ElNavDestination>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    Column(
        modifier = modifier
            .width(80.dp)
            .fillMaxHeight()
            .background(c.surface)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        destinations.forEach { dest ->
            RailItem(
                destination = dest,
                selected = dest.route == currentRoute,
                onNavigate = { onNavigate(dest.route) },
            )
        }
    }
}

@Composable
private fun RailItem(
    destination: ElNavDestination,
    selected: Boolean,
    onNavigate: () -> Unit,
) {
    val c = ElTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .size(width = 64.dp, height = 56.dp)
            .clip(CircleShape)
            .then(if (selected) Modifier.background(c.primary.copy(alpha = 0.10f)) else Modifier)
            .noRippleClickable(role = Role.Tab, onClick = onNavigate)
            .padding(vertical = 6.dp),
    ) {
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
                        .background(c.danger),
                )
            }
        }
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
