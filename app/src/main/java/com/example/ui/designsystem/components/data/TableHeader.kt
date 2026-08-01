package com.example.ui.designsystem.components.data

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElCardShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * The header row of an [ElTable]. Renders column titles with optional
 * sort indicator. Sorting is visual only — the caller owns the logic.
 */
@Composable
internal fun TableHeader(
    columns: List<ElTableColumn>,
    sortColumn: Int?,
    sortAscending: Boolean,
    onSortToggle: ((Int) -> Unit)?,
) {
    val c = ElTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        columns.forEachIndexed { index, col ->
            TableColumnHeader(
                column = col,
                isSorted = sortColumn == index,
                sortAscending = sortAscending,
                onToggle = { onSortToggle?.invoke(index) },
                modifier = Modifier.weight(col.weight),
            )
        }
    }
}

/** A single column header cell. */
@Composable
private fun TableColumnHeader(
    column: ElTableColumn,
    isSorted: Boolean,
    sortAscending: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = ElTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = when (column.align) {
            ElColumnAlign.START -> Arrangement.Start
            ElColumnAlign.CENTER -> Arrangement.Center
            ElColumnAlign.END -> Arrangement.End
        },
        modifier = modifier.then(
            if (column.sortable) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Tab,
                    onClick = onToggle,
                )
            } else Modifier
        ),
    ) {
        Text(
            text = column.title.uppercase(),
            color = c.textSecondary,
            style = ElTheme.textStyles.overline,
            textAlign = when (column.align) {
                ElColumnAlign.START -> TextAlign.Start
                ElColumnAlign.CENTER -> TextAlign.Center
                ElColumnAlign.END -> TextAlign.End
            },
            modifier = Modifier.weight(1f, fill = false),
        )
        if (column.sortable && isSorted) {
            Spacer(Modifier.size(4.dp))
            Icon(
                imageVector = if (sortAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = null,
                tint = c.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
