package com.example.infrastructure.room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database — offline cache + sync queue.
 *
 * Versioned at 1. Future schema changes MUST ship with a Migration object
 * (see [com.example.di.DatabaseModule]). `exportSchema = true` would dump
 * a JSON schema for migration validation — enable in production.
 *
 * The database is intentionally separate from the Supabase schema:
 *   - Cache entities mirror Supabase tables but add `syncedAt` for staleness checks.
 *   - The sync_queue table has no Supabase equivalent (it's mobile-only).
 */
@Database(
    entities = [
        ParentCacheEntity::class,
        StudentCacheEntity::class,
        PaymentCacheEntity::class,
        LedgerCacheEntity::class,
        SyncQueueEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class ElImtiyazDatabase : RoomDatabase() {
    abstract fun parentCacheDao(): ParentCacheDao
    abstract fun studentCacheDao(): StudentCacheDao
    abstract fun paymentCacheDao(): PaymentCacheDao
    abstract fun ledgerCacheDao(): LedgerCacheDao
    abstract fun syncQueueDao(): SyncQueueDao
}
