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
}
