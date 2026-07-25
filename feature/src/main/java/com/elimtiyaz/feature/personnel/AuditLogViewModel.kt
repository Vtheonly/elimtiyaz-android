package com.elimtiyaz.feature.personnel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.elimtiyaz.core.common.AppError
import com.elimtiyaz.core.common.Permission
import com.elimtiyaz.core.common.Result
import com.elimtiyaz.core.common.Session
import com.elimtiyaz.domain.model.AuditEntry
import com.elimtiyaz.domain.repository.AuditRepository
import com.elimtiyaz.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AuditLogViewModel — powers Route.AuditLog.
 *
 * Loads the most recent audit entries (capped at 200 by the repository) and
 * applies client-side filters by action type, entity type, actor search, and
 * date range. The screen renders them in pages of [pageSize] (default 50);
 * a "Charger plus" button grows the visible window until all matching rows
 * are shown.
 *
 * The current [Session] is exposed so the screen can gate itself on
 * [Permission.ViewAuditLog] (the route is reachable from the hub Audit tab
 * only when the user has the permission).
 */
@HiltViewModel
class AuditLogViewModel @Inject constructor(
    private val auditRepo: AuditRepository,
    auth: AuthRepository,
) : ViewModel() {

    /** Latest session — used by the screen to gate the audit view. */
    val session: StateFlow<Session?> = auth.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null,
    )

    private val _state = MutableStateFlow(AuditLogUiState())
    val state: StateFlow<AuditLogUiState> = _state.asStateFlow()

    init { load() }

    /** Fetch the recent audit entries from the repository. */
    fun load() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            auditRepo.recent(limit = MAX_FETCH).collect { result ->
                when (result) {
                    is Result.Success -> _state.update {
                        it.copy(
                            isLoading = false,
                            error = null,
                            allEntries = result.data.sortedByDescending { e -> e.at },
                            visibleCount = PAGE_SIZE,
                        )
                    }
                    is Result.Failure -> _state.update {
                        it.copy(isLoading = false, error = result.error, allEntries = emptyList())
                    }
                }
            }
        }
    }

    /** Set the action-type filter (e.g. "payment.create"). Null = all. */
    fun filterByAction(action: String?) {
        _state.update { it.copy(actionFilter = action, visibleCount = PAGE_SIZE) }
    }

    /** Set the entity-type filter (e.g. "payment", "student"). Null = all. */
    fun filterByEntity(entityType: String?) {
        _state.update { it.copy(entityFilter = entityType, visibleCount = PAGE_SIZE) }
    }

    /** Set the actor search string (matches actorId OR actorName, case-insensitive). */
    fun filterByActor(query: String) {
        _state.update { it.copy(actorQuery = query, visibleCount = PAGE_SIZE) }
    }

    /** Set the optional date range filter (ISO dates). Null start/end = open. */
    fun filterByDateRange(fromIso: String?, toIso: String?) {
        _state.update { it.copy(dateFrom = fromIso, dateTo = toIso, visibleCount = PAGE_SIZE) }
    }

    /** Show the next page of rows (page size = [PAGE_SIZE]). */
    fun loadMore() {
        _state.update { it.copy(visibleCount = it.visibleCount + PAGE_SIZE) }
    }

    /** Clear all filters. */
    fun clearFilters() {
        _state.update {
            it.copy(
                actionFilter = null,
                entityFilter = null,
                actorQuery = "",
                dateFrom = null,
                dateTo = null,
                visibleCount = PAGE_SIZE,
            )
        }
    }

    companion object {
        /** Max rows fetched from the repository in a single call. */
        const val MAX_FETCH = 200

        /** Number of rows revealed per "Charger plus" tap. */
        const val PAGE_SIZE = 50
    }
}

/** Audit log screen state. */
data class AuditLogUiState(
    val isLoading: Boolean = true,
    val error: AppError? = null,
    val allEntries: List<AuditEntry> = emptyList(),
    val actionFilter: String? = null,
    val entityFilter: String? = null,
    val actorQuery: String = "",
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val visibleCount: Int = AuditLogViewModel.PAGE_SIZE,
) {
    /** Filtered rows (applied client-side against [allEntries]). */
    val filteredEntries: List<AuditEntry>
        get() {
            val q = actorQuery.trim().lowercase()
            return allEntries.asSequence()
                .filter { actionFilter == null || it.action == actionFilter }
                .filter { entityFilter == null || it.entityType == entityFilter }
                .filter { q.isBlank() || it.actorName.lowercase().contains(q) || it.actorId.lowercase().contains(q) }
                .filter { entry ->
                    val at = entry.at
                    val fromOk = dateFrom == null || at >= dateFrom!!
                    val toOk = dateTo == null || at <= dateTo!!
                    fromOk && toOk
                }
                .toList()
        }

    /** Rows currently visible (the first [visibleCount] of [filteredEntries]). */
    val visibleEntries: List<AuditEntry>
        get() = filteredEntries.take(visibleCount)

    /** True when more filtered rows can be revealed via [AuditLogViewModel.loadMore]. */
    val canLoadMore: Boolean
        get() = visibleCount < filteredEntries.size

    /** Distinct action values present in the data — for the action filter dropdown. */
    val availableActions: List<String>
        get() = allEntries.map { it.action }.distinct().sorted()

    /** Distinct entity-type values present in the data — for the entity filter dropdown. */
    val availableEntityTypes: List<String>
        get() = allEntries.map { it.entityType }.distinct().sorted()
}
