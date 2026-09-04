package com.example.ui.designsystem.components.tabs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.noRippleClickable
import com.example.ui.designsystem.theme.ElPillShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Unified tab row — animated indicator slides between tabs.
 * Bold geometric style: filled pill indicator on selected tab.
 */
@Composable
fun ElTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ElPillShape)
            .background(c.surfaceVariant)
            .padding(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            tabs.forEachIndexed { index, label ->
                TabItem(
                    label = label,
                    selected = index == selectedIndex,
                    onClick = { onSelected(index) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Scrollable horizontal tab row — the DS answer to hub screens whose tab
 * count exceeds what the fixed [ElTabRow] can present without squeezing
 * each label. This is the T-044 pass-3 prerequisite: the 6-tab
 * FinancialsHub (and the other ModernSecondaryTabRow call sites in the
 * legacy tree) can only migrate to the design system once this component
 * exists, so the migration becomes a mechanical import/argument swap.
 *
 * Parity with the legacy `ui.components.ModernSecondaryTabRow` this
 * replaces (all of it preserved so call-site behaviour does not change):
 *   - LazyRow of pill tabs, horizontally scrollable when the row overflows;
 *   - the selected pill is auto-scrolled into view when the selection
 *     changes programmatically (hub screens switch tabs from deep links);
 *   - single-line labels.
 * The visual language is the DS's own (ElTheme tokens, [ElPillShape] pills
 * on a surfaceVariant track) — deliberately NOT the legacy MaterialTheme
 * colors, per the DUP-003 migration direction.
 *
 * ADR-008 note: this component does NOT create navigation routes; it is a
 * pure leaf widget — no repository, no session, no side effects.
 */
@Composable
fun ElScrollableTabRow(
    tabs: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (selectedIndex in tabs.indices) {
            listState.animateScrollToItem(selectedIndex)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            // Stable handle for UI tests: LazyRow composes only visible
            // items, so tests must scroll via this tag before asserting on
            // off-screen pills. Harmless in production semantics.
            .testTag("el_scrollable_tab_row")
            .clip(ElPillShape)
            .background(ElTheme.colors.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(tabs) { index, label ->
            ScrollableTabItem(
                label = label,
                isSelected = index == selectedIndex,
                onClick = { onSelected(index) },
            )
        }
    }
}

/** A single pill inside an [ElScrollableTabRow]. */
@Composable
private fun ScrollableTabItem(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val c = ElTheme.colors
    val textColor by animateColorAsState(
        if (isSelected) c.textOnColor else c.textSecondary,
        label = "scrollable-tab-fg",
    )
    Box(
        modifier = Modifier
            .clip(ElPillShape)
            .then(if (isSelected) Modifier.background(c.primaryBrush) else Modifier)
            .noRippleClickable(role = Role.Tab, onClick = onClick)
            // NB: the parameter is deliberately named `isSelected` so that
            // `selected` inside this lambda resolves to the semantics
            // receiver's extension var, not a val reassignment.
            .semantics { selected = isSelected }
            .padding(vertical = 10.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            style = ElTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/** A single tab inside an [ElTabRow]. */
@Composable
private fun TabItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    val textColor by animateColorAsState(
        if (selected) c.textOnColor else c.textSecondary,
        label = "tab-fg",
    )
    Box(
        modifier = modifier
            .clip(ElPillShape)
            .then(if (selected) Modifier.background(c.primaryBrush) else Modifier)
            .noRippleClickable(role = Role.Tab, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor,
            style = ElTheme.typography.labelLarge,
            maxLines = 1,
        )
    }
}

/**
 * Vertical icon+label tab list — for settings sections, dashboards.
 */
@Composable
fun ElVerticalTabList(
    items: List<Triple<ImageVector, String, Boolean>>,
    onItemClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        items.forEachIndexed { index, (icon, label, selected) ->
            VerticalTabItem(
                icon = icon,
                label = label,
                selected = selected,
                onClick = { onItemClick(index) },
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** A single vertical tab item with icon-in-circle + label. */
@Composable
private fun VerticalTabItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = ElTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ElPillShape)
            .then(if (selected) Modifier.background(c.primary.copy(alpha = 0.10f)) else Modifier)
            .noRippleClickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(ElPillShape)
                .then(
                    if (selected) Modifier.background(c.primary)
                    else Modifier.background(c.surfaceVariant)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) c.textOnColor else c.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = if (selected) c.primary else c.textPrimary,
            style = ElTheme.typography.labelLarge,
        )
    }
}
