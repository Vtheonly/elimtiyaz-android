package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.ElButtonShape
import com.example.ui.theme.ElCardShape
import com.example.ui.theme.ElCardShapeSmall
import com.example.ui.theme.ElDialogShape
import com.example.ui.theme.ElFabShape
import com.example.ui.theme.ElFieldShape
import com.example.ui.theme.ElPillShape
import com.example.ui.theme.elDesignTokens

// ── ElScaffold ──────────────────────────────────────────────────────────────

/**
 * Custom scaffold with a gradient background that gives screens depth.
 * Replaces stock [androidx.compose.material3.Scaffold] for a more branded feel.
 */
@Composable
fun ElScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val tokens = elDesignTokens()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.heroBrush),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            topBar()
            Box(modifier = Modifier.weight(1f)) {
                content()
            }
            bottomBar()
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            floatingActionButton()
        }
    }
}

// ── ElFab ───────────────────────────────────────────────────────────────────

/**
 * Custom floating action button with gradient fill, shadow, and press animation.
 * Replaces stock [androidx.compose.material3.FloatingActionButton].
 */
@Composable
fun ElFab(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    gradient: Brush? = null,
) {
    val tokens = elDesignTokens()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(120),
        label = "fabScale",
    )
    val bgBrush = gradient ?: tokens.primaryBrush

    Box(
        modifier = modifier
            .size(56.dp)
            .shadow(8.dp, ElFabShape, ambientColor = tokens.shadowColor, spotColor = tokens.shadowColor)
            .clip(ElFabShape)
            .background(bgBrush)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
    }
}

// ── ElStatCard ──────────────────────────────────────────────────────────────

/**
 * KPI / statistics card with icon, value, label, and accent color.
 * Used in dashboards and financial screens. Replaces stock Card-based KPIs.
 */
@Composable
fun ElStatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val tokens = elDesignTokens()
    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else Modifier

    Box(
        modifier = modifier
            .width(220.dp)
            .clip(ElCardShape)
            .then(clickMod)
            .background(tokens.surfaceBrush, ElCardShape)
            .border(1.dp, tokens.cardBorder.copy(alpha = 0.4f), ElCardShape)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(ElPillShape)
                        .background(accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── ElListItem ──────────────────────────────────────────────────────────────

/**
 * Custom list item with leading icon/avatar, title, subtitle, and trailing slot.
 * Replaces stock Card-based list items for a more consistent design language.
 */
@Composable
fun ElListItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    accentColor: Color? = null,
) {
    val tokens = elDesignTokens()
    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else Modifier

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ElCardShapeSmall)
            .then(clickMod)
            .background(tokens.surfaceBrush, ElCardShapeSmall)
            .border(1.dp, tokens.cardBorder.copy(alpha = 0.35f), ElCardShapeSmall)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

// ── ElAlertBanner ───────────────────────────────────────────────────────────

enum class ElAlertSeverity { Info, Success, Warning, Danger }

/**
 * Alert banner with severity-based color, icon, and optional dismiss action.
 * Used for notifications, errors, and status messages.
 */
@Composable
fun ElAlertBanner(
    message: String,
    severity: ElAlertSeverity,
    modifier: Modifier = Modifier,
    title: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val (bgColor, contentColor, icon) = when (severity) {
        ElAlertSeverity.Info -> Triple(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), MaterialTheme.colorScheme.primary, null)
        ElAlertSeverity.Success -> Triple(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), androidx.compose.ui.graphics.Color(0xFF2D9B6B), null)
        ElAlertSeverity.Warning -> Triple(androidx.compose.ui.graphics.Color(0xFFE0922F).copy(alpha = 0.12f), androidx.compose.ui.graphics.Color(0xFFE0922F), null)
        ElAlertSeverity.Danger -> Triple(MaterialTheme.colorScheme.error.copy(alpha = 0.1f), MaterialTheme.colorScheme.error, null)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ElCardShapeSmall)
            .background(bgColor)
            .border(1.dp, contentColor.copy(alpha = 0.2f), ElCardShapeSmall)
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (title != null) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = contentColor,
                    )
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor,
                )
            }
            if (onDismiss != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onDismiss,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Fermer",
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

// ── ElDivider ───────────────────────────────────────────────────────────────

/**
 * Custom divider with subtle gradient and configurable thickness.
 */
@Composable
fun ElDivider(
    modifier: Modifier = Modifier,
    thickness: Int = 1,
) {
    val tokens = elDesignTokens()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(thickness.dp)
            .background(tokens.dividerColor),
    )
}

// ── ElDialog ────────────────────────────────────────────────────────────────

/**
 * Custom dialog with branded shape, gradient header, and content slot.
 * Replaces stock [androidx.compose.material3.AlertDialog].
 */
@Composable
fun ElDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit = {},
    dismissButton: @Composable () -> Unit = {},
) {
    val tokens = elDesignTokens()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(ElDialogShape)
                .background(MaterialTheme.colorScheme.surface, ElDialogShape)
                .border(1.dp, tokens.cardBorder.copy(alpha = 0.4f), ElDialogShape),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(tokens.primaryBrush)
                        .padding(20.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        ),
                        color = Color.White,
                    )
                }
                // Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                ) {
                    content()
                }
                // Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    dismissButton()
                    Spacer(Modifier.width(8.dp))
                    confirmButton()
                }
            }
        }
    }
}

// ── ElDropdown ──────────────────────────────────────────────────────────────

/**
 * Custom dropdown field with branded appearance. Replaces stock
 * [androidx.compose.material3.ExposedDropdownMenuBox].
 */
@Composable
fun ElDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val tokens = elDesignTokens()
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
            )
        }
        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ElFieldShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, tokens.cardBorder, ElFieldShape)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = { expanded = !expanded },
                    )
                    .padding(horizontal = 14.dp, vertical = 14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        Icon(
                            imageVector = leadingIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        text = selectedValue,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ArrowDropDown else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(150)) + slideInVertically(tween(150)),
                exit = fadeOut(tween(100)) + slideOutVertically(tween(100)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(ElCardShapeSmall)
                        .background(MaterialTheme.colorScheme.surface, ElCardShapeSmall)
                        .border(1.dp, tokens.cardBorder.copy(alpha = 0.5f), ElCardShapeSmall)
                        .padding(vertical = 4.dp),
                ) {
                    options.forEach { option ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        onSelected(option)
                                        expanded = false
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (option == selectedValue) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (option == selectedValue) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── ElScrollableTabRow ──────────────────────────────────────────────────────

/**
 * Custom scrollable tab row for sub-screen navigation with many tabs.
 * Replaces stock [androidx.compose.material3.ScrollableTabRow].
 */
@Composable
fun ElScrollableTabRow(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = elDesignTokens()
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tabs) { tab ->
            val index = tabs.indexOf(tab)
            val isSelected = selectedTabIndex == index
            val bgAlpha = if (isSelected) 1f else 0.08f
            val textAlpha = if (isSelected) 1f else 0.7f

            Box(
                modifier = Modifier
                    .clip(ElPillShape)
                    .background(
                        if (isSelected) tokens.primaryBrush else Brush.horizontalGradient(
                            listOf(MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha), MaterialTheme.colorScheme.primary.copy(alpha = bgAlpha))
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onTabSelected(index) },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = tab,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                    ),
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = textAlpha),
                    maxLines = 1,
                )
            }
        }
    }
}

// ── ElGradientStatCard ──────────────────────────────────────────────────────

/**
 * Gradient-filled stat card for hero metrics. Uses the primary gradient
 * as background with white text — for prominent dashboard figures.
 */
@Composable
fun ElGradientStatCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    gradient: Brush? = null,
) {
    val tokens = elDesignTokens()
    val bgBrush = gradient ?: tokens.primaryBrush

    Box(
        modifier = modifier
            .clip(ElCardShape)
            .background(bgBrush, ElCardShape)
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                ),
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

// ── ElIconButton ────────────────────────────────────────────────────────────

/**
 * Custom icon button with circular background and press animation.
 * Replaces stock [androidx.compose.material3.IconButton].
 */
@Composable
fun ElIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    size: Int = 40,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(100),
        label = "iconBtnScale",
    )

    Box(
        modifier = modifier
            .size(size.dp)
            .clip(ElButtonShape)
            .background(background)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}