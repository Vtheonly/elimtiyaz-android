package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.theme.elDesignTokens

// ── ElDivider ───────────────────────────────────────────────────────────────

/**
 * Custom divider with subtle gradient and configurable thickness.
 */
@Composable
fun ElDivider(
    modifier: Modifier = Modifier,
    thickness: Int = 1,
) {
    val tokens = elDesignTokens()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness.dp)
            .background(tokens.dividerColor),
    )
}
