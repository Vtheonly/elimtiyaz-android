package com.example.ui.designsystem.components.card

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.theme.ElTheme

/**
 * Universal Expandable Tile for progressive disclosure.
 *
 * Designed to ensure titles are never truncated or cut off with ellipses:
 * the title area occupies the primary space on the left, while the primary figure
 * and status badge are cleanly aligned on the right.
 */
@Composable
fun ElExpandableCard(
    title: String,
    modifier: Modifier = Modifier,
    primaryFigure: String? = null,
    primaryFigureColor: Color = ElTheme.colors.textPrimary,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = ElTheme.colors.primary,
    headerBadge: (@Composable () -> Unit)? = null,
    headerTrailing: (@Composable () -> Unit)? = null,
    initiallyExpanded: Boolean = false,
    onExpandChanged: ((Boolean) -> Unit)? = null,
    expandedContent: @Composable ColumnScope.() -> Unit,
) {
    val c = ElTheme.colors
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(250),
        label = "chevron_rotate",
    )

    ElCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                val newState = !expanded
                expanded = newState
                onExpandChanged?.invoke(newState)
            },
        size = ElCardSize.STANDARD,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ElTheme.spacing.md),
        ) {
            // Header Row (Always Visible)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left Column: Icon + Full Title + Info tip + Subtitle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = ElTheme.spacing.sm),
                ) {
                    if (icon != null) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(ElTheme.spacing.sm))
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = title,
                                style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = c.textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            headerTrailing?.invoke()
                        }
                        if (subtitle != null && !expanded) {
                            Text(
                                text = subtitle,
                                style = ElTheme.typography.labelSmall,
                                color = c.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Right Column: Metric on top, Badge underneath + Chevron
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (primaryFigure != null) {
                            Text(
                                text = primaryFigure,
                                style = ElTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                color = primaryFigureColor,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "Réduire" else "Détails",
                            tint = c.textMuted,
                            modifier = Modifier
                                .size(20.dp)
                                .rotate(chevronRotation),
                        )
                    }
                    if (headerBadge != null) {
                        Spacer(Modifier.height(2.dp))
                        headerBadge()
                    }
                }
            }

            // Expanded Body (Progressive Disclosure)
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(tween(250)) + fadeIn(tween(200)),
                exit = shrinkVertically(tween(200)) + fadeOut(tween(150)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ElTheme.spacing.md),
                ) {
                    HorizontalDivider(
                        color = c.outlineVariant.copy(alpha = 0.5f),
                        thickness = 1.dp,
                    )
                    Spacer(Modifier.height(ElTheme.spacing.md))
                    expandedContent()
                }
            }
        }
    }
}