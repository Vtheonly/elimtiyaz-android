package com.example.ui.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.core.Session
import com.example.domain.model.ChatMessage
import com.example.domain.repository.ChatRepository
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
) : ViewModel() {

    data class ChatThreadState(
        val loading: Boolean = false,
        val messages: List<ChatMessage> = emptyList(),
        val sending: Boolean = false,
        val error: String? = null,
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

    fun send(body: String) {
        val id = channelId ?: return
        val s = session ?: return
        val trimmed = body.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_BODY_LENGTH) return
        viewModelScope.launch {
            _state.value = _state.value.copy(sending = true, error = null)
            when (val result = chatRepository.send(id, s.userId, trimmed)) {
                is Result.Ok -> {
                    _state.value = _state.value.copy(
                        sending = false,
                        messages = _state.value.messages + result.value,
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
