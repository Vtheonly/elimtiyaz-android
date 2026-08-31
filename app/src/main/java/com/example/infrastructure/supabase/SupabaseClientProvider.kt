package com.example.infrastructure.supabase

import android.content.Context
import android.util.Log
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
        if (saved.isNotBlank() && !isPlaceholderUrl(saved)) {
            return saved
        }
        val buildUrl = BuildConfig.SUPABASE_URL.trim().removeSurrounding("\"")
        if (buildUrl.startsWith("https://") && !isPlaceholderUrl(buildUrl)) {
            return buildUrl
        }
        // SECURITY FIX — no hardcoded production fallback URL: the credentials
        // MUST come from the runtime override (SharedPreferences) or from
        // BuildConfig (`.env` via the secrets plugin). Surface a clear error
        // instead of silently connecting to a committed project URL.
        Log.e(
            TAG,
            "Supabase URL non configurée — définissez SUPABASE_URL dans le fichier .env " +
                "(voir .env.example) ou saisissez-la dans Paramètres > Supabase.",
        )
        return ""
    }

    fun getActiveAnonKey(): String {
        val saved = prefs.getString(KEY_KEY, "")?.trim() ?: ""
        if (saved.isNotBlank() && !isPlaceholderKey(saved)) {
            return saved
        }
        val buildKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { BuildConfig.SUPABASE_PUBLISHABLE_KEY }.trim().removeSurrounding("\"")
        if (buildKey.isNotBlank() && !isPlaceholderKey(buildKey)) {
            return buildKey
        }
        Log.e(
            TAG,
            "Clé anonyme Supabase non configurée — définissez SUPABASE_ANON_KEY dans le fichier " +
                ".env (voir .env.example) ou saisissez-la dans Paramètres > Supabase.",
        )
        return ""
    }

    fun isConfigured(): Boolean {
        val url = getActiveUrl()
        val key = getActiveAnonKey()
        return url.startsWith("https://") && !isPlaceholderUrl(url) &&
            key.isNotBlank() && !isPlaceholderKey(key)
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

        // SECURITY FIX — never fall back to committed production credentials.
        // T-064 / SEC-005: when the app is unconfigured the client is built
        // against `https://supabase.unconfigured.invalid` — the `.invalid`
        // TLD is reserved by RFC 2606 and can NEVER resolve, so an
        // unconfigured build makes ZERO network calls to any real host
        // (the old `demo.supabase.co` fallback pinged a live third-party
        // endpoint on every cold start). Requests fail loudly (DNS) and
        // `isConfigured()` stays false so the Settings screen can prompt
        // for values.
        if (rawUrl.isBlank() || rawKey.isBlank()) {
            Log.e(TAG, "Construction du client Supabase avec une configuration VIDE — " +
                "les requêtes réseau échoueront jusqu'à ce que les identifiants soient fournis.")
        }

        // SEC-005 (T-064): RFC-2606 reserved, guaranteed-nonexistent host.
        val inertUrl = INERT_FALLBACK_URL
        val validUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            inertUrl
        }

        val validKey = rawKey.ifBlank { INERT_FALLBACK_KEY }

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
            // SEC-005 (T-064): the exception path ALSO builds the inert
            // client — never a public demo endpoint.
            createSupabaseClient(
                supabaseUrl = inertUrl,
                supabaseKey = INERT_FALLBACK_KEY,
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
        private const val TAG = "SupabaseClientProvider"
        private const val KEY_URL = "custom_supabase_url"
        private const val KEY_KEY = "custom_supabase_anon_key"

        /**
         * SEC-005 / T-064 — the SDK requires a non-blank URL to construct a
         * client; this reserved `.invalid` host can never resolve (RFC 2606),
         * so unconfigured builds make ZERO network calls to real hosts.
         * Exposed for tests via reflection-free internal visibility.
         */
        internal const val INERT_FALLBACK_URL = "https://supabase.unconfigured.invalid"
        internal const val INERT_FALLBACK_KEY = "inert-unconfigured-key"

        /** URL obviously meant as a template value (from .env.example). */
        private fun isPlaceholderUrl(url: String): Boolean =
            url.contains("your-project", ignoreCase = true) ||
                url.contains("your_project", ignoreCase = true) ||
                url.contains("placeholder", ignoreCase = true) ||
                url.contains("demo.supabase.co", ignoreCase = true)

        /** Key obviously meant as a template value (from .env.example). */
        private fun isPlaceholderKey(key: String): Boolean =
            key.contains("your-anon-key", ignoreCase = true) ||
                key.contains("your_anon_key", ignoreCase = true) ||
                key.contains("placeholder", ignoreCase = true) ||
                key.equals("demo-key", ignoreCase = true)
    }
}

