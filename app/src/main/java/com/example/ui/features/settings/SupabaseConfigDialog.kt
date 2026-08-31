package com.example.ui.features.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

/**
 * Supabase connection dialog — configuration entry point for the database.
 *
 * FIX (out of context): this dialog used to live inside the student roster
 * (a teacher-facing screen) where entering DB URLs/anon keys made no sense.
 * It now lives in Settings → Synchronisation, where connection configuration
 * belongs.
 */
@Composable
internal fun SupabaseConfigDialog(
    currentUrl: String,
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var url by remember { mutableStateOf(currentUrl) }
    var anonKey by remember { mutableStateOf(currentKey) }
    // SEC-004 (T-064): the anon key is a credential-looking secret on screen —
    // mask it by default with a show/hide toggle (shoulder-surfing / screen
    // recording protection), exactly like a password field.
    var keyVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text("Connexion Base de Données", style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Configurez l'accès à Supabase pour synchroniser les élèves, parents et paiements de votre établissement :",
                    style = MaterialTheme.typography.bodyMedium,
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Supabase Project URL") },
                    placeholder = { Text("https://xyzcompany.supabase.co") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = anonKey,
                    onValueChange = { anonKey = it },
                    label = { Text("Supabase Anon Key / API Key") },
                    placeholder = { Text("eyJhbGciOiJIUzI1NiIsInR5c...") },
                    singleLine = false,
                    maxLines = 3,
                    // SEC-004 (T-064): masked by default, toggle to reveal.
                    visualTransformation =
                        if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (keyVisible) "Masquer la clé" else "Afficher la clé",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    // SEC-004 (T-064): the build toolchain is none of the end
                    // user's business — the old helper text leaked "Google AI
                    // Studio". Env-var guidance only.
                    text = "💡 Vous pouvez aussi définir SUPABASE_URL et SUPABASE_ANON_KEY dans le fichier .env de l'application avant de la compiler.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(url, anonKey) },
                enabled = url.isNotBlank() && anonKey.isNotBlank(),
            ) {
                Text("Enregistrer & Synchroniser")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annuler")
            }
        },
    )
}
