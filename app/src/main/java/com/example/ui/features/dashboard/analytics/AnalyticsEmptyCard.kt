package com.example.ui.features.dashboard.analytics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardVariant
import com.example.ui.designsystem.theme.ElTheme

/**
 * The honest empty card (§15.16 — never fabricated trends).
 *
 * T-340 (STATS-400): relocated from RevenueTrendExplorerCard.kt when that
 * file was deleted per the owner's kill list — the other analytics cards
 * still consume it for their honest empty states.
 */
@Composable
internal fun AnalyticsEmptyCard(title: String, subtitle: String? = null) {
    val c = ElTheme.colors
    ElCard(modifier = Modifier.fillMaxWidth(), variant = ElCardVariant.OUTLINED) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                color = c.textPrimary,
                style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = subtitle ?: "Aucune donnée sur la période sélectionnée.",
                color = c.textMuted,
                style = ElTheme.typography.bodySmall,
            )
        }
    }
}
