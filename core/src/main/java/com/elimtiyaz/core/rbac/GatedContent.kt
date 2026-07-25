package com.elimtiyaz.core.rbac

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elimtiyaz.core.designsystem.ElImtiyazColors
import com.elimtiyaz.core.designsystem.ElImtiyazSpacing

/**
 * The visual treatment applied to any disabled UI node.
 *
 * Three flavours:
 *
 * 1. **Overlay** — wrap the original content with a semi-transparent scrim +
 *    lock icon. The content is still visible behind the scrim, communicating
 *    "this feature exists but is locked for you". Use on cards, list rows, tiles.
 *
 * 2. **Inline** — render the content at reduced alpha with a small lock badge
 *    next to the title. Use on FABs, icons, menu items where a full overlay
 *    would be too heavy.
 *
 * 3. **Placeholder** — replace the content with a friendly empty-state that
 *    explains WHY the feature is disabled. Use on full-screen pages.
 */

/** The alpha applied to disabled content (kept readable but clearly muted). */
private const val DISABLED_ALPHA = 0.38f

/**
 * Wrap any content with a semi-transparent scrim + lock icon.
 * The wrapped content is rendered behind the scrim at full opacity so its
 * shape is preserved. The scrim is non-interactive (clicks fall through).
 */
@Composable
fun DisabledOverlay(
    reason: DisableReason,
    modifier: Modifier = Modifier,
    showReason: Boolean = false,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        // Original content — rendered first so the scrim sits on top.
        content()
        // Scrim — semi-transparent surface tint.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f)),
        )
        // Lock badge — bottom-end corner.
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(ElImtiyazSpacing.x2)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f))
                .padding(horizontal = ElImtiyazSpacing.x2, vertical = ElImtiyazSpacing.x1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(ElImtiyazSpacing.x1))
            Text(
                text = "Verrouillé",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // Optional reason tooltip — center.
        if (showReason) {
            Text(
                text = reason.displayFr,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(ElImtiyazSpacing.x4),
            )
        }
    }
}

/**
 * Inline treatment: render content at reduced alpha with an optional lock icon.
 * Use on small UI affordances (FABs, icon buttons, chips) where a full overlay
 * would be visually noisy.
 */
@Composable
fun DisabledInline(
    modifier: Modifier = Modifier,
    showLock: Boolean = true,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.alpha(DISABLED_ALPHA),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ElImtiyazSpacing.x1),
    ) {
        content()
        if (showLock) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = "Verrouillé",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * Full-screen placeholder for a permanently-disabled page.
 * Use when a user navigates to a page they don't have access to (e.g. via deep
 * link) — the screen renders this instead of an error.
 */
@Composable
fun DisabledPlaceholder(
    reason: DisableReason,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(ElImtiyazSpacing.x8),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.size(ElImtiyazSpacing.x4))
        Text(
            text = "Fonctionnalité verrouillée",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.size(ElImtiyazSpacing.x2))
        Text(
            text = reason.displayFr,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (onBack != null) {
            Spacer(Modifier.size(ElImtiyazSpacing.x6))
            androidx.compose.material3.TextButton(onClick = onBack) { Text("Retour") }
        }
    }
}

/**
 * The high-level entry point screens use. Provide a [FeatureNode]; the helper
 * evaluates it against the current composition locals and either renders
 * [content] (Enabled), renders [content] wrapped in [DisabledOverlay] (Disabled),
 * or renders nothing (Hidden).
 *
 * Pass [disabledStyle] to control the visual treatment.
 */
@Composable
fun GatedContent(
    node: FeatureNode,
    modifier: Modifier = Modifier,
    disabledStyle: DisabledStyle = DisabledStyle.Overlay,
    content: @Composable () -> Unit,
) {
    when (val state = accessStateOf(node)) {
        is AccessState.Enabled  -> content()
        is AccessState.Disabled -> when (disabledStyle) {
            DisabledStyle.Overlay     -> DisabledOverlay(state.reason, modifier) { content() }
            DisabledStyle.Inline      -> DisabledInline(modifier) { content() }
            DisabledStyle.Placeholder -> DisabledPlaceholder(state.reason, modifier)
        }
        is AccessState.Hidden   -> { /* render nothing */ }
    }
}

/**
 * Variant of [GatedContent] that takes a raw [AccessRequirement] instead of a
 * full [FeatureNode]. Useful for one-off affordances (a FAB, a menu item) that
 * don't need to be registered in the feature tree.
 */
@Composable
fun GatedContent(
    requirement: AccessRequirement,
    modifier: Modifier = Modifier,
    hideWhenUnauthenticated: Boolean = false,
    disabledStyle: DisabledStyle = DisabledStyle.Inline,
    content: @Composable () -> Unit,
) {
    when (val state = accessStateOf(requirement, hideWhenUnauthenticated)) {
        is AccessState.Enabled  -> content()
        is AccessState.Disabled -> when (disabledStyle) {
            DisabledStyle.Overlay     -> DisabledOverlay(state.reason, modifier) { content() }
            DisabledStyle.Inline      -> DisabledInline(modifier) { content() }
            DisabledStyle.Placeholder -> DisabledPlaceholder(state.reason, modifier)
        }
        is AccessState.Hidden   -> { /* render nothing */ }
    }
}

/** Visual flavour to apply when the node is disabled. */
enum class DisabledStyle {
    /** Semi-transparent scrim + lock badge. Best for cards / tiles / list rows. */
    Overlay,

    /** Reduced alpha + small lock icon. Best for FABs / icon buttons / chips. */
    Inline,

    /** Full-screen "feature locked" placeholder. Best for full pages. */
    Placeholder,
}
