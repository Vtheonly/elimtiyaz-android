package com.example.infrastructure.supabase

import com.example.infrastructure.sync.RealtimeEventSource
import com.example.infrastructure.sync.TableChanged
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SupabaseRealtimeEventSource — the single production implementation of
 * [RealtimeEventSource] (T-069 / REALTIME-104).
 *
 * One realtime channel per table (`android-realtime-<table>`), subscribing to
 * ALL postgres-change events (event `*`, schema `public`, no column filter) —
 * the same contract as the website's `useRealtimeInvalidation` hooks: RLS
 * scopes the delivered events to the rows the signed-in user may see, so NO
 * explicit tenant/user filter is needed (and none may be added — the
 * REALTIME-102 lesson: a narrow filter silently drops role-broadcast rows).
 *
 * The supabase-kt Realtime plugin (installed in [SupabaseClientProvider])
 * auto-provides the session's JWT to the socket (`disconnectOnSessionLoss`)
 * — postgres-change events arrive RLS-filtered per the signed-in user.
 *
 * Lifecycle contract (cold flow): collecting joins the channel topic and
 * starts emitting; cancelling the collection removes the channel from the
 * socket. `removeChannel` is a suspend call but `awaitClose` is not — the
 * removal therefore runs on a detached cleanup scope (best-effort; the
 * socket also tears channels down on sign-out via disconnectOnSessionLoss).
 */
@Singleton
class SupabaseRealtimeEventSource @Inject constructor(
    private val provider: SupabaseClientProvider,
) : RealtimeEventSource {

    override val isConfigured: Boolean
        get() = provider.isConfigured()

    /** Detached scope for the non-suspend awaitClose teardown. */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun changes(table: String): Flow<TableChanged> = channelFlow {
        val channel = provider.realtime.channel("android-realtime-$table")
        val events = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            this.table = table
        }
        // Join the channel topic (sends the postgres_changes join message).
        channel.subscribe()
        val collector = launch {
            events.collect { send(TableChanged(table)) }
        }
        awaitClose {
            collector.cancel()
            cleanupScope.launch {
                runCatching { provider.realtime.removeChannel(channel) }
            }
        }
    }
}
