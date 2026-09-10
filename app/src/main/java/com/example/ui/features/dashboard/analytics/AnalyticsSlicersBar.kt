package com.example.ui.features.dashboard.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.paymentCategoryLabelFr
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.data.ElChartPalette
import com.example.ui.designsystem.theme.ElTheme

/**
 * AnalyticsSlicersBar — the cross-filtering chip row (PARITY-003).
 *
 * The native twin of the desktop's `analytics-slicers.tsx`: method chips
 * (the 3 canonical) + category chips (the engine's presentCategories),
 * "N opérations · total" badge, and Réinitialiser. Power BI slicer
 * semantics — the ViewModel re-runs the ENGINE's applyAnalyticsFilters on
 * the canonical payments stream (never a UI-side re-implementation).
 */
@Composable
internal fun AnalyticsSlicersBar(
    methodFilters: Set<String>,
    categoryFilters: Set<String>,
    presentCategories: List<String>,
    sliceCount: Int,
    sliceTotalDzd: Long,
    onToggleMethod: (String) -> Unit,
    onToggleCategory: (String) -> Unit,
    onReset: () -> Unit,
) {
    val c = ElTheme.colors

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.FilterAlt,
                    contentDescription = null,
                    tint = c.textSecondary,
                    modifier = Modifier.padding(end = 6.dp),
                )
                Text(
                    text = "Segmentation",
                    style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = c.textPrimary,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$sliceCount op. • ${compactDzd(sliceTotalDzd)} DA",
                    color = c.textMuted,
                    style = ElTheme.typography.labelSmall.copy(fontSize = 10.sp),
                )
                ElIconButton(
                    icon = Icons.Default.Refresh,
                    onClick = onReset,
                    contentDescription = "Réinitialiser les filtres",
                )
            }
        }

        // Method chips — canonical 3 + their colors (desktop convention)
        val methods = listOf(
            Triple("cash", "Espèces", ElChartPalette.primary),
            Triple("check", "Chèque", ElChartPalette.gold),
            Triple("transfer", "Virement", ElChartPalette.cyan),
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(methods) { (code, label, color) ->
                AnalyticsFilterChip(
                    label = label,
                    selected = code in methodFilters,
                    color = color,
                    onClick = { onToggleMethod(code) },
                )
            }
        }

        // Category chips — the engine's presentCategories (FR-sorted)
        if (presentCategories.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(presentCategories) { code ->
                    AnalyticsFilterChip(
                        label = paymentCategoryLabelFr(code),
                        selected = code in categoryFilters,
                        color = ElChartPalette.violet,
                        onClick = { onToggleCategory(code) },
                    )
                }
            }
        }
    }
}

/** One slicer chip (selected = filled, unselected = outlined). */
@Composable
private fun AnalyticsFilterChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    val c = ElTheme.colors
    Box(
        modifier = Modifier
            .then(
                if (selected) {
                    Modifier.background(color.copy(alpha = 0.18f), RoundedCornerShape(50))
                } else {
                    Modifier.border(1.dp, c.surfaceVariant, RoundedCornerShape(50))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) c.textPrimary else c.textSecondary,
            style = ElTheme.typography.labelMedium.copy(fontSize = 11.sp),
        )
    }
}

/** Compact DZD (k / M) — the desktop's compactK convention. */
internal fun compactDzd(dzd: Long): String = when {
    dzd >= 1_000_000L -> {
        val m = (dzd / 100_000L).toInt() / 10.0
        trimTrailingZero("${m}M")
    }
    dzd >= 10_000L -> "${dzd / 1_000L}k"
    dzd >= 1_000L -> {
        val k = (dzd / 100L).toInt() / 10.0
        trimTrailingZero("${k}k")
    }
    else -> "$dzd"
}

private fun trimTrailingZero(s: String): String =
    if (s.endsWith(".0")) s.dropLast(2) else s
