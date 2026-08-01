package com.example.ui.designsystem.components.display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.foundation.pressClickable
import com.example.ui.designsystem.theme.ElTheme

/**
 * A label-value row used in detail screens. The label is rendered on the
 * left in the muted text style; the value is rendered on the right in the
 * tinted color (defaults to [ElColors.primary]).
 *
 * When [onClick] is supplied, the row gains a press-scale clickable
 * treatment and is suitable as a navigation affordance.
 *
 * @param label     Left label text (muted).
 * @param value     Right value text (tinted).
 * @param modifier  Outer modifier.
 * @param icon      Optional icon rendered before the label.
 * @param valueTint Color applied to the value text. Defaults to primary.
 * @param onClick   When non-null, the row becomes clickable.
 */
@Composable
fun ElInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: (@Composable () -> Unit)? = null,
    valueTint: Color = ElTheme.colors.primary,
    onClick: (() -> Unit)? = null,
) {
    val c = ElTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.pressClickable(
                        pressedScale = 0.99f,
                        onClick = onClick,
                    )
                } else Modifier
            )
            .padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                color = c.textSecondary,
                style = ElTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            color = valueTint,
            style = ElTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
