package com.example.ui.designsystem.components.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * Unified bottom navigation bar — bold geometric pill indicator on each item.
 */
@Composable
fun ElBottomBar(
    destinations: List<ElNavDestination>,
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    Box(
        modifier = modifier.fillMaxWidth().background(c.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            destinations.forEach { dest ->
                NavItem(
                    destination = dest,
                    selected = dest.route == currentRoute,
                    onNavigate = { onNavigate(dest.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
