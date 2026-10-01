package com.example.ui.features.personnel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElGradient
import com.example.ui.designsystem.theme.ElTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.Session
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElGradientStatCard
import com.example.ui.designsystem.components.display.ElSectionHeader

@Composable
fun SignOutScreen(session: Session, onSignOut: () -> Unit) {
    val c = ElTheme.colors
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElGradientStatCard(
            title = "Session Utilisateur",
            value = session.displayName,
            subtitle = "Gérez votre session et déconnexion",
            gradient = ElGradient.BRAND,
            modifier = Modifier.fillMaxWidth(),
        )

        ElCard(modifier = Modifier.fillMaxWidth(), size = ElCardSize.STANDARD) {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ElSectionHeader(title = "Informations")
                Text("Email: ${session.email}", style = ElTheme.typography.bodyMedium, color = c.textPrimary)
                Text(
                    "Rôle: ${session.role.code}",
                    style = ElTheme.typography.bodyMedium.copy(color = c.primary, fontWeight = FontWeight.Medium),
                )
                Text(
                    "Permissions: ${session.permissions.size} actives",
                    style = ElTheme.typography.bodySmall,
                    color = c.textSecondary,
                )
            }
        }

        ElButton(
            text = "Se déconnecter",
            onClick = onSignOut,
            variant = ElButtonVariant.DANGER,
            fullWidth = true,
        )
    }
}
