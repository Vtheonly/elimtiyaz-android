package com.elimtiyaz.data.di

import com.elimtiyaz.core.common.DefaultDispatcherProvider
import com.elimtiyaz.core.common.DispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Provides the [DispatcherProvider] abstraction over [kotlinx.coroutines.Dispatchers]
 * so tests can swap dispatchers without touching production code.
 */
@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    /** Provide the default [DispatcherProvider]. */
    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
