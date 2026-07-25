package com.elimtiyaz.data.di

import android.content.Context
import com.elimtiyaz.data.repository.SettingsRepositoryImpl
import com.elimtiyaz.domain.repository.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the DataStore-backed [SettingsRepository]. The same implementation
 * is used in both mock and real mode — settings are a local-only concern and
 * never touch Supabase.
 */
@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    /** Provide the [SettingsRepository] singleton. */
    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepositoryImpl(context)
}
