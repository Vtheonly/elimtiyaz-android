package com.elimtiyaz.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordScreen(
    nav: NavController,
    vm: AuthViewModel = hiltViewModel(),
) {
    var email by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mot de passe oublié") },
                navigationIcon = {
                    IconButton(onClick = { nav.popBackStack() }) {
                        Icon(Icons.Outlined.ArrowBack, contentDescription = null)
                    }
                },
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier.fillMaxSize().padding(inner).padding(ElimtiyazSpacing.x6),
            verticalArrangement = Arrangement.spacedBy(ElimtiyazSpacing.x3),
        ) {
            Text("Saisissez votre e-mail. Vous recevrez un lien de réinitialisation.")
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Adresse e-mail") },
                leadingIcon = { Icon(Icons.Outlined.Mail, contentDescription = null) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.requestPasswordReset(email) { ok, msg ->
                        result = if (ok) "Si ce compte existe, un e-mail a été envoyé." else msg
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Envoyer") }
            result?.let { Text(it) }
        }
    }
}

fun NavGraphBuilder.forgotPasswordScreen(nav: NavController) {
    composable(Route.Forgot.route) { ForgotPasswordScreen(nav) }
}
