package com.example.ui.features.financials

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.example.core.PaymentCategory
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.theme.ElTheme

internal fun cleanDescription(raw: String, fallbackCategory: String): String {
    if (raw.isBlank()) return "Opération sur $fallbackCategory"
    return when {
        raw.contains("réconciliation 0063", ignoreCase = true) -> "Régularisation de remise (recalcul import)"
        raw.contains("Devis annuel", ignoreCase = true) -> "Devis annuel de scolarité"
        raw.contains("Encaissement", ignoreCase = true) -> raw
        raw.contains("Tranche", ignoreCase = true) -> raw
        else -> raw
    }
}

internal fun categoryFr(cat: PaymentCategory): String = when (cat) {
    PaymentCategory.TUITION -> "Scolarité"
    PaymentCategory.TRANSPORT -> "Transport"
    PaymentCategory.CANTEEN -> "Cantine"
    PaymentCategory.UNIFORM -> "Uniforme"
    PaymentCategory.BOOKS -> "Fournitures & Livres"
    PaymentCategory.PARENT_CREDIT -> "Crédit Parent"
    PaymentCategory.EXTRACURRICULAR -> "Parascolaire"
    else -> cat.name.replace("_", " ")
}

/**
 * T-460 pass C: rebuilt on the DS card (was the legacy gradient ElCard with
 * an extra hand-rolled 12dp inner padding — the DS COMPACT card applies its
 * own token padding). The Color param stays a Color so every call site picks
 * its semantic token (c.primary / c.success / c.danger) at the source.
 */
@Composable
internal fun MetricMiniCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    ElCard(modifier = modifier, size = ElCardSize.COMPACT) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = ElTheme.typography.labelSmall,
                color = ElTheme.colors.textSecondary,
            )
            Spacer(Modifier.height(ElTheme.spacing.xs))
            Text(
                text = value,
                style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
        }
    }
}
