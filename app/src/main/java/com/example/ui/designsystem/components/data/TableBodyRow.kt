package com.example.ui.designsystem.components.data

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * A single body row in an [ElTable]. Renders cells aligned per their
 * column spec, with a divider below if it's not the last row.
 */
@Composable
internal fun TableBodyRow(
    row: ElTableRow,
    columns: List<ElTableColumn>,
    isLast: Boolean,
) {
    val c = ElTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (row.onClick != null) {
                        Modifier.clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = row.onClick,
                        )
                    } else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            row.cells.forEachIndexed { cellIndex, cell ->
                val col = columns.getOrNull(cellIndex) ?: ElTableColumn(title = "")
                Text(
                    text = cell,
                    color = c.textPrimary,
                    style = ElTheme.typography.bodyMedium,
                    textAlign = when (col.align) {
                        ElColumnAlign.START -> TextAlign.Start
                        ElColumnAlign.CENTER -> TextAlign.Center
                        ElColumnAlign.END -> TextAlign.End
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(col.weight),
                )
            }
        }
        if (!isLast) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(1.dp)
                    .background(c.outlineVariant),
            )
        }
    }
}
