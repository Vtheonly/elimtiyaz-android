package com.example.ui.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.core.Session
import com.example.domain.model.ChatAttachment
import com.example.domain.model.ChatMessage
import com.example.domain.repository.ChatRepository
import com.example.domain.repository.StorageBuckets
import com.example.domain.repository.StorageRepository
import com.example.infrastructure.sync.RealtimeSyncManager
import com.example.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * T-102-follow-up — the conversation side of the Android chat (v1):
 * messages + composer + read receipts.
 *
 * Behaviour mirrors the website's MessagesView:
 *   - messages load oldest-first, refresh on realtime chat_messages events;
 *   - incoming (not-authored-by-me, not-yet-read) messages are marked read
 *     automatically while the channel is open (migration 0051's contract:
 *     a member appends their OWN read_by entry);
 *   - send inserts directly (online only — the 0061 trigger maintains the
 *     channel's last-message ordering columns).
 */
@HiltViewModel
class ChatDetailViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val sessionManager: SessionManager,
    private val realtime: RealtimeSyncManager,
    // T-464 / MEDIA-300: the attachment upload leg (the canonical
    // StorageRepository — tenant-scoped paths, honest failure classes).
    private val storageRepository: StorageRepository,
) : ViewModel() {

    data class ChatThreadState(
        val loading: Boolean = false,
        val messages: List<ChatMessage> = emptyList(),
        val sending: Boolean = false,
        val uploading: Boolean = false,
        val error: String? = null,
        // T-464: the pending attachments (validated, not yet uploaded).
        val pendingAttachments: List<PendingAttachment> = emptyList(),
    )

    /** A locally picked file awaiting the send (validated client-side). */
    data class PendingAttachment(
        val fileName: String,
        val mimeType: String?,
        val sizeBytes: Long,
        val bytes: ByteArray,
    )

    private val _state = MutableStateFlow(ChatThreadState())
    val state: StateFlow<ChatThreadState> = _state.asStateFlow()

    private var realtimeJob: Job? = null
    private var channelId: String? = null

    val session: Session? get() = sessionManager.state.value

    fun bind(channelId: String) {
        if (this.channelId == channelId) return
        this.channelId = channelId
        refresh()
        observeRealtime()
    }

    fun refresh() {
        val id = channelId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val result = chatRepository.messages(id)) {
                is Result.Ok -> {
                    _state.value = _state.value.copy(loading = false, messages = result.value)
                    markIncomingRead(result.value)
                }
                is Result.Err -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        messages = _state.value.messages, // keep stale content visible
                        error = result.error.userMessage,
                    )
                }
            }
        }
    }

    /** VAULT §05 / website parity: mark incoming messages read while open. */
    private fun markIncomingRead(messages: List<ChatMessage>) {
        val s = session ?: return
        val incoming = messages.filter {
            it.authorId != s.userId && !it.isReadBy(s.userId)
        }
        if (incoming.isEmpty()) return
        viewModelScope.launch {
            when (val result = chatRepository.markRead(incoming, s.userId)) {
                is Result.Err -> {
                    // REALTIME-101 lesson: surface the rejection — never
                    // swallow read-receipt failures silently.
                    _state.value = _state.value.copy(
                        error = _state.value.error ?: result.error.userMessage,
                    )
                }
                is Result.Ok -> Unit
            }
        }
    }

    /**
     * T-464 / MEDIA-300: pick a file into the pending set — validated
     * client-side against the bucket's limits (10 MB; jpeg/png/webp/pdf/
     * xlsx/plain) so the user hears it from the UI, not from a storage error.
     */
    fun addPendingAttachment(fileName: String, mimeType: String?, sizeBytes: Long, bytes: ByteArray) {
        if (!com.example.domain.model.ChatAttachmentLimits.isAllowed(sizeBytes, mimeType)) {
            _state.value = _state.value.copy(
                error = "Pièce jointe refusée : 10 Mo max (images JPEG/PNG/WebP, PDF, XLSX ou texte).",
            )
            return
        }
        _state.value = _state.value.copy(
            error = null,
            pendingAttachments = _state.value.pendingAttachments +
                PendingAttachment(fileName, mimeType, sizeBytes, bytes),
        )
    }

    fun removePendingAttachment(index: Int) {
        _state.value = _state.value.copy(
            pendingAttachments = _state.value.pendingAttachments.filterIndexed { i, _ -> i != index },
        )
    }

    /**
     * Send: Upload → Store happens FIRST (each pending file goes to the
     * chat-attachments bucket under the channel's folder — the 0136 member-
     * scoped path canon), then ONE insert carries the body + the metadata.
     * A failed upload aborts the send entirely (no message referencing a
     * missing file); a rejected insert surfaces (CROSS-200).
     */
    fun send(body: String) {
        val id = channelId ?: return
        val s = session ?: return
        val trimmed = body.trim()
        val pending = _state.value.pendingAttachments
        if ((trimmed.isEmpty() && pending.isEmpty()) || trimmed.length > MAX_BODY_LENGTH) return
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true, error = null)
            // ── Upload → Store ──
            val uploaded = mutableListOf<ChatAttachment>()
            if (pending.isNotEmpty()) {
                _state.value = _state.value.copy(uploading = true)
                for (file in pending) {
                    // Unique object name per upload (timestamp prefix) — two
                    // same-named files in one channel must not overwrite.
                    val uniqueName = "${System.currentTimeMillis()}-" +
                        file.fileName.replace(Regex("[^\\w.\\-]+"), "_")
                    when (val up = storageRepository.uploadProof(
                        bucket = StorageBuckets.CHAT_ATTACHMENTS,
                        tenantId = s.tenantId,
                        entityId = id,
                        fileName = uniqueName,
                        bytes = file.bytes,
                        mimeType = file.mimeType ?: "application/octet-stream",
                    )) {
                        is Result.Ok -> uploaded.add(
                            ChatAttachment(
                                fileName = file.fileName,
                                storagePath = up.value,
                                mimeType = file.mimeType,
                                sizeBytes = file.sizeBytes,
                            ),
                        )
                        is Result.Err -> {
                            _state.value = _state.value.copy(
                                sending = false,
                                uploading = false,
                                error = up.error.userMessage,
                            )
                            return@launch
                        }
                    }
                }
                _state.value = _state.value.copy(uploading = false)
            }
            // ── Send (the metadata rides the insert) ──
            when (val result = chatRepository.send(id, s.userId, trimmed, uploaded)) {
                is Result.Ok -> {
                    _state.value = _state.value.copy(
                        sending = false,
                        messages = _state.value.messages + result.value,
                        pendingAttachments = emptyList(),
                    )
                }
                is Result.Err -> {
                    _state.value = _state.value.copy(
                        sending = false,
                        error = result.error.userMessage,
                    )
                }
            }
        }
    }

    /**
     * T-464 / MEDIA-300: a FRESH signed URL for one attachment (the receive
     * half — vault §12.07: signed URLs are minted per use, never cached).
     * Returns null on failure (the caller degrades to the document chip).
     */
    suspend fun signedAttachmentUrl(storagePath: String): String? =
        when (val r = storageRepository.createSignedUrl(StorageBuckets.CHAT_ATTACHMENTS, storagePath)) {
            is Result.Ok -> r.value
            is Result.Err -> null
        }

    /**
     * T-464 / MEDIA-300: open an attachment in the system viewer via a fresh
     * signed URL (the Android Open/View/Download affordance — the viewer
     * offers the save action). Failures surface as the banner error.
     */
    fun openAttachment(context: android.content.Context, attachment: ChatAttachment) {
        viewModelScope.launch {
            when (val r = storageRepository.createSignedUrl(StorageBuckets.CHAT_ATTACHMENTS, attachment.storagePath)) {
                is Result.Ok -> {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse(r.value)
                    }
                    runCatching {
                        context.startActivity(
                            android.content.Intent.createChooser(intent, "Ouvrir ${attachment.fileName}"),
                        )
                    }
                }
                is Result.Err -> {
                    _state.value = _state.value.copy(
                        error = _state.value.error ?: r.error.userMessage,
                    )
                }
            }
        }
    }

    private fun observeRealtime() {
        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            realtime.tableEvents.collect { table ->
                if (table == "chat_messages" || table == "chat_channels") refresh()
            }
        }
    }

    override fun onCleared() {
        realtimeJob?.cancel()
        super.onCleared()
    }

    companion object {
        /** Same ceiling as the website's chatMessageSchema (Zod, 5000 chars). */
        const val MAX_BODY_LENGTH = 5_000
    }
}
