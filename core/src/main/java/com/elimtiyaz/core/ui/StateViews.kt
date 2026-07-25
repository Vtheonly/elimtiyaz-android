package com.elimtiyaz.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing

/**
 * Full-screen centered spinner — used on initial loads.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(48.dp),
        )
        if (message != null) {
            Spacer(Modifier.height(ElimtiyazSpacing.x3))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Full-screen error placeholder with an optional retry button.
 */
@Composable
fun ErrorState(
    error: AppError,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val icon: ImageVector = when (error) {
        is AppError.Network -> Icons.Outlined.CloudOff
        else -> Icons.Outlined.ErrorOutline
    }
    Column(
        modifier = modifier.fillMaxSize().padding(ElimtiyazSpacing.x6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(ElimtiyazSpacing.x4))
        Text(
            text = error.userMessage,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            Button(onClick = onRetry) { Text("Réessayer") }
        }
    }
}

/**
 * Empty-state placeholder — used by lists with zero results.
 */
@Composable
fun EmptyState(
    title: String,
    description: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(ElimtiyazSpacing.x6),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(ElimtiyazSpacing.x4))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (description != null) {
            Spacer(Modifier.height(ElimtiyazSpacing.x2))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(ElimtiyazSpacing.x4))
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/**
 * Inline banner shown at the top of any screen when the device is offline
 * or pending writes to the sync queue.
 */
@Composable
fun OfflineBanner(
    pendingCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
            .padding(horizontal = ElimtiyazSpacing.x4, vertical = ElimtiyazSpacing.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(ElimtiyazSpacing.x2))
            Text(
                text = if (pendingCount > 0) "Hors ligne — $pendingCount en attente" else "Hors ligne",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
fun <T> AsyncContent(
    isLoading: Boolean,
    error: AppError?,
    items: List<T>,
    onRetry: (() -> Unit)? = null,
    emptyTitle: String = "Aucun élément",
    emptyDescription: String? = null,
    emptyIcon: ImageVector = Icons.Outlined.Inbox,
    content: @Composable (List<T>) -> Unit,
) {
    when {
        isLoading && items.isEmpty() -> LoadingState()
        error != null && items.isEmpty() -> ErrorState(error, onRetry)
        items.isEmpty() -> EmptyState(emptyTitle, emptyDescription, emptyIcon)
        else -> content(items)
    }
}

@Suppress("unused") val DefaultContentPadding = PaddingValues(ElimtiyazSpacing.x4)
@Suppress("unused") private val unused: Color = Color.Transparent  // keeps imports tidy
