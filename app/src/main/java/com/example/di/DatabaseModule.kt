package com.example.di

import android.content.Context
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

/**
 * Database + WorkManager DI module.
 *
 * Room is used as an offline cache + sync queue (NOT the primary store —
 * Supabase is the source of truth). The database mirrors the Supabase
 * schema for cached reads and includes a `sync_queue` table for offline
 * writes.
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
}
