package com.example.ui.designsystem.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import com.example.ui.designsystem.foundation.elShadow
import com.example.ui.designsystem.foundation.noRippleClickable
import com.example.ui.designsystem.foundation.pressClickable
import com.example.ui.designsystem.theme.ElElevationSpec
import com.example.ui.designsystem.theme.ElTheme

/**
 * The unified El-Imtiyaz card. Every surface container in the app should use
 * this or be composed from it.
 *
 * All variants share:
 *  - Shape: signature 24dp card (or 16dp for COMPACT)
 *  - Optional press-scale for interactive cards
 *  - Optional tinted shadow
 *  - Theme-consistent structural border
 */
@Composable
fun ElCard(
    modifier: Modifier = Modifier,
    variant: ElCardVariant = ElCardVariant.ELEVATED,
    size: ElCardSize = ElCardSize.STANDARD,
    shape: Shape = defaultCardShape(size),
    elevation: ElElevationSpec? = ElTheme.elevation.medium,
    onClick: (() -> Unit)? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    background: Color? = null,
    gradient: Brush? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = resolveCardStyle(variant, background, gradient)
    val resolvedBorder = border ?: style.border
    val showShadow = elevation != null && elevation.alpha > 0f &&
        variant != ElCardVariant.OUTLINED

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .then(if (showShadow) Modifier.elShadow(elevation!!, shape) else Modifier)
            .clip(shape)
            .background(style.background, shape)
            .then(if (resolvedBorder != null) Modifier.border(resolvedBorder, shape) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.pressClickable(
                        pressedScale = 0.98f,
                        interactionSource = interactionSource,
                        role = Role.Tab,
                        onClick = onClick,
                    )
                } else Modifier
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding(size)),
            content = content,
        )
    }
}
