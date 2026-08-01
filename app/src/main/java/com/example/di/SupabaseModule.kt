package com.example.di

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.settings.SettingsStorage
import javax.inject.Singleton

/**
 * Supabase DI module — provides the singleton client + auth/storage/postgrest/realtime accessors.
 *
 * The [SupabaseClientProvider] reads URL + anon key from BuildConfig. JWT
 * persistence is handled by the Supabase Auth plugin via [EncryptedSettingsStorage]
 * (backed by EncryptedSharedPreferences — see below).
 *
 * BUGFIX (iter 2): the previous iteration declared an EncryptedSharedPreferences
 * provider but never wired it into the Auth plugin. Now [provideSettingsStorage]
 * binds the encrypted prefs to the Auth plugin via [EncryptedSettingsStorage],
 * so refresh tokens survive app cold-starts and users stay signed in.
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides @Singleton
    fun provideSupabaseClientProvider(
        settingsStorage: SettingsStorage,
    ): com.example.infrastructure.supabase.SupabaseClientProvider =
        com.example.infrastructure.supabase.SupabaseClientProvider(settingsStorage)

    @Provides @Singleton
    fun provideSupabaseClient(provider: com.example.infrastructure.supabase.SupabaseClientProvider): SupabaseClient =
        provider.client

    /**
     * Provide the encrypted [SharedPreferences] used for both:
     *   - Supabase Auth JWT persistence (via [EncryptedSettingsStorage]).
     *   - Any other secrets the app needs to store (FCM tokens, etc.).
     *
     * Falls back to plain `MODE_PRIVATE` SharedPreferences if the Android
     * Keystore is unavailable (rare — only on broken/emulator images).
     */
    @Provides @Singleton
    fun provideEncryptedPrefs(@ApplicationContext context: Context): android.content.SharedPreferences =
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "el-imtiyaz-secrets",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            context.getSharedPreferences("el-imtiyaz-fallback-prefs", Context.MODE_PRIVATE)
        }

    /**
     * Provide [SettingsStorage] for the Supabase Auth plugin — wraps the
     * encrypted SharedPreferences so JWT refresh tokens persist across
     * cold-starts.
     */
    @Provides @Singleton
    fun provideSettingsStorage(prefs: android.content.SharedPreferences): SettingsStorage =
        com.example.infrastructure.supabase.EncryptedSettingsStorage(prefs)
}
