package com.example.infrastructure.supabase

import android.content.Context
import com.example.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.android.Android
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase client provider — lazy singleton.
 *
 * Reads URL + anon key from BuildConfig (injected by the secrets plugin
 * from `.env`, or fallback to the default values in `build.gradle.kts`).
 *
 * The client uses the Android Ktor engine. JWT persistence is handled
 * by the Auth plugin via a [SettingsSessionManager] (built by
 * [EncryptedSettingsStorage.createSessionManager]) — refresh tokens
 * survive app cold-starts.
 *
 * CRITICAL: never ship the `service_role` key in the APK. Only the `anon`
 * key is used here; RLS enforces tenant isolation server-side.
 */
@Singleton
class SupabaseClientProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val client: SupabaseClient by lazy { build() }

    val auth get() = client.auth
    val postgrest get() = client.postgrest
    val storage get() = client.storage
    val realtime get() = client.realtime
    val functions get() = client.functions

    private fun build(): SupabaseClient {
        val rawUrl = BuildConfig.SUPABASE_URL.trim().removeSurrounding("\"")
        val rawKey = BuildConfig.SUPABASE_ANON_KEY.trim().removeSurrounding("\"")

        val validUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            "https://demo.supabase.co"
        }

        val validKey = if (rawKey.isNotBlank()) rawKey else "demo-key"

        return try {
            createSupabaseClient(
                supabaseUrl = validUrl,
                supabaseKey = validKey,
            ) {
                install(Auth) {
                    // Persist JWT refresh tokens to EncryptedSharedPreferences
                    // (via SettingsSessionManager) so users stay signed in
                    // across app cold-starts.
                    sessionManager = EncryptedSettingsStorage.createSessionManager(context)
                }
                install(Postgrest)
                install(Realtime)
                install(Storage)
                install(Functions)
                httpEngine = Android.create()
            }
        } catch (e: Exception) {
            createSupabaseClient(
                supabaseUrl = "https://demo.supabase.co",
                supabaseKey = "demo-key",
            ) {
                install(Auth) {
                    sessionManager = EncryptedSettingsStorage.createSessionManager(context)
                }
                install(Postgrest)
                httpEngine = Android.create()
            }
        }
    }
}
