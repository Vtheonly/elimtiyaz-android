package com.example.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import javax.inject.Singleton

/**
 * Supabase DI module — provides the singleton client + auth/storage/postgrest/realtime accessors.
 *
 * The [SupabaseClientProvider] reads URL + anon key from BuildConfig. JWT
 * persistence is handled by the Supabase Auth plugin via
 * [com.example.infrastructure.supabase.EncryptedSettingsStorage.createSessionManager]
 * (backed by EncryptedSharedPreferences).
 *
 * Note: the encrypted prefs + SettingsSessionManager are created lazily
 * inside [SupabaseClientProvider.build()] because they need an Android
 * Context, which Hilt provides via `@ApplicationContext`. The
 * `EncryptedSettingsStorage` object exposes a `clear(context)` helper
 * used by `SupabaseAuthRepository.signOut()` to wipe the local session.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides @Singleton
    fun provideSupabaseClientProvider(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
    ): com.example.infrastructure.supabase.SupabaseClientProvider =
        com.example.infrastructure.supabase.SupabaseClientProvider(context)

    @Provides @Singleton
    fun provideSupabaseClient(provider: com.example.infrastructure.supabase.SupabaseClientProvider): SupabaseClient =
        provider.client
}
