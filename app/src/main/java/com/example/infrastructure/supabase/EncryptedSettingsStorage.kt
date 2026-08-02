package com.example.infrastructure.supabase

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.russhwolf.settings.Settings
import com.russhwolf.settings.SharedPreferencesSettings
import io.github.jan.supabase.auth.SettingsSessionManager
import kotlinx.serialization.json.Json

/**
 * Persistent session storage for the Supabase Auth plugin.
 *
 * The Supabase Kotlin SDK 3.x Auth plugin uses a [SettingsSessionManager]
 * (backed by `com.russhwolf.settings.Settings`) to persist the user's
 * `UserSession` (access token, refresh token, expires-at, user id) across
 * app cold-starts. Without persistent storage, the Auth plugin falls back
 * to in-memory storage and the user is forced to re-login every time the
 * app process is killed.
 *
 * We back the [Settings] with [EncryptedSharedPreferences] so the JWT
 * refresh token is encrypted at rest using AndroidKeystore-backed
 * AES-256-GCM. Falls back to plain `MODE_PRIVATE` SharedPreferences if
 * the Android Keystore is unavailable (rare — only on broken/emulator
 * images).
 *
 * BUGFIX (iter 2 → iter 3): the previous iteration referenced
 * `io.github.jan.supabase.auth.settings.SettingsStorage`, which does NOT
 * exist in Supabase SDK 3.1.1. The correct API is
 * [SettingsSessionManager] (in `io.github.jan.supabase.auth`), which
 * wraps a `com.russhwolf.settings.Settings` instance. This class now
 * produces a properly-configured [SettingsSessionManager] ready to be
 * passed to `AuthConfig.sessionManager`.
 */
object EncryptedSettingsStorage {

    private const val PREFS_NAME = "el-imtiyaz-auth"
    private const val SESSION_KEY = "el_imtiyaz.auth.session"

    /**
     * Build a [SettingsSessionManager] backed by encrypted
     * [SharedPreferences]. The [Json] serializer is configured to match
     * the Supabase SDK's expectations (ignore unknown keys, encode
     * defaults).
     */
    fun createSessionManager(context: Context): SettingsSessionManager {
        val prefs = createEncryptedPrefs(context)
        val settings: Settings = SharedPreferencesSettings(prefs)
        val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            isLenient = true
        }
        return SettingsSessionManager(
            settings = settings,
            key = SESSION_KEY,
            json = json,
        )
    }

    /**
     * Clear all stored auth state — called on signOut().
     */
    fun clear(context: Context) {
        createEncryptedPrefs(context).edit().clear().apply()
    }

    private fun createEncryptedPrefs(context: Context): SharedPreferences =
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            // Keystore unavailable — fall back to plain SharedPreferences.
            // This is rare (only on broken emulator images) but ensures
            // the app doesn't crash at startup when Keystore fails.
            context.getSharedPreferences("${PREFS_NAME}-fallback", Context.MODE_PRIVATE)
        }
}
