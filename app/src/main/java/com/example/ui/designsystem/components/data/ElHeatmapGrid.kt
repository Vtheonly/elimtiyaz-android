package com.example.ui.designsystem.components.data

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.theme.ElTheme

/**
 * ElHeatmapGrid — the collection heatmap matrix (PARITY-003).
 *
 * The native twin of the desktop's `collection-heatmap-card.tsx` (a CSS
 * grid, NOT Recharts): school-weekday rows × calendar-month columns, cell
 * intensity = the desktop's alpha scale `[0, 0.22, 0.42, 0.66, 0.92]` over
 * the brand blue, quantized by the ENGINE's 0–4 level (quantization is a
 * calculation, never re-done here). Level-0 cells render as dashed empty
 * slots (desktop convention).
 *
 * @param rows engine rows (Dim→Jeu), cells parallel to [columnLabels].
 * @param columnLabels month labels ("Sep", "Oct", …).
 */
@Composable
fun ElHeatmapGrid(
    rows: List<ElHeatmapRow>,
    columnLabels: List<String>,
    modifier: Modifier = Modifier,
    cellHeight: Dp = 26.dp,
    baseColor: Color = ElChartPalette.primary,
) {
    val c = ElTheme.colors
    val alphas = floatArrayOf(0f, 0.22f, 0.42f, 0.66f, 0.92f)

    Column(modifier = modifier.fillMaxWidth()) {
        // Month header row
        Row(modifier = Modifier.padding(start = 34.dp)) {
            columnLabels.forEachIndexed { i, label ->
                Box(
                    modifier = Modifier.weight(1f).padding(horizontal = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (columnLabels.size > 8 && i % 2 == 1) "" else label,
                        color = c.textMuted,
                        style = ElTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.height(2.dp))

        rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.rowLabel,
                    color = c.textSecondary,
                    style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1,
                    modifier = Modifier.width(30.dp),
                )
                row.cells.forEach { cell ->
                    val level = cell.level.coerceIn(0, 4)
                    val alpha = alphas[level]
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 1.dp, vertical = 1.dp)
                            .height(cellHeight)
                            .clip(RoundedCornerShape(4.dp))
                            .then(
                                if (level == 0) {
                                    Modifier.border(
                                        width = 1.dp,
                                        color = c.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(4.dp),
                                    )
                                } else {
                                    Modifier.background(baseColor.copy(alpha = alpha))
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        // The count badge (like the desktop's cell text, white at high intensity)
                        if (level >= 3) {
                            Text(
                                text = compactCellText(cell.amount),
                                color = Color.White,
                                style = ElTheme.typography.labelSmall.copy(fontSize = 8.sp, textAlign = TextAlign.Center),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Compact k/M formatting for heatmap cells (desktop compactK convention). */
internal fun compactCellText(v: Float): String = when {
    v >= 1_000_000f -> {
        val m = (v / 100_000f).toInt() / 10f
        "${m}M"
    }
    v >= 10_000f -> "${(v / 1_000f).toInt()}k"
    v >= 1_000f -> {
        val k = (v / 100f).toInt() / 10f
        "${k}k"
    }
    else -> "${v.toInt()}"
}
