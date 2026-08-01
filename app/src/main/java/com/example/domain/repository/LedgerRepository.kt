package com.example.domain.repository

import com.example.core.LedgerEntry
import com.example.core.ParentLedgerSummary
import com.example.core.Result
import com.example.core.Reconcile
import kotlinx.coroutines.flow.Flow

/** Ledger repository contract — append-only financial journal. */
interface LedgerRepository {
    fun observe(): Flow<List<LedgerEntry>>
    fun observeByParent(parentId: String): Flow<List<LedgerEntry>>
    fun observeByAccount(accountId: String): Flow<List<LedgerEntry>>
    suspend fun append(entry: LedgerEntry): Result<LedgerEntry>
    suspend fun appendMany(entries: List<LedgerEntry>): Result<List<LedgerEntry>>
    suspend fun reverse(originalId: String, reason: String, actorId: String, actorName: String): Result<LedgerEntry>
    suspend fun summary(parentId: String): Result<ParentLedgerSummary>
    suspend fun reconcile(): Result<Reconcile.Report>
}
