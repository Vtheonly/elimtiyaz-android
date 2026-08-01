package com.example.ui.designsystem.components.data

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElCardShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Unified data table — header row + body rows in a single signature card
 * surface. Supports sortable columns (visual only — sorting logic is the
 * caller's responsibility) and row tap.
 *
 * Composition:
 *  - [TableHeader] renders the title row
 *  - [TableBodyRow] renders each data row with optional divider
 *  - Caller-supplied [emptyState] composable renders when rows is empty
 */
@Composable
fun ElTable(
    columns: List<ElTableColumn>,
    rows: List<ElTableRow>,
    modifier: Modifier = Modifier,
    sortColumn: Int? = null,
    sortAscending: Boolean = true,
    onSortToggle: ((Int) -> Unit)? = null,
    emptyState: @Composable (() -> Unit)? = null,
) {
    val c = ElTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ElCardShape)
            .background(c.surface)
            .border(ElTheme.borders.thin, c.outline, ElCardShape)
            .padding(1.dp),
    ) {
        TableHeader(
            columns = columns,
            sortColumn = sortColumn,
            sortAscending = sortAscending,
            onSortToggle = onSortToggle,
        )

        if (rows.isEmpty() && emptyState != null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                emptyState()
            }
        } else {
            rows.forEachIndexed { index, row ->
                TableBodyRow(
                    row = row,
                    columns = columns,
                    isLast = index == rows.lastIndex,
                )
            }
        }
    }
}
