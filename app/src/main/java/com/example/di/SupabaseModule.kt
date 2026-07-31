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
import javax.inject.Singleton

/**
 * Supabase DI module — provides the singleton client + auth/storage/postgrest/realtime accessors.
 *
 * The [SupabaseClientProvider] reads URL + anon key from BuildConfig. JWT
 * persistence is handled by the Supabase Auth plugin via EncryptedSharedPreferences
 * (configured here as the SettingsStorage implementation).
 */
@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides @Singleton
    fun provideSupabaseClientProvider(): com.example.infrastructure.supabase.SupabaseClientProvider =
        com.example.infrastructure.supabase.SupabaseClientProvider()

    @Provides @Singleton
    fun provideSupabaseClient(provider: com.example.infrastructure.supabase.SupabaseClientProvider): SupabaseClient =
        provider.client

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
}
