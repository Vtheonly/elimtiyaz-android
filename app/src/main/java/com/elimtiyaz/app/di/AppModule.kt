package com.elimtiyaz.app.di

import com.elimtiyaz.core.common.DispatcherProvider
import com.elimtiyaz.core.common.DefaultDispatcherProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-level DI bindings that don't belong to :data or :feature.
 * Currently only the dispatcher provider, but reserved for future
 * app-scoped singletons (FCM token manager, etc.).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()
}
