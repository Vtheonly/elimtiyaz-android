package com.example.ui.designsystem.components.card

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.designsystem.components.display.ElGradient
import com.example.ui.designsystem.foundation.elShadow
import com.example.ui.designsystem.foundation.pressClickable
import com.example.ui.designsystem.theme.ElCardShape
import com.example.ui.designsystem.theme.ElTheme

/**
 * Gradient stat card — used by dashboards to surface KPIs with brand-tinted
 * backgrounds. White text on top of a two-color linear gradient.
 *
 * Each [ElGradient] enum maps to a curated color pair (resolved by the
 * private `ElGradient.colors()` extension).
 * The card supports an optional leading icon and a subtitle (e.g. "+5 % vs
 * last month"). When [onClick] is supplied, the card gains a press-scale
 * treatment and a tinted shadow.
 *
 * @param title     Card label (small, white on gradient).
 * @param value     The hero KPI value (large numeric, white).
 * @param gradient  Gradient family — picks the color pair.
 * @param icon      Optional leading icon.
 * @param subtitle  Optional supporting text under the value.
 * @param modifier  Outer modifier.
 * @param onClick   When non-null, the card becomes clickable.
 */
@Composable
fun ElGradientStatCard(
    title: String,
    value: String,
    gradient: ElGradient,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val (start, end) = gradient.colors()
    val brush = Brush.linearGradient(listOf(start, end))
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(ElCardShape)
            .background(brush)
            .elShadow(ElTheme.elevation.medium, ElCardShape)
            .then(
                if (onClick != null) {
                    Modifier.pressClickable(
                        pressedScale = 0.97f,
                        interactionSource = interaction,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .padding(20.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = title,
                    color = Color.White.copy(alpha = 0.92f),
                    style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.size(10.dp))
            Text(
                text = value,
                color = Color.White,
                style = ElTheme.textStyles.numeric.copy(
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(Modifier.size(4.dp))
                Text(
                    text = subtitle,
                    color = Color.White.copy(alpha = 0.85f),
                    style = ElTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Resolves the gradient enum to its (start, end) [Color] pair. */
@Composable
private fun ElGradient.colors(): Pair<Color, Color> {
    val c = ElTheme.colors
    return when (this) {
        ElGradient.BRAND      -> c.primary        to c.tertiary
        ElGradient.REVENUE    -> c.success        to c.successGradient[1]
        ElGradient.DEBT       -> c.danger         to c.dangerGradient[1]
        ElGradient.ATTENDANCE -> c.info           to c.primary
        ElGradient.SUCCESS    -> c.successGradient[0] to c.successGradient[1]
        ElGradient.WARNING    -> c.warningGradient[0] to c.warningGradient[1]
        ElGradient.DANGER     -> c.dangerGradient[0]  to c.dangerGradient[1]
    }
}
