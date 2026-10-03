package com.example.ui.features.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.domain.model.ChatAttachment
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
import com.example.domain.repository.StorageBuckets
import com.example.domain.repository.StorageRepository
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
    // T-463 / CHAT-300: parent/student conversations carry the Portail tag
    // so the operator always knows which system they are in.
    isPortal: Boolean = false,
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
                    if (isPortal) {
                        ElTag(text = "Portail — parent/élève", tone = ElTagTone.INFO, size = ElTagSize.SM)
                    }
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
                            viewModel = viewModel,
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
                // T-464 / MEDIA-300: the attachment composer — pick (image/* +
                // application/pdf via the system picker), validate, upload on
                // send (a failed upload aborts), render inline on receipt.
                val context = androidx.compose.ui.platform.LocalContext.current
                val pickFile = rememberLauncherForActivityResult(
                    ActivityResultContracts.GetContent(),
                ) { uri ->
                    if (uri != null) {
                        // Read the picked file NOW (the ACTION_GET_CONTENT uri
                        // is only valid during this lifecycle scope).
                        runCatching {
                            val resolver = context.contentResolver
                            val size = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
                            val mime = resolver.getType(uri)
                            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "fichier"
                            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                            if (bytes != null && size > 0) {
                                viewModel.addPendingAttachment(name, mime, size, bytes)
                            } else {
                                viewModel.addPendingAttachment(name, mime, bytes?.size?.toLong() ?: 0L, bytes ?: ByteArray(0))
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ElTheme.spacing.md, vertical = ElTheme.spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs),
                ) {
                    if (state.pendingAttachments.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs)) {
                            state.pendingAttachments.forEachIndexed { i, pending ->
                                androidx.compose.material3.AssistChip(
                                    onClick = { viewModel.removePendingAttachment(i) },
                                    label = {
                                        Text(
                                            text = pending.fileName,
                                            style = ElTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        )
                                    },
                                    leadingIcon = {
                                        androidx.compose.material3.Icon(
                                            Icons.Default.Description,
                                            contentDescription = null,
                                            modifier = Modifier.size(ElTheme.spacing.md),
                                        )
                                    },
                                    trailingIcon = {
                                        androidx.compose.material3.Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Retirer",
                                            modifier = Modifier.size(ElTheme.spacing.md),
                                        )
                                    },
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                        ElIconButton(
                            icon = Icons.Default.Add,
                            onClick = { pickFile.launch("*/*") },
                            enabled = !state.sending && !state.uploading,
                            contentDescription = "Joindre une image ou un PDF (10 Mo max)",
                            tint = c.textPrimary,
                            background = androidx.compose.ui.graphics.Color.Transparent,
                        )
                        Spacer(Modifier.width(ElTheme.spacing.xs))
                        if (state.sending) {
                            ElSpinner(size = 22, strokeWidth = 2, modifier = Modifier.width(ElTheme.spacing.xl))
                        } else {
                            ElIconButton(
                                icon = Icons.AutoMirrored.Filled.Send,
                                onClick = {
                                    viewModel.send(draft)
                                    if (state.error == null) draft = ""
                                },
                                enabled = !state.sending && (draft.isNotBlank() || state.pendingAttachments.isNotEmpty()),
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
}

@Composable
private fun MessageBubble(message: ChatMessage, own: Boolean, viewModel: ChatDetailViewModel) {
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
                if (message.body.isNotBlank()) {
                    Text(
                        text = message.body,
                        style = ElTheme.typography.bodyMedium,
                        color = if (own) {
                            c.onPrimary
                        } else {
                            c.textPrimary
                        },
                    )
                }
                // T-464 / MEDIA-300: the Receive → Open → View → Download
                // half — images preview inline (a FRESH signed URL per
                // composition, vault §12.07: never cached), documents open
                // through the system viewer via the same signed-URL flow.
                if (message.attachments.isNotEmpty()) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(ElTheme.spacing.xs),
                        modifier = Modifier.padding(top = ElTheme.spacing.xs),
                    ) {
                        message.attachments.forEach { attachment ->
                            AttachmentView(attachment = attachment, viewModel = viewModel)
                        }
                    }
                }
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


// ============================================================================
// T-464 / MEDIA-300 — the attachment rendering (the receive half):
// images preview inline (a FRESH signed URL per composition — vault §12.07:
// never cached, never public), documents open in the system viewer through
// the same signed-URL flow (Open + Download are the same affordance on
// Android: the viewer offers the save action).
// ============================================================================

@Composable
private fun AttachmentView(attachment: ChatAttachment, viewModel: ChatDetailViewModel) {
    val c = ElTheme.colors
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    if (attachment.isImage) {
        var url by remember(attachment.storagePath) { mutableStateOf<String?>(null) }
        LaunchedEffect(attachment.storagePath) {
            url = viewModel.signedAttachmentUrl(attachment.storagePath)
        }
        val resolved = url
        when {
            resolved != null && resolved.startsWith("http") -> {
                AsyncImage(
                    model = resolved,
                    contentDescription = attachment.fileName,
                    modifier = Modifier
                        .size(width = 220.dp, height = 152.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                return
            }
            resolved != null && resolved.startsWith("file://") -> {
                val file = java.io.File(resolved.removePrefix("file://"))
                if (file.exists()) {
                    AsyncImage(
                        model = file,
                        contentDescription = attachment.fileName,
                        modifier = Modifier
                            .size(width = 220.dp, height = 152.dp)
                            .clip(RoundedCornerShape(10.dp)),
                    )
                    return
                }
            }
        }
    }

    // The document chip: click → a fresh signed URL → the system viewer.
    androidx.compose.material3.AssistChip(
        onClick = { scope.launch { viewModel.openAttachment(context, attachment) } },
        label = {
            Text(
                text = attachment.fileName,
                style = ElTheme.typography.labelSmall,
                color = c.textPrimary,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            androidx.compose.material3.Icon(
                Icons.Default.Description,
                contentDescription = null,
                modifier = Modifier.size(ElTheme.spacing.lg),
            )
        },
    )
}
