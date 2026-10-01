package com.example.ui.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.input.ElSwitch
import com.example.ui.designsystem.theme.ElTheme

/**
 * Preferences toggle row — T-044/UI-unification: rebuilt on the design-system
 * switch + tokens (was: raw M3 Switch + PrimaryBlue legacy token).
 * Behaviour and the ToggleRow(icon, label, sublabel, checked, …) contract
 * are preserved for PreferencesSection.
 */
@Composable
internal fun ToggleRow(
    icon: ImageVector,
    label: String,
    sublabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val c = ElTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ElTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(c.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = c.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(ElTheme.spacing.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = ElTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = c.textPrimary,
            )
            Text(
                text = sublabel,
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
            )
        }
        ElSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}
