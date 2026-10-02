package com.example.ui.features.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.Result
import com.example.core.Session
import com.example.domain.model.ChatChannel
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
 * T-102-follow-up — the channel-list side of the Android chat
 * (v1: 21st session; v2: 133rd — unread badges from the Room cache).
 *
 * Loads the signed-in staff member's channels on open, then refreshes on
 * realtime chat events (T-069's manager, the tableEvents bus) while the
 * screen is visible. Since v2 the repository is the Room-caching
 * decorator: an online load failure with cached history serves the cache
 * (Result.Ok) — only a cold offline start surfaces [ChatListState.error]
 * (the operator sees the truth, never a silent empty list).
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val sessionManager: SessionManager,
    private val realtime: RealtimeSyncManager,
) : ViewModel() {

    data class ChatListState(
        val loading: Boolean = false,
        val channels: List<ChatChannel> = emptyList(),
        val error: String? = null,
        // v2: per-channel unread badge counts (from the Room cache; empty
        // offline cold start = no badges — never fabricated counts).
        val unreadByChannel: Map<String, Int> = emptyMap(),
    )

    private val _state = MutableStateFlow(ChatListState())
    val state: StateFlow<ChatListState> = _state.asStateFlow()

    private var realtimeJob: Job? = null

    init {
        refresh()
        observeRealtime()
    }

    val session: Session? get() = sessionManager.state.value

    fun refresh() {
        val s = session ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            when (val result = chatRepository.channels(s.userId)) {
                is Result.Ok -> {
                    _state.value = ChatListState(
                        loading = false,
                        channels = result.value,
                        unreadByChannel = chatRepository.unreadByChannel(s.userId),
                    )
                }
                is Result.Err -> {
                    _state.value = _state.value.copy(
                        loading = false,
                        channels = _state.value.channels, // keep stale content visible
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
                if (table == "chat_channels" || table == "chat_messages") refresh()
            }
        }
    }

    override fun onCleared() {
        realtimeJob?.cancel()
        super.onCleared()
    }
}
