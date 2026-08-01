@file:OptIn(ExperimentalLayoutApi::class)

package com.example.ui.designsystem.gallery.tabs

import androidx.compose.foundation.layout.ExperimentalLayoutApi

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.card.ElCardVariant
import com.example.ui.designsystem.gallery.GallerySection
import com.example.ui.designsystem.theme.ElColors
import com.example.ui.designsystem.theme.ElTheme

/** Foundations tab — color palette, surfaces, typography, spacing, elevation. */
fun LazyListScope.foundationsTab(c: ElColors) {
    item { BrandColorsSection(c) }
    item { SurfacesSection(c) }
    item { TypographySection(c) }
    item { SpacingElevationSection(c) }
}

@Composable
private fun BrandColorsSection(c: ElColors) {
    GallerySection(
        title = "Brand Colors",
        description = "The signature palette — bold, saturated, and confident.",
    ) {
        val swatches = listOf(
            "Primary" to c.primary,
            "Accent" to c.primaryAccent,
            "Tertiary" to c.tertiary,
            "Success" to c.success,
            "Warning" to c.warning,
            "Danger" to c.danger,
            "Info" to c.info,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            swatches.forEach { (name, color) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(color),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(name, color = c.textSecondary, style = ElTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun SurfacesSection(c: ElColors) {
    GallerySection(
        title = "Surfaces",
        description = "Background → surface → variant → elevated.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "BG" to c.background,
                "Surface" to c.surface,
                "Variant" to c.surfaceVariant,
                "Elevated" to c.surfaceElevated,
            ).forEach { (n, col) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(col))
                    Spacer(Modifier.height(4.dp))
                    Text(n, color = c.textSecondary, style = ElTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun TypographySection(c: ElColors) {
    GallerySection(
        title = "Typography",
        description = "Heavy display + crisp body. The contrast is the identity.",
    ) {
        Text("Display Small", color = c.textPrimary, style = ElTheme.typography.displaySmall)
        Text("Headline Medium", color = c.textPrimary, style = ElTheme.typography.headlineMedium)
        Text("Title Large", color = c.textPrimary, style = ElTheme.typography.titleLarge)
        Text("Body Large — the quick brown fox jumps over the lazy dog.", color = c.textPrimary, style = ElTheme.typography.bodyLarge)
        Text("Label Medium", color = c.textSecondary, style = ElTheme.typography.labelMedium)
        Text("OVERLINE", color = c.textMuted, style = ElTheme.textStyles.overline)
    }
}

@Composable
private fun SpacingElevationSection(c: ElColors) {
    GallerySection(
        title = "Spacing & Elevation",
        description = "4dp grid · tinted shadows for depth.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ElCard(variant = ElCardVariant.ELEVATED, size = ElCardSize.COMPACT) {
                Text("Low", color = c.textPrimary, style = ElTheme.typography.labelMedium)
            }
            ElCard(variant = ElCardVariant.ELEVATED, size = ElCardSize.COMPACT) {
                Text("Medium", color = c.textPrimary, style = ElTheme.typography.labelMedium)
            }
            ElCard(variant = ElCardVariant.ELEVATED, size = ElCardSize.COMPACT) {
                Text("High", color = c.textPrimary, style = ElTheme.typography.labelMedium)
            }
        }
    }
}
