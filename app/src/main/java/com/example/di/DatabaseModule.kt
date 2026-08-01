package com.example.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.WorkManager
import com.example.infrastructure.room.ElImtiyazDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Database + WorkManager + DataStore DI module.
 *
 * Room is used as an offline cache + sync queue (NOT the primary store —
 * Supabase is the source of truth). The database mirrors the Supabase
 * schema for cached reads and includes a `sync_queue` table for offline
 * writes.
 *
 * DataStore<Preferences> is the singleton-backed key-value store used by
 * the Settings screen for dark-mode / language / notification toggles.
 * The producer scope uses a SupervisorJob so a write failure doesn't
 * cancel the underlying data store.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ElImtiyazDatabase =
        Room.databaseBuilder(context, ElImtiyazDatabase::class.java, "el_imtiyaz.db")
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides @Singleton
    fun provideParentCacheDao(db: ElImtiyazDatabase) = db.parentCacheDao()

    @Provides @Singleton
    fun provideStudentCacheDao(db: ElImtiyazDatabase) = db.studentCacheDao()

    @Provides @Singleton
    fun providePaymentCacheDao(db: ElImtiyazDatabase) = db.paymentCacheDao()

    @Provides @Singleton
    fun provideLedgerCacheDao(db: ElImtiyazDatabase) = db.ledgerCacheDao()

    @Provides @Singleton
    fun provideSyncQueueDao(db: ElImtiyazDatabase) = db.syncQueueDao()

    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    /**
     * Provide the singleton [DataStore<Preferences>] for app settings.
     *
     * The store is created via [PreferenceDataStoreFactory.create] with a
     * dedicated file (`el_imtiyaz_settings.preferences_pb`) and a
     * supervisor-scoped coroutine on [Dispatchers.IO] so I/O failures
     * don't crash the app.
     */
    @Provides @Singleton
    fun provideSettingsDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        produceFile = { java.io.File(context.filesDir, "datastore/el_imtiyaz_settings.preferences_pb") },
    )
}

