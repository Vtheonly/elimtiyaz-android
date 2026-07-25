package com.elimtiyaz.data.di

import com.elimtiyaz.data.BuildConfig
import com.elimtiyaz.data.remote.SupabaseClientFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.ktor.client.HttpClient
import kotlinx.serialization.json.Json
import javax.inject.Named
import javax.inject.Singleton

/**
 * Hilt module that wires the network layer:
 * - The kotlinx.serialization [Json] parser (lenient, shared by all DTOs).
 * - A Ktor [HttpClient] with CIO engine + content negotiation + logging.
 * - The [SupabaseClient] built from `BuildConfig.SUPABASE_URL` /
 *   `BuildConfig.SUPABASE_ANON_KEY` (populated at build time from
 *   `local.properties`).
 * - The `"isMockMode"` flag that switches every repository between the mock
 *   and real implementations.
 *
 * When Supabase keys are absent, `isMockMode = true` and the Supabase client
 * is *not* constructed — the app runs fully offline against the in-memory
 * mock seed data.
 */
@Module
@InstallIn(SingletonComponent::class)
@Suppress("unused")
object NetworkModule {

    /** Forced to true to run in offline/mock mode with preset credentials, skipping the database. */
    @Provides
    @Named("isMockMode")
    @Singleton
    fun provideIsMockMode(): Boolean = true

    /** Build the shared [Json] parser used by DTOs and the sync queue. */
    @Provides
    @Singleton
    fun provideJson(): Json = SupabaseClientFactory.defaultJson()

    /** Build the Ktor [HttpClient] shared by all Supabase modules. */
    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = SupabaseClientFactory.defaultHttpClient(json)

    /**
     * Build the [SupabaseClient]. In mock mode (no keys) we still construct a
     * client pointing at a sentinel URL — it's never actually called because
     * `DataModule` routes every repository to its mock implementation, but
     * Hilt still needs the binding to satisfy the real-impl constructor.
     */
    @Provides
    @Singleton
    fun provideSupabaseClient(httpClient: HttpClient): SupabaseClient {
        val url = BuildConfig.SUPABASE_URL.ifBlank { "https://placeholder.supabase.co" }
        val key = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "placeholder-anon-key" }
        return SupabaseClientFactory.create(supabaseUrl = url, supabaseKey = key, httpClient = httpClient)
    }
}
