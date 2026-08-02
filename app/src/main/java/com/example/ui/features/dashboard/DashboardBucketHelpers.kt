package com.example.ui.features.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.designsystem.theme.Tangerine600

/**
 * Color for each debt-aging bucket, used by the donut chart on the dashboard.
 *
 * Mapping mirrors the desktop's debt-aging palette:
 *   - 0_30     → success (green)
 *   - 31_60    → info    (sky blue)
 *   - 61_90    → warning (tangerine)
 *   - 91_180   → Tangerine600 (deep orange)
 *   - 180_plus → danger  (rose)
 */
@Composable
internal fun bucketColor(bucket: String): Color = when (bucket) {
    "0_30", "0-30"     -> ElTheme.colors.success
    "31_60", "31-60"   -> ElTheme.colors.info
    "61_90", "61-90"   -> ElTheme.colors.warning
    "91_180", "91-180" -> Tangerine600
    "180_plus", "180+" -> ElTheme.colors.danger
    else               -> ElTheme.colors.primary
}

/** Human-readable label for each aging bucket, used in the donut legend. */
internal fun bucketLabel(bucket: String): String = when (bucket) {
    "0_30", "0-30"     -> "0–30 j"
    "31_60", "31-60"   -> "31–60 j"
    "61_90", "61-90"   -> "61–90 j"
    "91_180", "91-180" -> "91–180 j"
    "180_plus", "180+" -> "180+ j"
    else               -> bucket
}
