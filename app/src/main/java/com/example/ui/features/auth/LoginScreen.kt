package com.example.ui.features.auth

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.theme.ElTheme

@Composable
fun LoginScreen(
    onSignedIn: () -> Unit,
    onChangePassword: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val c = ElTheme.colors

    LaunchedEffect(state.signedIn) {
        if (state.signedIn) onSignedIn()
    }

    var passwordVisible by remember { mutableStateOf(false) }

    ElScaffold { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(ElTheme.spacing.xl),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.lg),
            ) {
                // Brand logo
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(c.primaryBrush),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "EI",
                        style = ElTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = c.textOnColor,
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "El-Imtiyaz Staff",
                        style = ElTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = c.textPrimary,
                    )
                    Text(
                        "Plateforme de gestion scolaire",
                        style = ElTheme.typography.bodyMedium,
                        color = c.textSecondary,
                    )
                }

                Spacer(Modifier.height(ElTheme.spacing.sm))

                ElCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md),
                    ) {
                        Text(
                            "Connexion",
                            style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = c.textPrimary,
                        )

                        // Email Field
                        ElTextField(
                            value = state.email,
                            onValueChange = { viewModel.updateEmail(it) },
                            label = "Email",
                            leadingIcon = Icons.Default.Person,
                            placeholder = "admin@elimtiyaz.dz",
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            isError = state.error != null,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Password Field
                        ElTextField(
                            value = state.password,
                            onValueChange = { viewModel.updatePassword(it) },
                            label = "Mot de passe",
                            leadingIcon = Icons.Default.Lock,
                            trailingIcon = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            onTrailingIconClick = { passwordVisible = !passwordVisible },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            isError = state.error != null,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        // Error Banner
                        state.error?.let { err ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ElTheme.shapes.small)
                                    .background(c.danger.copy(alpha = 0.12f))
                                    .padding(ElTheme.spacing.md),
                            ) {
                                Text(
                                    err,
                                    color = c.danger,
                                    style = ElTheme.typography.bodySmall,
                                )
                            }
                        }

                        // Primary Connect Button
                        ElButton(
                            text = if (state.isLoading) "Connexion…" else "Déverrouiller l'espace",
                            onClick = { viewModel.signIn(state.email, state.password) },
                            enabled = !state.isLoading,
                            loading = state.isLoading,
                            fullWidth = true,
                        )

                        Spacer(Modifier.height(ElTheme.spacing.xs))

                        // Quick Demo Access Section (Guaranteed Login)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HorizontalDivider(modifier = Modifier.weight(1f), color = c.outlineVariant)
                            Text(
                                "  OU ACCÈS RAPIDE  ",
                                style = ElTheme.textStyles.chartMicro.copy(letterSpacing = 1.sp),
                                color = c.textMuted,
                            )
                            HorizontalDivider(modifier = Modifier.weight(1f), color = c.outlineVariant)
                        }

                        ElButton(
                            text = "Accéder en mode Démo (SuperAdmin)",
                            onClick = { viewModel.signInDemo("admin@elimtiyaz.dz") },
                            variant = ElButtonVariant.SECONDARY,
                            icon = Icons.Default.AccountCircle,
                            enabled = !state.isLoading,
                            fullWidth = true,
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs),
                        ) {
                            ElChip(
                                text = "Direction",
                                onClick = {
                                    viewModel.updateEmail("admin@elimtiyaz.dz")
                                    viewModel.updatePassword("demo1234")
                                    viewModel.signInDemo("admin@elimtiyaz.dz")
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ElChip(
                                text = "Finances",
                                onClick = {
                                    viewModel.updateEmail("finance@elimtiyaz.dz")
                                    viewModel.updatePassword("demo1234")
                                    viewModel.signInDemo("finance@elimtiyaz.dz")
                                },
                                modifier = Modifier.weight(1f),
                            )
                            ElChip(
                                text = "Enseignant",
                                onClick = {
                                    viewModel.updateEmail("enseignant@elimtiyaz.dz")
                                    viewModel.updatePassword("demo1234")
                                    viewModel.signInDemo("enseignant@elimtiyaz.dz")
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                ElButton(
                    text = "Changer le mot de passe",
                    onClick = onChangePassword,
                    variant = ElButtonVariant.GHOST,
                )
            }
        }
    }
}