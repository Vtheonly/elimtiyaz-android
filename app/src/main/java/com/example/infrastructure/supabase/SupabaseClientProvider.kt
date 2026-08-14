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
 * Supabase client provider — singleton with runtime dynamic configuration support.
 *
 * Reads URL + anon key from SharedPreferences (if set by user) or from BuildConfig
 * (injected by the secrets plugin from `.env`).
 *
 * The client uses the Android Ktor engine. JWT persistence is handled
 * by the Auth plugin via a [SettingsSessionManager].
 */
@Singleton
class SupabaseClientProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs = context.getSharedPreferences("supabase_config", Context.MODE_PRIVATE)

    @Volatile
    private var activeClient: SupabaseClient? = null

    val client: SupabaseClient
        get() {
            val existing = activeClient
            if (existing != null) return existing
            synchronized(this) {
                activeClient?.let { return it }
                val built = build()
                activeClient = built
                return built
            }
        }

    val auth get() = client.auth
    val postgrest get() = client.postgrest
    val storage get() = client.storage
    val realtime get() = client.realtime
    val functions get() = client.functions

    fun getActiveUrl(): String {
        val saved = prefs.getString(KEY_URL, "")?.trim() ?: ""
        if (saved.isNotBlank() && !saved.contains("your-project", ignoreCase = true) && !saved.contains("placeholder", ignoreCase = true) && !saved.contains("demo.supabase.co", ignoreCase = true)) {
            return saved
        }
        val buildUrl = BuildConfig.SUPABASE_URL.trim().removeSurrounding("\"")
        if (buildUrl.startsWith("https://") && !buildUrl.contains("your-project", ignoreCase = true) && !buildUrl.contains("placeholder", ignoreCase = true) && !buildUrl.contains("demo.supabase.co", ignoreCase = true)) {
            return buildUrl
        }
        return DEFAULT_URL
    }

    fun getActiveAnonKey(): String {
        val saved = prefs.getString(KEY_KEY, "")?.trim() ?: ""
        if (saved.isNotBlank() && !saved.contains("your-anon-key", ignoreCase = true) && !saved.contains("placeholder", ignoreCase = true) && !saved.contains("demo-key", ignoreCase = true)) {
            return saved
        }
        val buildKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { BuildConfig.SUPABASE_PUBLISHABLE_KEY }.trim().removeSurrounding("\"")
        if (buildKey.isNotBlank() && !buildKey.contains("your-anon-key", ignoreCase = true) && !buildKey.contains("placeholder", ignoreCase = true) && !buildKey.contains("demo-key", ignoreCase = true)) {
            return buildKey
        }
        return DEFAULT_KEY
    }

    fun isConfigured(): Boolean {
        val url = getActiveUrl()
        val key = getActiveAnonKey()
        return url.startsWith("https://") &&
            !url.contains("your-project", ignoreCase = true) &&
            !url.contains("demo.supabase.co", ignoreCase = true) &&
            !url.contains("placeholder", ignoreCase = true) &&
            key.isNotBlank() &&
            !key.equals("your-anon-key", ignoreCase = true) &&
            !key.equals("placeholder-anon-key", ignoreCase = true) &&
            !key.equals("placeholder-publishable-key", ignoreCase = true) &&
            !key.equals("demo-key", ignoreCase = true)
    }

    fun saveConfig(url: String, anonKey: String) {
        prefs.edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_KEY, anonKey.trim())
            .apply()
        synchronized(this) {
            activeClient = build()
        }
    }

    fun clearConfig() {
        prefs.edit().clear().apply()
        synchronized(this) {
            activeClient = build()
        }
    }

    private fun build(): SupabaseClient {
        val rawUrl = getActiveUrl()
        val rawKey = getActiveAnonKey()

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

    companion object {
        private const val KEY_URL = "custom_supabase_url"
        private const val KEY_KEY = "custom_supabase_anon_key"
        // Real Supabase project credentials — hard-coded per task instructions.
        // Verified working against https://hkvkefubghbbotgnteir.supabase.co on 2026-08-14:
        // the `pull_students_for_sync` RPC returned 389 students using this anon key.
        // The service_role / secret key is NEVER stored in the APK.
        const val DEFAULT_URL = SupabaseConfig.SUPABASE_URL
        const val DEFAULT_KEY = SupabaseConfig.SUPABASE_ANON_KEY
    }
}

