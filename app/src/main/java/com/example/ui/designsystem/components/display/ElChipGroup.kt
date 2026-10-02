@file:OptIn(ExperimentalLayoutApi::class)

package com.example.ui.designsystem.components.display

import androidx.compose.foundation.layout.ExperimentalLayoutApi

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * Chip group — lays out multiple filter chips with consistent spacing.
 *
 * Each entry is `(label, selected)`. [onToggle] receives the index that was tapped.
 */
@Composable
fun ElChipGroup(
    chips: List<Pair<String, Boolean>>,
    onToggle: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
        verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
    ) {
        chips.forEachIndexed { index, (label, selected) ->
            ElChip(
                text = label,
                variant = ElChipVariant.FILTER,
                selected = selected,
                onClick = { onToggle(index) },
            )
        }
    }
}
