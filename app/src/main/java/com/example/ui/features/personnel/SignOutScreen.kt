package com.example.ui.features.personnel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.Session
import com.example.ui.components.ElButton
import com.example.ui.components.ElButtonStyle
import com.example.ui.components.ElCard
import com.example.ui.components.ElGradientStatCard
import com.example.ui.components.ElSectionHeader
import com.example.ui.theme.PrimaryBlue

@Composable
fun SignOutScreen(session: Session, onSignOut: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ElGradientStatCard(
            title = "Session Utilisateur",
            value = session.displayName,
            subtitle = "Gérez votre session et déconnexion",
            modifier = Modifier.fillMaxWidth(),
        )

        ElCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ElSectionHeader(title = "Informations")
                Text("Email: ${session.email}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Rôle: ${session.role.code}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = PrimaryBlue, fontWeight = FontWeight.Medium),
                )
                Text(
                    "Permissions: ${session.permissions.size} actives",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        ElButton(
            text = "Se déconnecter",
            onClick = onSignOut,
            style = ElButtonStyle.Danger,
            fullWidth = true,
        )
    }
}
