package com.example.ui.designsystem.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.ui.designsystem.foundation.elShadow
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.designsystem.theme.ElTooltipShape

/**
 * ElInfoTip — T-458 (128th session): the Android mirror of the desktop's
 * T-447 `InfoTip` (src/features/dashboard/components/analytics/info-tip.tsx)
 * — the ⓘ affordance that explains a Statistics element from the
 * centralized [StatsTips] glossary (presentation-ONLY: it renders the
 * glossary's text; it never computes, never derives — §15.53a).
 *
 * The contract (the desktop's, verbatim semantics):
 *  - `tip` is a DOTTED glossary key ("waveVelocity.collectedPct");
 *  - the HONEST-EMPTY rule: an unknown key renders NOTHING (never a
 *    fabricated text — the desktop's `if (!title) return null`);
 *  - the content: the bold title, then the three meta-labelled fields
 *    (Mesure / Calcul / Statut — the status line only when present);
 *  - the icon is keyboard/talkback-accessible (the contentDescription =
 *    the entry's title).
 *
 * Android presentation: a TAP toggles the popup (hover does not exist on
 * touch; the desktop uses a radix hover tooltip with 200ms delay). The
 * popup is dismiss-on-outside-tap.
 */
@Composable
fun ElInfoTip(
    tip: String,
    modifier: Modifier = Modifier,
    size: Int = 14,
    tint: Color = Color.Unspecified,
) {
    val entry = remember(tip) { StatsTips.tip(tip) } ?: return // honest-empty
    val c = ElTheme.colors
    var showing by remember(tip) { mutableStateOf(false) }

    Box(modifier = modifier) {
        val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = entry.title,
            modifier = Modifier
                .size(size.dp)
                .testTag("stat-tip-${tip.replace('.', '-')}")
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(if (showing) c.primary.copy(alpha = 0.12f) else Color.Transparent)
                .padding(1.dp)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { showing = !showing },
                ),
            tint = if (tint == Color.Unspecified) c.textSecondary else tint,
        )

        if (showing) {
            Popup(
                alignment = Alignment.TopStart,
                offset = androidx.compose.ui.unit.IntOffset(0, (size + 6)),
                properties = PopupProperties(focusable = false, dismissOnClickOutside = true),
                onDismissRequest = { showing = false },
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 300.dp)
                        .clip(ElTooltipShape)
                        .background(c.inverseSurface)
                        .elShadow(ElTheme.elevation.low, ElTooltipShape)
                        .padding(horizontal = ElTheme.spacing.md, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = entry.title,
                        color = c.inverseOnSurface,
                        style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    )
                    TipField(StatsTips.metaMeasures, entry.measures)
                    TipField(StatsTips.metaCalc, entry.calc)
                    entry.status?.let { TipField(StatsTips.metaStatus, it) }
                }
            }
        }
    }
}

@Composable
private fun TipField(label: String, value: String) {
    val c = ElTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
            text = label,
            color = c.inverseOnSurface.copy(alpha = 0.7f),
            style = ElTheme.typography.labelSmall,
        )
        Text(
            text = value,
            color = c.inverseOnSurface,
            style = ElTheme.typography.labelSmall,
        )
    }
}
