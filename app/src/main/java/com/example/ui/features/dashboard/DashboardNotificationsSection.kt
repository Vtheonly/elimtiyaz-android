package com.example.ui.features.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.core.formatDzd
import com.example.domain.model.AppNotification
import com.example.domain.model.Payment
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElButtonSize
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.theme.ElTheme

/**
 * T-489 (UI-330) — the Overview tab's COMPACT activity feed (the former
 * "Dernières Opérations & Journal" section: 3 two-line payment rows + up to
 * 3 full notification cards).
 *
 * The owner's mandate: the payment/ID/transaction rows "do not need to take
 * up so much space" on the first page. The compact contract:
 *  - ONE header with a "Tout voir" quick access to the Alerts inbox (the
 *    full notification list — the route was already wired into
 *    DashboardHubScreen but previously UNUSED);
 *  - at most 2 SINGLE-LINE recent-payment rows (receipt + amount; the
 *    method/date detail lives in the Finance journal, one tap away);
 *  - ONE summary row for the unread notifications (count + the most
 *    recent title) that opens the inbox — instead of a card stack.
 *
 * Every relocated surface stays reachable: payments → the Finance hub's
 * Journal tab (onNavigateToFinancials), notifications → the Alerts inbox
 * (onNavigateToAlerts).
 */
@Composable
internal fun DashboardNotificationsSection(
    notifications: List<AppNotification>,
    recentPayments: List<Payment>,
    onNavigateToFinancials: () -> Unit,
    onNavigateToAlerts: () -> Unit = {},
) {
    val unreadNotifications = notifications.filter { it.readAt == null }

    Column(verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.md)) {
        ElSectionHeader(
            title = "Activité Récente",
            subtitle = if (recentPayments.isNotEmpty() || unreadNotifications.isNotEmpty()) {
                "${recentPayments.size} encaissements récents · ${unreadNotifications.size} non lues"
            } else {
                "Encaissements récents et alertes opérationnelles"
            },
            trailing = {
                ElButton(
                    text = "Tout voir",
                    onClick = onNavigateToAlerts,
                    variant = ElButtonVariant.GHOST,
                    size = ElButtonSize.SMALL,
                )
            },
        )

        // ── Recent Cash-Desk Payments — compact single-line rows ─────────
        if (recentPayments.isNotEmpty()) {
            ElCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToFinancials,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Encaissements récents au guichet",
                        style = ElTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = ElTheme.colors.textPrimary,
                    )
                    Spacer(Modifier.height(ElTheme.spacing.sm))

                    recentPayments.take(2).forEach { payment ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = ElTheme.spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(ElTheme.spacing.xl)
                                        .clip(CircleShape)
                                        .background(ElTheme.colors.success.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Payments,
                                        contentDescription = null,
                                        tint = ElTheme.colors.success,
                                        modifier = Modifier.size(ElTheme.spacing.md),
                                    )
                                }
                                Spacer(Modifier.size(ElTheme.spacing.sm))
                                Text(
                                    text = payment.receiptNumber,
                                    style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = ElTheme.colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Text(
                                text = "+${(payment.amount / 100).formatDzd()} DA",
                                style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = ElTheme.colors.success,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        // ── Unread notifications — ONE summary row into the inbox ────────
        if (unreadNotifications.isNotEmpty()) {
            ElCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = onNavigateToAlerts,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
                ) {
                    Box(
                        modifier = Modifier
                            .size(ElTheme.spacing.xxl)
                            .clip(CircleShape)
                            .background(ElTheme.colors.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = ElTheme.colors.primary,
                            modifier = Modifier.size(ElTheme.spacing.lg),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = unreadNotifications.first().title,
                            style = ElTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = ElTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Notifications non lues",
                            style = ElTheme.typography.labelSmall,
                            color = ElTheme.colors.textSecondary,
                            maxLines = 1,
                        )
                    }
                    ElTag(
                        text = if (unreadNotifications.size > 99) "99+" else unreadNotifications.size.toString(),
                        tone = if (unreadNotifications.isNotEmpty()) ElTagTone.WARNING else ElTagTone.INFO,
                    )
                }
            }
        }
    }
}
