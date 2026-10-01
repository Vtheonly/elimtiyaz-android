package com.example.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.overlays.ElDialogShell
import com.example.ui.designsystem.theme.ElTheme

/**
 * Supabase connection dialog — configuration entry point for the database.
 *
 * FIX (out of context): this dialog used to live inside the student roster
 * (a teacher-facing screen) where entering DB URLs/anon keys made no sense.
 * It now lives in Settings → Synchronisation, where connection configuration
 * belongs.
 *
 * T-460 pass G-d (issue #3 F-07): the raw-M3 AlertDialog → the DS
 * ElDialogShell + ElTextField (the last unregistered raw surface the audit
 * found — it was missing from the pass G list). The SEC-004 key-masking
 * contract is preserved verbatim.
 */
@Composable
internal fun SupabaseConfigDialog(
    currentUrl: String,
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    val c = ElTheme.colors
    var url by remember { mutableStateOf(currentUrl) }
    var anonKey by remember { mutableStateOf(currentKey) }
    // SEC-004 (T-064): the anon key is a credential-looking secret on screen —
    // mask it by default with a show/hide toggle (shoulder-surfing / screen
    // recording protection), exactly like a password field.
    var keyVisible by remember { mutableStateOf(false) }

    ElDialogShell(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = c.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Connexion Base de Données",
                    style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = c.textPrimary,
                )
            }

            Text(
                text = "Configurez l'accès à Supabase pour synchroniser les élèves, parents et paiements de votre établissement :",
                style = ElTheme.typography.bodyMedium,
                color = c.textSecondary,
            )

            ElTextField(
                value = url,
                onValueChange = { url = it },
                label = "Supabase Project URL",
                placeholder = "https://xyzcompany.supabase.co",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            ElTextField(
                value = anonKey,
                onValueChange = { anonKey = it },
                label = "Supabase Anon Key / API Key",
                placeholder = "eyJhbGciOiJIUzI1NiIsInR5c...",
                singleLine = false,
                // SEC-004 (T-064): masked by default, toggle to reveal.
                visualTransformation =
                    if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                onTrailingIconClick = { keyVisible = !keyVisible },
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                // SEC-004 (T-064): the build toolchain is none of the end
                // user's business — the old helper text leaked "Google AI
                // Studio". Env-var guidance only.
                text = "💡 Vous pouvez aussi définir SUPABASE_URL et SUPABASE_ANON_KEY dans le fichier .env de l'application avant de la compiler.",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ElButton(
                    text = "Annuler",
                    onClick = onDismiss,
                    variant = ElButtonVariant.GHOST,
                    modifier = Modifier.weight(1f),
                )
                ElButton(
                    text = "Enregistrer & Synchroniser",
                    onClick = { onSave(url, anonKey) },
                    enabled = url.isNotBlank() && anonKey.isNotBlank(),
                    variant = ElButtonVariant.PRIMARY,
                    modifier = Modifier.weight(1.5f),
                )
            }
        }
    }
}
