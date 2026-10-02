package com.example.ui.features.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.domain.model.ChatMessage
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.feedback.ElSpinner
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.theme.ElTheme
import java.time.format.DateTimeFormatter

/**
 * T-102-follow-up / ANDR-CHAT-200 — the Android chat CONVERSATION (v1):
 * messages (oldest-first), auto-scroll to the newest, own-vs-other message
 * bubbles, auto mark-incoming-read while open, and an online-only
 * composer (announcements channels are read-only for non-creators — same
 * rule as the website).
 */
@Composable
fun ChatDetailScreen(
    channelId: String,
    channelName: String,
    isAnnouncement: Boolean,
    onBack: () -> Unit,
    viewModel: ChatDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val session = viewModel.session

    LaunchedEffect(channelId) { viewModel.bind(channelId) }
    // Auto-scroll to the newest message whenever the list grows.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    val c = ElTheme.colors

    ElScaffold(
        topBar = {
            ElTopBar(
                title = channelName,
                onBack = onBack,
                actions = {
                    if (isAnnouncement) {
                        ElTag(text = "Annonce — lecture", tone = ElTagTone.INFO, size = ElTagSize.SM)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            state.error?.let { error ->
                ElAlertBanner(
                    title = error,
                    severity = ElAlertSeverity.DANGER,
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (state.loading && state.messages.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ElSpinner(size = 36)
                    }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = ElTheme.spacing.md,
                        vertical = ElTheme.spacing.sm,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.sm),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        MessageBubble(
                            message = message,
                            own = session != null && message.authorId == session.userId,
                        )
                    }
                }
            }

            if (isAnnouncement) {
                Text(
                    "Canal d'annonce — lecture seule (le bureau publie, l'école lit).",
                    style = ElTheme.typography.labelSmall,
                    color = c.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ElTheme.spacing.lg, vertical = ElTheme.spacing.sm),
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ElTheme.spacing.md, vertical = ElTheme.spacing.sm),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    ElTextField(
                        value = draft,
                        onValueChange = {
                            if (it.length <= ChatDetailViewModel.MAX_BODY_LENGTH) draft = it
                        },
                        placeholder = "Écrire un message…",
                        modifier = Modifier.weight(1f),
                        singleLine = false,
                        enabled = !state.sending,
                    )
                    Spacer(Modifier.width(ElTheme.spacing.sm))
                    if (state.sending) {
                        ElSpinner(size = 22, strokeWidth = 2, modifier = Modifier.width(ElTheme.spacing.xl))
                    } else {
                        ElIconButton(
                            icon = Icons.AutoMirrored.Filled.Send,
                            onClick = {
                                viewModel.send(draft)
                                if (state.error == null) draft = ""
                            },
                            enabled = !state.sending && draft.isNotBlank(),
                            contentDescription = "Envoyer",
                            tint = c.onPrimary,
                            background = c.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, own: Boolean) {
    val c = ElTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (own) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (own) 14.dp else 3.dp,
                bottomEnd = if (own) 3.dp else 14.dp,
            ),
            color = if (own) {
                c.primary
            } else {
                c.surfaceVariant
            },
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Column(Modifier.padding(horizontal = ElTheme.spacing.md, vertical = ElTheme.spacing.sm)) {
                Text(
                    text = message.body,
                    style = ElTheme.typography.bodyMedium,
                    color = if (own) {
                        c.onPrimary
                    } else {
                        c.textPrimary
                    },
                )
                Text(
                    text = runCatching {
                        DateTimeFormatter.ofPattern("HH:mm")
                            .withZone(java.time.ZoneId.systemDefault())
                            .format(java.time.Instant.parse(message.sentAt))
                    }.getOrDefault(""),
                    style = ElTheme.typography.labelSmall,
                    color = if (own) c.onPrimary.copy(alpha = 0.7f) else c.textSecondary,
                    modifier = Modifier.align(Alignment.End),
                )
            }
        }
    }
}
