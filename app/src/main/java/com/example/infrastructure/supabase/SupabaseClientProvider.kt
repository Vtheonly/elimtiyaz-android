package com.example.infrastructure.supabase

import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.settings.SettingsStorage
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
 * from .env, or fallback to the default values in build.gradle.kts).
 *
 * The client uses the Android Ktor engine. JWT persistence is handled
 * by the Auth plugin via the injected [SettingsStorage] (which is backed
 * by EncryptedSharedPreferences — see [com.example.di.SupabaseModule]).
 *
 * BUGFIX (iter 2): previously the Auth plugin was installed without a
 * SettingsStorage, so it used the default in-memory storage — refresh
 * tokens were lost on every app cold-start, forcing re-login. Now we
 * pass a persistent [SettingsStorage] so JWTs survive cold-starts.
 *
 * CRITICAL: never ship the `service_role` key in the APK. Only the `anon`
 * key is used here; RLS enforces tenant isolation server-side.
 */
@Singleton
class SupabaseClientProvider @Inject constructor(
    private val settingsStorage: SettingsStorage,
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
                    // so users stay signed in across app cold-starts.
                    settingsStorage = this@SupabaseClientProvider.settingsStorage
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
                    settingsStorage = this@SupabaseClientProvider.settingsStorage
                }
                install(Postgrest)
                httpEngine = Android.create()
            }
        }
    }
}
