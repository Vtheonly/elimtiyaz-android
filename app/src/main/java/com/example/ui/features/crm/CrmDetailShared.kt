package com.example.ui.features.crm

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * T-460 pass G-e: the CRM detail family's shared DS building blocks.
 *
 * The legacy `ui.components.ElInfoRow` (label/value row on the old tokens) is
 * retired with the kit; this is the DS replacement used by StudentDetail and
 * ParentDetail. Semantics preserved: label left (secondary), value right
 * (primary, optional emphasis colour).
 */
@Composable
internal fun ElInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = ElTheme.colors.textPrimary,
) {
    val c = ElTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = ElTheme.typography.bodySmall,
            color = c.textSecondary,
        )
        Text(
            text = value,
            style = ElTheme.typography.bodySmall,
            color = valueColor,
        )
    }
}
