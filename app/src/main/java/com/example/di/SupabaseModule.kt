package com.example.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

/**
 * Supabase DI module — provides the singleton client + auth/storage/postgrest/realtime accessors.
 *
 * The mobile app is **offline-first**: Room is the source of truth and all
 * repositories read/write locally. However, the Supabase client is still
 * provided so that:
 *
 *   1. **FCM token registration** can call the `register_fcm_token` RPC when
 *      Supabase is configured.
 *   2. **Remote sync is ALREADY wired** (T-062 / DRIFT-007 corrected this
 *      comment): the local repositories (payments, students, installments,
 *      ledger, grades, attendance, homework) inject `SyncSupport` and call
 *      `enqueueOnly(...)`, which pushes writes to Supabase through the
 *      `SyncQueueDispatcher`'s canonical RPCs — no `@Binds` swap is needed
 *      or involved. The pull side (`PullSyncRepository`) fetches back into
 *      Room on the sync cycle.
 *   3. **Auth** can fall back to real Supabase Auth when credentials are
 *      present in `.env` (otherwise the local `LocalAuthRepository` is used).
 *
 * The [SupabaseClientProvider] reads URL + anon key from BuildConfig (injected
 * by the secrets plugin from `.env`). JWT persistence is handled by the
 * Supabase Auth plugin via [EncryptedSettingsStorage.createSessionManager]
 * (backed by EncryptedSharedPreferences).
 *
 * CRITICAL: never ship the `service_role` key in the APK. Only the `anon`
 * key is used here; RLS enforces tenant isolation server-side.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides @Singleton
    fun provideSupabaseClientProvider(
        @ApplicationContext context: Context,
    ): com.example.infrastructure.supabase.SupabaseClientProvider =
        com.example.infrastructure.supabase.SupabaseClientProvider(context)

    @Provides @Singleton
    fun provideSupabaseClient(
        provider: com.example.infrastructure.supabase.SupabaseClientProvider,
    ): SupabaseClient = provider.client

    // ── T-069 / REALTIME-104: realtime freshness wiring ─────────────────────
    // The single production RealtimeEventSource, the OnlineGate adapter, and
    // the RealtimePullTarget binding (PullSyncRepository implements the seam —
    // Hilt needs the interface→impl binding explicit). RealtimeSyncManager
    // consumes these; unit tests provide fakes directly.

    @Provides @Singleton
    fun provideRealtimePullTarget(
        repo: com.example.infrastructure.sync.PullSyncRepository,
    ): com.example.infrastructure.sync.RealtimePullTarget = repo

    @Provides @Singleton
    fun provideRealtimeEventSource(
        source: com.example.infrastructure.supabase.SupabaseRealtimeEventSource,
    ): com.example.infrastructure.sync.RealtimeEventSource = source

    @Provides @Singleton
    fun provideOnlineGate(
        detector: com.example.infrastructure.sync.OnlineDetector,
    ): com.example.infrastructure.sync.OnlineGate =
        com.example.infrastructure.sync.OnlineGate { detector.isOnline() }

    // ── T-102-follow-up / ANDR-CHAT-200: the chat repository ────────────────
    // Chat is ONLINE-ONLY in v1 (no Room cache — deliberate scope decision,
    // see the task entry): the repository talks to the canonical chat
    // tables directly, so it is provided straight from the Supabase
    // implementation (NOT routed through the Local*Repository layer).
    @Provides @Singleton
    fun provideChatRepository(
        repo: com.example.infrastructure.supabase.SupabaseChatRepository,
    ): com.example.domain.repository.ChatRepository = repo
}
