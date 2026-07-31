package com.example.infrastructure.supabase

import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
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
 * The client uses the Android Ktor engine. JWT is managed automatically
 * by the Auth plugin (stored in EncryptedSharedPreferences via the
 * `SettingsStorage` implementation configured in [SupabaseModule]).
 *
 * CRITICAL: never ship the `service_role` key in the APK. Only the `anon`
 * key is used here; RLS enforces tenant isolation server-side.
 */
@Singleton
class SupabaseClientProvider @Inject constructor() {

    val client: SupabaseClient by lazy { build() }

    val auth get() = client.auth
    val postgrest get() = client.postgrest
    val storage get() = client.storage
    val realtime get() = client.realtime
    val functions get() = client.functions

    private fun build(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY,
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
        install(Storage)
        install(Functions)
        httpEngine = Android
    }
}
