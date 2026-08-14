package com.example.ui.features.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ui.components.ElButton
import com.example.ui.components.ElCard
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElTextField
import com.example.ui.theme.elDesignTokens

/**
 * Login screen.
 *
 * FIX (login-blocks): the email + password fields now bind DIRECTLY to the
 * ViewModel state (single source of truth). The previous implementation kept
 * local `mutableStateOf` copies that desynced from the VM after a demo-account
 * tap, so the user would see one email on screen but the VM would sign in
 * with a different one (or an empty one). With single-source-of-truth state,
 * the demo-account button updates the VM, the screen re-renders from the VM,
 * and the sign-in call uses exactly what's on screen.
 *
 * Navigation: this screen does NOT call `onSignedIn` itself. The
 * [com.example.ui.navigation.AppNavHost] observes `SessionManager.state`
 * and navigates to Main the moment a session appears. This avoids the
 * duplicate-navigate race that previously froze the back stack.
 */
@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onChangePassword: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val tokens = elDesignTokens()

    // Defensive fallback: if for any reason the AppNavHost session observer
    // doesn't fire (e.g. SessionManager was already non-null before this
    // composable entered composition), still navigate when signedIn flips.
    // This is a SAFETY NET only — the primary nav trigger lives in AppNavHost.
    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    var passwordVisible by remember { mutableStateOf(false) }

    ElScaffold {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Brand logo
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(tokens.primaryDiagonalBrush),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "EI",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                        ),
                        color = Color.White,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "El-Imtiyaz Staff",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        "Plateforme de gestion scolaire",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))

                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            "Connexion",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )

                        // Email binds DIRECTLY to VM state (single source of truth)
                        ElTextField(
                            value = state.email,
                            onValueChange = { viewModel.updateEmail(it) },
                            label = "Email",
                            leadingIcon = Icons.Default.Person,
                            placeholder = "nom@elimtiyaz.dz",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            isError = state.error != null,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Password binds DIRECTLY to VM state
                        ElTextField(
                            value = state.password,
                            onValueChange = { viewModel.updatePassword(it) },
                            label = "Mot de passe",
                            leadingIcon = Icons.Default.Lock,
                            trailingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = { passwordVisible = !passwordVisible },
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (passwordVisible) "Masquer" else "Afficher",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = state.error != null,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        state.error?.let { err ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                                    .padding(12.dp),
                            ) {
                                Text(
                                    err,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }

                        ElButton(
                            text = if (state.isLoading) "Connexion…" else "Déverrouiller l'espace",
                            onClick = { viewModel.signIn(state.email, state.password) },
                            enabled = !state.isLoading,
                            loading = state.isLoading,
                            fullWidth = true,
                        )
                    }
                }

                // Change password link
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onChangePassword,
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        "Changer le mot de passe",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Note: demo account shortcuts were removed because the app now
                // authenticates against the REAL Supabase Auth instance. Users
                // must sign in with their real Supabase credentials.
            }
        }
    }
}
