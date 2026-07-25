package com.elimtiyaz.core.designsystem

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Spacing scale — powers-of-2 base, with a 1.5× step.
 * Use [ElImtiyazSpacing] for all paddings/margins so the entire app stays rhythmic.
 */
object ElImtiyazSpacing {
    val x0_5 = 2.dp
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x8 = 32.dp
    val x10 = 40.dp
    val x12 = 48.dp
    val x16 = 64.dp

    val pageHorizontal = x4
    val pageVertical = x4
    val cardPadding = PaddingValues(x4)
    val listItemSpacing = x2
}

object ElImtiyazRadius {
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val pill = 999.dp
}

object ElImtiyazElevation {
    val none = 0.dp
    val low = 1.dp
    val medium = 2.dp
    val high = 4.dp
    val floating = 8.dp
}

val ElImtiyazShapes = Shapes(
    extraSmall = RoundedCornerShape(ElImtiyazRadius.sm),
    small = RoundedCornerShape(ElImtiyazRadius.sm),
    medium = RoundedCornerShape(ElImtiyazRadius.md),
    large = RoundedCornerShape(ElImtiyazRadius.lg),
    extraLarge = RoundedCornerShape(ElImtiyazRadius.xl),
)
