package com.elimtiyaz.core.rbac

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Specialised gated affordances — drop-in replacements for the Material 3
 * components that automatically render the disabled state when the user lacks
 * access. Each one evaluates the requirement against the current composition
 * locals and either renders the active component, the disabled variant, or nothing.
 *
 * Convention: when disabled, the component is rendered (NOT hidden) so the user
 * sees the affordance exists. Clicks are silently ignored. A lock icon is
 * appended (or replaces the leading icon) to communicate the locked state.
 */

/**
 * Bottom-nav item. When disabled, the item is rendered with reduced alpha and
 * a small lock overlay; clicks are ignored.
 */
@Composable
fun RowScope.GatedNavigationBarItem(
    requirement: AccessRequirement,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    val state = accessStateOf(requirement)
    val alpha = if (state is AccessState.Disabled) 0.4f else 1f
    val effectiveClick = if (state is AccessState.Enabled) onClick else ({})
    NavigationBarItem(
        selected = selected && state is AccessState.Enabled,
        onClick = effectiveClick,
        icon = {
            if (state is AccessState.Disabled) {
                Icon(Icons.Outlined.Lock, contentDescription = "Verrouillé")
            } else {
                Icon(icon, contentDescription = label)
            }
        },
        label = { Text(label, modifier = Modifier.alpha(alpha)) },
        modifier = modifier,
        enabled = state is AccessState.Enabled,
    )
}

/**
 * FAB. When disabled, rendered with reduced alpha and a lock icon; clicks ignored.
 */
@Composable
fun GatedFloatingActionButton(
    requirement: AccessRequirement,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    text: String? = null,
) {
    val state = accessStateOf(requirement)
    val effectiveIcon = if (state is AccessState.Disabled) Icons.Outlined.Lock else icon
    val effectiveClick = if (state is AccessState.Enabled) onClick else ({})
    val fabModifier = if (state is AccessState.Disabled) modifier.alpha(0.4f) else modifier
    if (expanded && text != null) {
        ExtendedFloatingActionButton(
            onClick = effectiveClick,
            icon = { Icon(effectiveIcon, contentDescription = contentDescription) },
            text = { Text(text) },
            modifier = fabModifier,
        )
    } else {
        FloatingActionButton(
            onClick = effectiveClick,
            modifier = fabModifier,
        ) {
            Icon(effectiveIcon, contentDescription = contentDescription)
        }
    }
}

/**
 * Icon button (e.g. top-bar action). When disabled, the icon is replaced by a
 * lock and the click is ignored.
 */
@Composable
fun GatedIconButton(
    requirement: AccessRequirement,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val state = accessStateOf(requirement)
    val effectiveIcon = if (state is AccessState.Disabled) Icons.Outlined.Lock else icon
    val effectiveClick = if (state is AccessState.Enabled) onClick else ({})
    IconButton(
        onClick = effectiveClick,
        modifier = if (state is AccessState.Disabled) modifier.alpha(0.4f) else modifier,
        enabled = state is AccessState.Enabled,
    ) {
        Icon(effectiveIcon, contentDescription = contentDescription)
    }
}

/**
 * Scope-marker so the helper above can be used inside RowScope/BottomBarScope.
 * Currently unused but reserved for future Scafold-based gating.
 */
@Suppress("unused")
private fun RowScope.noop() = Unit
