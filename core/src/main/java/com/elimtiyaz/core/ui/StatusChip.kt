package com.elimtiyaz.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elimtiyaz.core.designsystem.ElimtiyazSpacing
import com.elimtiyaz.core.designsystem.LocalElimtiyazStatusColors

/**
 * A small rounded pill used to display a status (Payment, Attendance, Expense…).
 * Color is derived from [StatusTone].
 */
@Composable
fun StatusChip(
    label: String,
    tone: StatusTone,
    modifier: Modifier = Modifier,
) {
    val status = LocalElImtiyazStatusColors.current
    val (bg, fg) = when (tone) {
        StatusTone.Success -> status.success to Color.Black
        StatusTone.Warning -> status.warning to Color.Black
        StatusTone.Danger  -> status.danger  to Color.White
        StatusTone.Info    -> status.info    to Color.Black
        StatusTone.Neutral -> status.neutral to Color.White
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier
                .width(6.dp)
                .background(bg, RoundedCornerShape(999.dp))
                .padding(vertical = 6.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = fg.copy(alpha = 0.85f).compositeOver(MaterialTheme.colorScheme.surface),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

enum class StatusTone { Success, Warning, Danger, Info, Neutral }

/** helper to blend two colors when an alpha < 1 is applied */
private fun Color.compositeOver(background: Color): Color {
    val a = alpha + background.alpha * (1f - alpha)
    if (a == 0f) return Color.Transparent
    val r = (red * alpha + background.red * background.alpha * (1f - alpha)) / a
    val g = (green * alpha + background.green * background.alpha * (1f - alpha)) / a
    val b = (blue * alpha + background.blue * background.alpha * (1f - alpha)) / a
    return Color(r, g, b, a)
}
