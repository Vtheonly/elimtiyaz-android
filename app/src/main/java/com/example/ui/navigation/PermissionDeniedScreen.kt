package com.example.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ui.components.ElEmptyState
import com.example.ui.components.ElScaffold
import com.example.ui.components.ElTopBar

@Composable
internal fun PermissionDeniedScreen(onBack: () -> Unit) {
    ElScaffold(
        topBar = { ElTopBar(title = "Accès refusé", onBack = onBack) },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            ElEmptyState(
                icon = Icons.Default.Lock,
                title = "Permission insuffisante",
                message = "Votre rôle ne vous permet pas d'accéder à cet écran. Contactez un administrateur si vous pensez qu'il s'agit d'une erreur.",
                actionText = "Retour",
                onAction = onBack,
            )
        }
    }
}
