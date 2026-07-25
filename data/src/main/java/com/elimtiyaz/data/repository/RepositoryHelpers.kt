package com.elimtiyaz.data.repository

import co.touchlab.kermit.Logger
import com.elimtiyaz.core.common.DispatcherProvider
import com.elimtiyaz.core.common.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Shared helpers for the Supabase-backed repositories. Centralises the
 * "emit Room cache → fetch Supabase → update Room → emit fresh" pattern so
 * each repository can focus on its own schema mapping.
 */
internal object RepositoryHelpers {

    private val log = Logger.withTag("Data.Repo")

    /**
     * Build a cold Flow that:
     * 1. Reads the local cache snapshot and emits it as `Result.Success`.
     * 2. Calls [fetch] to refresh from Supabase. On success it persists the
     *    fresh rows via [persist] and emits the freshly-loaded list; on
     *    failure it emits `Result.Failure`.
     */
    fun <T : Any> cacheThenFetch(
        dispatchers: DispatcherProvider,
        loadCache: suspend () -> List<T>,
        fetch: suspend () -> List<T>,
        persist: suspend (List<T>) -> Unit,
    ): Flow<Result<List<T>>> = flow {
        // 1. Emit cached snapshot (empty list if no cache yet).
        val cached = runCatching { loadCache() }.getOrDefault(emptyList())
        emit(Result.success(cached))
        // 2. Refresh from Supabase, persist, emit.
        val result = Result.runCatching {
            val fresh = fetch()
            runCatching { persist(fresh) }.onFailure { log.w { "Cache persist failed: ${it.message}" } }
            fresh
        }
        emit(result)
    }.flowOn(dispatchers.io)

    /** Single-item variant: emit cached value (or null) then refresh. */
    fun <T : Any> cacheThenFetchOne(
        dispatchers: DispatcherProvider,
        loadCache: suspend () -> T?,
        fetch: suspend () -> T,
        persist: suspend (T) -> Unit,
    ): Flow<Result<T>> = flow {
        val cached = runCatching { loadCache() }.getOrNull()
        if (cached != null) emit(Result.success(cached))
        val result = Result.runCatching {
            val fresh = fetch()
            runCatching { persist(fresh) }.onFailure { log.w { "Cache persist failed: ${it.message}" } }
            fresh
        }
        emit(result)
    }.flowOn(dispatchers.io)
}
