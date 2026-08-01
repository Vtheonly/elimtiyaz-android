package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ElAvatarShape
import com.example.ui.theme.ElButtonShape
import com.example.ui.theme.ElCardShape
import com.example.ui.theme.ElCardShapeSmall
import com.example.ui.theme.ElFieldShape
import com.example.ui.theme.ElPillShape
import com.example.ui.theme.elDesignTokens

// ── ElCard ─────────────────────────────────────────────────────────────────

/**
 * Signature card component with gradient surface, subtle border, and
 * configurable elevation. Replaces stock Material 3 [Card].
 *
 * @param gradient When true, applies the theme surface gradient for depth.
 * @param accent An optional left-edge accent bar color (null = no bar).
 * @param compact When true, uses the smaller [ElCardShapeSmall] shape.
 */
@Composable
fun ElCard(
    modifier: Modifier = Modifier,
    gradient: Boolean = true,
    accent: Color? = null,
    compact: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val tokens = elDesignTokens()
    val shape = if (compact) ElCardShapeSmall else ElCardShape
    val bgBrush = if (gradient) tokens.surfaceBrush else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface))

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .then(clickMod)
            .background(bgBrush, shape)
            .border(1.dp, tokens.cardBorder.copy(alpha = 0.5f), shape)
            .then(if (accent != null) Modifier.padding(start = 3.dp) else Modifier),
    ) {
        if (accent != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(end = 3.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surface),
            )
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .matchParentSize()
                    .background(accent),
            )
        }
        content()
    }
}

// ── ElButton ───────────────────────────────────────────────────────────────

enum class ElButtonStyle { Primary, Secondary, Danger, Ghost }

/**
 * Custom button with gradient fill (primary), tonal surface (secondary),
 * or transparent (ghost). Includes pressed-state animation.
 */
@Composable
fun ElButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ElButtonStyle = ElButtonStyle.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    fullWidth: Boolean = false,
) {
    val tokens = elDesignTokens()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.97f else 1f,
        animationSpec = tween(100),
        label = "pressScale",
    )

    val (containerBrush, contentColor) = when (style) {
        ElButtonStyle.Primary -> tokens.primaryBrush to Color.White
        ElButtonStyle.Secondary -> Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surfaceVariant)) to MaterialTheme.colorScheme.onSurface
        ElButtonStyle.Danger -> tokens.dangerBrush to Color.White
        ElButtonStyle.Ghost -> Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)) to MaterialTheme.colorScheme.primary
    }

    val alpha = if (enabled) 1f else 0.4f

    Box(
        modifier = modifier
            .then(if (fullWidth) Modifier.fillMaxWidth() else Modifier)
            .height(50.dp)
            .clip(ElButtonShape)
            .background(containerBrush, ElButtonShape)
            .then(if (style == ElButtonStyle.Ghost) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), ElButtonShape) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = contentColor,
                )
            } else {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = alpha),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = text,
                    color = contentColor.copy(alpha = alpha),
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    ),
                )
            }
        }
    }
}

// ── ElTextField ────────────────────────────────────────────────────────────

/**
 * Custom text field with animated focus border, custom shape, and
 * optional leading/trailing icons. Replaces stock [OutlinedTextField].
 */
@Composable
fun ElTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingIcon: ImageVector? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    isError: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
) {
    val tokens = elDesignTokens()
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val borderColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error
            isFocused -> MaterialTheme.colorScheme.primary
            else -> tokens.cardBorder
        },
        animationSpec = tween(200),
        label = "fieldBorder",
    )

    val labelColor by animateColorAsState(
        targetValue = when {
            isError -> MaterialTheme.colorScheme.error
            isFocused -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(200),
        label = "fieldLabel",
    )

    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                ),
                color = labelColor,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ElFieldShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, borderColor, ElFieldShape)
                .then(if (isFocused) Modifier.border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), ElFieldShape) else Modifier),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = if (singleLine) 14.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).padding(end = 0.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                androidx.compose.foundation.text.BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f).then(if (singleLine) Modifier else Modifier),
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else minLines,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    keyboardOptions = keyboardOptions,
                    visualTransformation = visualTransformation,
                    enabled = enabled && !readOnly,
                    cursorBrush = Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary)),
                    decorationBox = { innerTextField ->
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        }
                        innerTextField()
                    },
                )
                if (trailingIcon != null) {
                    Spacer(Modifier.width(8.dp))
                    trailingIcon()
                }
            }
        }
    }
}

// ── ElAvatar ───────────────────────────────────────────────────────────────

/**
 * Circular avatar with initials, gradient background, and configurable size.
 */
@Composable
fun ElAvatar(
    initials: String,
    modifier: Modifier = Modifier,
    size: Int = 44,
    gradient: Brush? = null,
) {
    val tokens = elDesignTokens()
    val bgBrush = gradient ?: tokens.primaryBrush
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(ElAvatarShape)
            .background(bgBrush),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.take(2).uppercase(),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = (size * 0.35f).sp,
            ),
        )
    }
}

// ── ElChip / ElTag ─────────────────────────────────────────────────────────

/**
 * Pill-shaped tag/chip with configurable accent color. Used for status
 * badges, categories, and filter chips.
 */
@Composable
fun ElTag(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bgAlpha = if (selected) 1f else 0.12f
    val textAlpha = if (selected) 1f else 0.85f

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else Modifier

    Box(
        modifier = modifier
            .clip(ElPillShape)
            .background(color.copy(alpha = bgAlpha))
            .then(clickMod)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            ),
            color = if (selected) Color.White else color.copy(alpha = textAlpha),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ── ElProgressBar ──────────────────────────────────────────────────────────

/**
 * Custom progress bar with gradient fill and rounded track.
 */
@Composable
fun ElProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    gradient: Brush? = null,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    val tokens = elDesignTokens()
    val fillBrush = gradient ?: tokens.primaryBrush

    Box(
        modifier = modifier
            .height(6.dp)
            .clip(ElPillShape)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(6.dp)
                .clip(ElPillShape)
                .background(fillBrush),
        )
    }
}

// ── ElBadge ────────────────────────────────────────────────────────────────

/**
 * Small circular badge for notification counts.
 */
@Composable
fun ElBadge(
    count: Int,
    modifier: Modifier = Modifier,
) {
    if (count <= 0) return
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 9) "9+" else count.toString(),
            color = Color.White,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
            ),
        )
    }
}

// ── ElTopBar ───────────────────────────────────────────────────────────────

/**
 * Custom top app bar with gradient title, optional back button, and
 * action slot. Replaces stock [TopAppBar].
 */
@Composable
fun ElTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(ElButtonShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Retour",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
    }
}

// ── ElEmptyState ───────────────────────────────────────────────────────────

/**
 * Empty state placeholder with icon, message, and optional action.
 */
@Composable
fun ElEmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (actionText != null && onAction != null) {
            ElButton(
                text = actionText,
                onClick = onAction,
                style = ElButtonStyle.Secondary,
            )
        }
    }
}

// ── ElSectionHeader ────────────────────────────────────────────────────────

/**
 * Section header with title and optional "see all" action.
 */
@Composable
fun ElSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (actionText != null && onAction != null) {
            Box(
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onAction,
                ),
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// ── ElGradientHeader ───────────────────────────────────────────────────────

/**
 * Full-width gradient header banner used at the top of screens.
 * Contains a title, subtitle, and optional icon.
 */
@Composable
fun ElGradientHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    val tokens = elDesignTokens()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(tokens.primaryBrush)
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    ),
                    color = Color.White,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
        }
    }
}

// ── ElInfoRow ──────────────────────────────────────────────────────────────

/**
 * Label-value row used in detail screens. Label on the left (muted),
 * value on the right (primary text).
 */
@Composable
fun ElInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = valueColor,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
        )
    }
}