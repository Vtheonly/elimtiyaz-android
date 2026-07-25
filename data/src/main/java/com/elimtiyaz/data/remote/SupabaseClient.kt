package com.elimtiyaz.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Builder for the platform-wide [SupabaseClient]. Kept in a separate file from
 * [com.elimtiyaz.data.di.NetworkModule] so it can be unit-tested without Hilt.
 *
 * The client installs all five Supabase modules per §3 of the architecture
 * doc: `postgrest` for tables, `auth` for sign-in/JWT, `realtime` for live
 * attendance/payment updates, `storage` for the media vault, and `functions`
 * for Edge Functions (DAG triggers, AI, audit insertion).
 */
object SupabaseClientFactory {

    /**
     * Construct a [SupabaseClient] pointing at the given project URL + anon key.
     *
     * @param supabaseUrl  Project URL e.g. `https://xyz.supabase.co`.
     * @param supabaseKey  Anon public key.
     * @param httpClient   Optional custom Ktor client (defaults to a CIO-based
     *                     client with JSON content-negotiation + logging).
     * @return a configured [SupabaseClient]. Never null.
     */
    fun create(
        supabaseUrl: String,
        supabaseKey: String,
        httpClient: HttpClient = defaultHttpClient(),
    ): SupabaseClient = createSupabaseClient(
        supabaseUrl = supabaseUrl,
        supabaseKey = supabaseKey,
    ) {
        install(Postgrest)
        install(Auth)
        install(Realtime)
        install(Storage)
        install(Functions)
    }

    /** Build the default Ktor [HttpClient] used by all Supabase modules. */
    fun defaultHttpClient(json: Json = defaultJson()): HttpClient = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        install(Logging) { level = LogLevel.HEADERS }
    }

    /** Build the default [Json] parser: lenient, ignoring unknown keys. */
    fun defaultJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }
}
