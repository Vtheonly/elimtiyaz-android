package com.example.infrastructure.supabase

import android.content.SharedPreferences
import io.github.jan.supabase.auth.settings.SettingsStorage

/**
 * [SettingsStorage] backed by [SharedPreferences] (provided as encrypted
 * SharedPreferences in [com.example.di.SupabaseModule]).
 *
 * The Supabase Auth plugin uses this to persist the user's JWT refresh
 * token across app cold-starts. Without persistent storage, the auth
 * plugin falls back to in-memory storage and the user is forced to
 * re-login every time the app process is killed.
 *
 * The keys mirror the default SettingsStorage implementation
 * (`supabase.auth.tokens`, `supabase.auth.last_refresh`) but are namespaced
 * under `el_imtiyaz.auth.*` to avoid collisions with other Supabase
 * clients that might share the same SharedPreferences file.
 *
 * BUGFIX (iter 2): the previous iteration referenced `SettingsStorage` in
 * the SupabaseModule KDoc but never provided an implementation, so the
 * Auth plugin used in-memory storage. This class closes that gap.
 */
class EncryptedSettingsStorage(
    private val prefs: SharedPreferences,
) : SettingsStorage {

    private val keyAccessToken = "el_imtiyaz.auth.access_token"
    private val keyRefreshToken = "el_imtiyaz.auth.refresh_token"
    private val keyExpiresAt = "el_imtiyaz.auth.expires_at"
    private val keyTokenType = "el_imtiyaz.auth.token_type"
    private val keyUserId = "el_imtiyaz.auth.user_id"

    override fun setAccessToken(token: String?) {
        prefs.edit().apply {
            if (token == null) remove(keyAccessToken) else putString(keyAccessToken, token)
        }.apply()
    }

    override fun getAccessToken(): String? = prefs.getString(keyAccessToken, null)

    override fun setRefreshToken(token: String?) {
        prefs.edit().apply {
            if (token == null) remove(keyRefreshToken) else putString(keyRefreshToken, token)
        }.apply()
    }

    override fun getRefreshToken(): String? = prefs.getString(keyRefreshToken, null)

    override fun setExpiresAt(expiresAt: Long?) {
        prefs.edit().apply {
            if (expiresAt == null) remove(keyExpiresAt) else putLong(keyExpiresAt, expiresAt)
        }.apply()
    }

    override fun getExpiresAt(): Long? = if (prefs.contains(keyExpiresAt)) {
        prefs.getLong(keyExpiresAt, 0L)
    } else null

    override fun setTokenType(tokenType: String?) {
        prefs.edit().apply {
            if (tokenType == null) remove(keyTokenType) else putString(keyTokenType, tokenType)
        }.apply()
    }

    override fun getTokenType(): String? = prefs.getString(keyTokenType, null)

    override fun setUserId(userId: String?) {
        prefs.edit().apply {
            if (userId == null) remove(keyUserId) else putString(keyUserId, userId)
        }.apply()
    }

    override fun getUserId(): String? = prefs.getString(keyUserId, null)

    /** Clear all stored auth state — called on signOut(). */
    fun clear() {
        prefs.edit().apply {
            remove(keyAccessToken)
            remove(keyRefreshToken)
            remove(keyExpiresAt)
            remove(keyTokenType)
            remove(keyUserId)
        }.apply()
    }
}
