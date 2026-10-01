package com.example.ui.features.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Announcement
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElCardSize
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme

/**
 * T-460 pass G-a (android issue #3, finding F-14): the ONE shared
 * notification-row language for the app's two alert surfaces — the Dashboard
 * hub's "Rappels & Notifications" section and the full Alerts inbox. Both
 * previously rendered the same entity with two different visual languages
 * (the hub: DS cards with icon + tone tag + call action; the inbox: raw M3
 * cards with an 8dp priority dot and raw type codes).
 *
 * The row takes DISPLAY-READY primitives so each surface maps its own model
 * (the hub's derived triage alerts vs the inbox's [com.example.domain.model.AppNotification])
 * onto the same visual language without coupling the models.
 */
@Composable
internal fun NotificationRow(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Notifications,
    iconTint: Color = ElTheme.colors.primary,
    metaLabel: String? = null,
    metaTone: ElTagTone = ElTagTone.NEUTRAL,
    timeLabel: String? = null,
    unread: Boolean = false,
    maxBodyLines: Int = 3,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val c = ElTheme.colors
    ElCard(
        modifier = modifier.fillMaxWidth(),
        size = ElCardSize.STANDARD,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = ElTheme.typography.titleSmall.copy(
                        fontWeight = if (unread) FontWeight.Bold else FontWeight.SemiBold,
                    ),
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                metaLabel?.let { label ->
                    Spacer(Modifier.width(8.dp))
                    ElTag(
                        text = label,
                        tone = metaTone,
                        size = ElTagSize.SM,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = body,
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = maxBodyLines,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (unread) {
                    ElTag(
                        text = "Non lu",
                        tone = ElTagTone.DANGER,
                        size = ElTagSize.SM,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Spacer(Modifier.weight(1f))
                timeLabel?.let { time ->
                    Text(
                        text = time,
                        style = ElTheme.typography.labelSmall,
                        color = c.textSecondary,
                    )
                }
                trailing?.let {
                    Spacer(Modifier.width(8.dp))
                    it()
                }
            }
        }
    }
}

/**
 * The canonical type → (French label, icon, tone) mapping for the
 * notification family. Used by the Alerts inbox so the raw wire codes
 * (`payment_overdue`…) never render as-is again — the same mapping
 * discipline as the hub section (which derives its own alert models).
 */
internal fun notificationTypeMeta(type: String): Triple<String, ImageVector, ElTagTone> = when (type) {
    "payment_overdue", "overdue_debt" -> Triple("Paiements", Icons.Default.Payment, ElTagTone.WARNING)
    "expense_pending", "pending_expense" -> Triple("Dépenses", Icons.Default.ReceiptLong, ElTagTone.INFO)
    "pending_check" -> Triple("Paiements", Icons.Default.Payment, ElTagTone.INFO)
    "attendance_alert" -> Triple("Présences", Icons.Default.School, ElTagTone.WARNING)
    "homework" -> Triple("Devoirs", Icons.Default.School, ElTagTone.INFO)
    "audit" -> Triple("Audit", Icons.Default.Info, ElTagTone.NEUTRAL)
    "system" -> Triple("Système", Icons.Default.Announcement, ElTagTone.NEUTRAL)
    "message" -> Triple("Messages", Icons.Default.Announcement, ElTagTone.INFO)
    else -> Triple("Autres", Icons.Default.Info, ElTagTone.NEUTRAL)
}

/** The canonical priority → tone mapping (urgent → DANGER, …, low → NEUTRAL). */
internal fun notificationPriorityTone(priority: String): ElTagTone = when (priority) {
    "urgent" -> ElTagTone.DANGER
    "high" -> ElTagTone.WARNING
    "medium" -> ElTagTone.INFO
    else -> ElTagTone.NEUTRAL
}
