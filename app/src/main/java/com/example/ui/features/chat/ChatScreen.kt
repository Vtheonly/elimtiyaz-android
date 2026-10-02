package com.example.ui.features.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.domain.model.ChatChannel
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElBadge
import com.example.ui.designsystem.components.display.ElBadgeStyle
import com.example.ui.designsystem.components.display.ElBadgeTone
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.feedback.ElSpinner
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Channel list — first screen of the chat stack (T-102).
 *
 * Read-side only (per ADR-008 the desktop creates channels from parent
 * files; the send path lives in ChatDetail). Channel rows show the channel
 * name, and a last-activity timestamp when present.
 *
 * T-460 pass G-c (issue #3 F-06): the raw-M3 chrome → the design system
 * (ElScaffold/ElTopBar, the DS spinner in the top bar, ElAlertBanner for
 * errors, ElEmptyState for the empty case). The ADR-008 read-side contract
 * and the refresh action are preserved.
 */
@Composable
fun ChatScreen(
    onBack: () -> Unit,
    onOpenChannel: (ChatChannel) -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val c = ElTheme.colors
    val state by viewModel.state.collectAsState()

    ElScaffold(
        topBar = {
            ElTopBar(
                title = "Messagerie",
                onBack = onBack,
                actions = {
                    if (state.loading) {
                        ElSpinner(
                            size = 22,
                            strokeWidth = 2,
                            color = c.primary,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                    ElIconButton(
                        icon = Icons.Default.Refresh,
                        onClick = viewModel::refresh,
                        contentDescription = "Actualiser",
                        tint = c.textPrimary,
                        background = androidx.compose.ui.graphics.Color.Transparent,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.error?.let { error ->
                ElAlertBanner(
                    title = error,
                    severity = ElAlertSeverity.DANGER,
                )
            }

            if (!state.loading && state.channels.isEmpty() && state.error == null) {
                EmptyChannelsHint()
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp),
            ) {
                items(state.channels, key = { it.id }) { channel ->
                    ChannelRow(
                        channel = channel,
                        unread = state.unreadByChannel[channel.id] ?: 0,
                        onClick = { onOpenChannel(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChannelsHint() {
    ElEmptyState(
        icon = Icons.Default.Refresh,
        title = "Aucune conversation",
        subtitle = "Les conversations sont ouvertes par le personnel depuis le bureau " +
            "(fiche parent → Messager). Les parents y répondent depuis le portail.",
    )
}

@Composable
private fun ChannelRow(channel: ChatChannel, unread: Int, onClick: () -> Unit) {
    val c = ElTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Channel avatar: first letter of the name in a circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(c.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = channel.name.take(1).uppercase(),
                style = ElTheme.typography.titleMedium,
                color = c.onPrimary,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = ElTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = channel.lastMessagePreview ?: channel.description ?: "",
                style = ElTheme.typography.bodySmall,
                color = c.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            // T-102 v2: the per-channel unread badge (derived from the Room
            // cache; 0 = no badge — a cached 0 is honest, an offline cold
            // start shows none rather than a fabricated count).
            if (unread > 0) {
                ElBadge(
                    text = if (unread > 99) "99+" else unread.toString(),
                    tone = ElBadgeTone.DANGER,
                    style = ElBadgeStyle.SOLID,
                )
                Spacer(Modifier.height(4.dp))
            }
            channel.lastMessageAt?.let { ts ->
                Text(
                    text = relativeTime(ts),
                    style = ElTheme.typography.labelSmall,
                    color = c.textSecondary,
                )
            }
            if (channel.isAnnouncement) {
                ElTag(text = "Annonce", tone = ElTagTone.INFO, size = ElTagSize.SM)
            }
        }
    }
}

/** Compact relative timestamp (Aujourd'hui / hier / date). */
internal fun relativeTime(iso: String): String = runCatching {
    val sent = Instant.parse(iso)
    val now = Instant.now()
    val days = ChronoUnit.DAYS.between(sent.truncatedTo(ChronoUnit.DAYS), now.truncatedTo(ChronoUnit.DAYS))
    when {
        days <= 0L -> DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault()).format(sent)
        days == 1L -> "hier"
        days < 7L -> "${days}j"
        else -> DateTimeFormatter.ofPattern("dd/MM").withZone(java.time.ZoneId.systemDefault()).format(sent)
    }
}.getOrDefault("")
