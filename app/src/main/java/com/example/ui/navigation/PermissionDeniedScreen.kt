package com.example.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme

@Composable
internal fun PermissionDeniedScreen(onBack: () -> Unit) {
    ElScaffold(
        topBar = { ElTopBar(title = "Accès refusé", onBack = onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(ElTheme.spacing.xl),
            verticalArrangement = Arrangement.Center,
        ) {
            ElEmptyState(
                icon = Icons.Default.Lock,
                title = "Permission insuffisante",
                subtitle = "Votre rôle ne vous permet pas d'accéder à cet écran. Contactez un administrateur si vous pensez qu'il s'agit d'une erreur.",
                actionLabel = "Retour",
                onAction = onBack,
            )
        }
    }
}
