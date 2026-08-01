package com.example.ui.designsystem.components.display

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.noRippleClickable
import com.example.ui.designsystem.theme.ElCardShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Dismissible alert banner — full-width card with a severity-tinted
 * background, a leading severity icon, an optional action button, and an
 * optional dismiss (X) button.
 *
 * Slides in from the top on first composition so the banner reads as a
 * freshly-arrived notification rather than a static block.
 *
 * @param title        Banner title (bold).
 * @param message      Optional body message below the title.
 * @param severity     Severity selector — drives background tint and icon.
 * @param onDismiss    When non-null, renders the dismiss (X) button.
 * @param modifier     Outer modifier.
 * @param actionLabel  Optional action button label.
 * @param onAction     Optional action tap callback.
 */
@Composable
fun ElAlertBanner(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    severity: ElAlertSeverity = ElAlertSeverity.INFO,
    onDismiss: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = ElTheme.colors
    val (bg, fg, icon) = severity.resolve(c)

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically { -it / 2 } + fadeIn(tween(220)),
        exit = slideOutVertically { -it } + fadeOut(tween(180)),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = modifier
                .fillMaxWidth()
                .clip(ElCardShape)
                .background(bg)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = fg,
                    style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                )
                if (message != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = message,
                        color = fg.copy(alpha = 0.85f),
                        style = ElTheme.typography.bodySmall,
                    )
                }
                if (actionLabel != null && onAction != null) {
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = onAction,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 0.dp,
                            vertical = 0.dp,
                        ),
                    ) {
                        Text(
                            actionLabel,
                            color = fg,
                            style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        )
                    }
                }
            }
            if (onDismiss != null) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = fg.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(20.dp)
                        .noRippleClickable(onClick = onDismiss),
                )
            }
        }
    }
}

/** Resolves a [severity] to its (background, foreground, icon) tuple. */
private fun ElAlertSeverity.resolve(
    c: com.example.ui.designsystem.theme.ElColors,
): Triple<Color, Color, ImageVector> = when (this) {
    ElAlertSeverity.INFO    -> Triple(c.infoContainer,    c.info,    Icons.Default.Info)
    ElAlertSeverity.SUCCESS -> Triple(c.successContainer, c.success, Icons.Default.CheckCircle)
    ElAlertSeverity.WARNING -> Triple(c.warningContainer, c.warning, Icons.Default.Warning)
    ElAlertSeverity.DANGER  -> Triple(c.dangerContainer,  c.danger,  Icons.Default.Error)
}
