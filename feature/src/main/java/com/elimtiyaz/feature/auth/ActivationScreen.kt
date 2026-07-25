package com.elimtiyaz.feature.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.elimtiyaz.app.navigation.Route
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    email: String,
    nav: NavController,
    vm: AuthViewModel = hiltViewModel(),
) {
    var otp by remember { mutableStateOf("") }
    var newPwd by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Activation du compte") },
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
            Text("Un code à 6 chiffres a été envoyé à $email.")
            OutlinedTextField(
                value = otp,
                onValueChange = { otp = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("Code OTP") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = newPwd,
                onValueChange = { newPwd = it },
                label = { Text("Nouveau mot de passe") },
                leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null) },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    vm.activateAccount(email, otp, newPwd) { ok, msg ->
                        result = if (ok) "Compte activé. Vous pouvez vous connecter." else msg
                        if (ok) nav.popBackStack(Route.Login.route, inclusive = false)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) { Text("Activer") }
            result?.let { Text(it) }
        }
    }
}

fun NavGraphBuilder.activationScreen(nav: NavController) {
    composable(
        route = Route.Activation.route,
        arguments = listOf(navArgument("email") { type = NavType.StringType }),
    ) { backStack ->
        val email = backStack.arguments?.getString("email").orEmpty()
        ActivationScreen(email, nav)
    }
}
