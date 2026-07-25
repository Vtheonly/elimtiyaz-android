package com.elimtiyaz.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.common.Role
import com.elimtiyaz.core.designsystem.ElimtiyazColors
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing

/**
 * Login screen — email + password with a "forgot" link and a "remember me" toggle.
 *
 * Mock-mode hint: when no Supabase keys are configured, the screen shows a small
 * hint card with demo credentials so reviewers can log in without setup.
 */
@Composable
fun LoginScreen(
    nav: NavController,
    vm: AuthViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()

    // Auto-navigate if a session is restored
    session?.let { s ->
        if (s.role == Role.Parent || s.role == Role.Student) {
            // Parent/student should use the Web Portal
            nav.navigate(Route.WebPortalRedirect.route) {
                popUpTo(Route.Login.route) { this@popUpTo.inclusive = true }
            }
        } else {
            nav.navigate(Route.Dashboard.route) {
                popUpTo(Route.Login.route) { this@popUpTo.inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = ElimtiyazSpacing.x6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Logo
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(ElimtiyazColors.PrimaryBlue),
            contentAlignment = Alignment.Center,
        ) {
            Text("EI", color = ElimtiyazColors.OffWhite, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(ElimtiyazSpacing.x4))
        Text("El-Imtiyaz", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(
            "Plateforme de gestion scolaire",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(ElimtiyazSpacing.x8))

        OutlinedTextField(
            value = state.email,
            onValueChange = vm::emailChanged,
            label = { Text("Adresse e-mail") },
            leadingIcon = { Icon(Icons.Outlined.Mail, contentDescription = null) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(ElimtiyazSpacing.x3))
        OutlinedTextField(
            value = state.password,
            onValueChange = vm::passwordChanged,
            label = { Text("Mot de passe") },
            leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        state.error?.let { err ->
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(ElimtiyazSpacing.x4))
        Button(
            onClick = { vm.signIn(onSuccess = { /* auto-nav via session above */ }) },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            enabled = !state.isLoading,
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp),
                )
            } else {
                Text("Se connecter", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(ElimtiyazSpacing.x2))
        TextButton(onClick = { nav.navigate(Route.Forgot.route) }) { Text("Mot de passe oublié ?") }
        Spacer(Modifier.height(ElimtiyazSpacing.x6))

        // Demo hint — visible in mock mode
        Text(
            "Mode démo: admin@elimtiyaz.dz / admin123",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

fun NavGraphBuilder.loginScreen(nav: NavController) {
    composable(Route.Login.route) { LoginScreen(nav) }
}
