package com.example.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.input.ElDropdown
import com.example.ui.designsystem.components.input.ElDropdownOption
import com.example.ui.designsystem.components.display.ElSectionHeader

@Composable
internal fun PreferencesSection(
    settings: SettingsState,
    onDarkMode: (Boolean) -> Unit,
    onNotifications: (Boolean) -> Unit,
    onForceOffline: (Boolean) -> Unit,
    onLanguage: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElSectionHeader(title = "Préférences")
        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ToggleRow(
                    icon = Icons.Default.DarkMode,
                    label = "Mode sombre",
                    sublabel = "Thème foncé pour les écrans",
                    checked = settings.darkMode,
                    onCheckedChange = onDarkMode,
                )
                ToggleRow(
                    icon = Icons.Default.Notifications,
                    label = "Notifications",
                    sublabel = "Recevoir les alertes push",
                    checked = settings.notificationsEnabled,
                    onCheckedChange = onNotifications,
                )
                ToggleRow(
                    icon = Icons.Default.CloudOff,
                    label = "Mode hors-ligne",
                    sublabel = "Forcer la désactivation du réseau",
                    checked = settings.forceOffline,
                    onCheckedChange = onForceOffline,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        ElDropdown(
                            label = "Langue",
                            // selectedValue must match option.value (the ISO code),
                            // not the display label — the design-system dropdown
                            // resolves the selected option by value equality.
                            selectedValue = settings.language,
                            options = listOf(
                                ElDropdownOption(value = "fr", label = "Français"),
                                ElDropdownOption(value = "ar", label = "العربية"),
                                ElDropdownOption(value = "en", label = "English"),
                            ),
                            onSelected = { option -> onLanguage(option.value) },
                        )
                    }
                }
            }
        }
    }
}
