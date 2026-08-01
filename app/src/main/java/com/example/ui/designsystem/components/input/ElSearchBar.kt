package com.example.ui.designsystem.components.input

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.pressClickable
import com.example.ui.designsystem.theme.ElPillShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Pill-shaped search input with leading magnifier icon, animated focus
 * expansion, and a press-scale clear button.
 *
 * Replaces the legacy ad-hoc `OutlinedTextField` search fields scattered
 * across screens. Pulls tokens from [ElTheme] for the surface, border,
 * and motion so light/dark switching is automatic.
 *
 * @param query         Current query text.
 * @param onQueryChange Callback when the user edits the query.
 * @param modifier      Outer modifier.
 * @param placeholder   Placeholder shown when [query] is empty.
 * @param leadingIcon   Optional override; defaults to a search icon.
 * @param trailingIcon  Optional override; defaults to a clear button shown
 *                      only when the query is non-empty.
 */
@Composable
fun ElSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search…",
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val c = ElTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val isFocused by interaction.collectIsFocusedAsState()

    // Subtle width expansion on focus — feels like the search "wakes up".
    val focusScale by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0.985f,
        label = "el-search-focus",
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth(focusScale)
            .defaultMinSize(minHeight = 48.dp)
            .clip(ElPillShape)
            .background(c.surface)
            .border(
                width = ElTheme.borders.thin,
                color = if (isFocused) c.primary else c.outline,
                shape = ElPillShape,
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // Leading icon (default = search).
        if (leadingIcon != null) {
            leadingIcon()
        } else {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = if (isFocused) c.primary else c.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(10.dp))

        // Text input area.
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = c.textMuted,
                    style = ElTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = ElTheme.typography.bodyMedium.copy(color = c.textPrimary),
                cursorBrush = SolidColor(c.primary),
                interactionSource = interaction,
            )
        }

        // Trailing icon (default = clear button, only shown when query non-empty).
        if (trailingIcon != null) {
            trailingIcon()
        } else if (query.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            ClearButton(onClick = { onQueryChange("") })
        }
    }
}

/** Press-scale circular clear button. */
@Composable
private fun ClearButton(onClick: () -> Unit) {
    val c = ElTheme.colors
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(ElPillShape)
            .background(c.surfaceVariant)
            .pressClickable(
                pressedScale = 0.85f,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Clear search",
            tint = c.textSecondary,
            modifier = Modifier.size(14.dp),
        )
    }
}

/**
 * Convenience overload accepting an [ImageVector] for the leading icon —
 * keeps call sites simple when callers just want a different icon.
 */
@Composable
fun ElSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    placeholder: String = "Search…",
) {
    ElSearchBar(
        query = query,
        onQueryChange = onQueryChange,
        modifier = modifier,
        placeholder = placeholder,
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = ElTheme.colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}
