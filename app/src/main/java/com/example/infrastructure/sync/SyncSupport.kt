package com.example.infrastructure.sync

import com.example.core.AppError
import com.example.core.Errors
import com.example.core.Result
import com.example.infrastructure.room.ParentCacheDao
import com.example.infrastructure.room.PaymentCacheDao
import com.example.infrastructure.room.StudentCacheDao
import com.example.infrastructure.room.LedgerCacheEntity
import com.example.infrastructure.room.LedgerCacheDao
import com.example.infrastructure.room.ParentCacheEntity
import com.example.infrastructure.room.PaymentCacheEntity
import com.example.infrastructure.room.StudentCacheEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json

/**
 * Helper for offline-first repository implementations.
 *
 * Two patterns are supported:
 *
 * 1. **cache-then-network** for reads — emit the cached rows immediately,
 *    then fetch from Supabase, write to cache, and emit again. Falls back
 *    to cache-only when offline.
 *
 * 2. **try-then-enqueue** for writes — attempt the direct Supabase call;
 *    on network/offline failure, enqueue the mutation to [SyncService]
 *    and return [Result.Err] with [AppError.CODE_OFFLINE] so the UI can
 *    surface a "queued for sync" message.
 *
 * Mirrors the desktop's `cache-then-network` + `useSyncActions().enqueue`
 * patterns (see desktop-reference-summary.md §9 Sync model).
 */
@Singleton
class SyncSupport @Inject constructor(
    private val syncService: SyncService,
    private val onlineDetector: OnlineDetector,
    private val parentCacheDao: ParentCacheDao,
    private val studentCacheDao: StudentCacheDao,
    private val paymentCacheDao: PaymentCacheDao,
    private val ledgerCacheDao: LedgerCacheDao,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    // ── Reads: cache-then-network ───────────────────────────────────────

    /**
     * Emit cached rows immediately, then fetch from network, update cache,
     * and emit the network result. If the network fails, the cache remains
     * the source of truth.
     *
     * The [fetch] lambda is invoked on [Dispatchers.IO] by the underlying flow.
     */
    fun <T : Any> cacheThenNetwork(
        cacheRead: suspend () -> List<T>,
        cacheWrite: suspend (List<T>) -> Unit,
        fetch: suspend () -> List<T>,
    ): Flow<List<T>> = flow {
        // 1. Emit cached value first (instant UI)
        val cached = runCatching { cacheRead() }.getOrDefault(emptyList())
        emit(cached)

        // 2. If offline, stop here — cache is the source of truth.
        if (!onlineDetector.isOnline()) return@flow

        // 3. Fetch from network
        val fresh = runCatching { fetch() }.getOrNull() ?: return@flow

        // 4. Write to cache (best-effort)
        runCatching { cacheWrite(fresh) }

        // 5. Emit fresh value
        emit(fresh)
    }

    /** Single-item variant of [cacheThenNetwork]. */
    fun <T : Any> cacheThenNetworkSingle(
        cacheRead: suspend () -> T?,
        cacheWrite: suspend (T) -> Unit,
        fetch: suspend () -> T?,
    ): Flow<T?> = flow {
        val cached = runCatching { cacheRead() }.getOrNull()
        emit(cached)
        if (!onlineDetector.isOnline()) return@flow
        val fresh = runCatching { fetch() }.getOrNull() ?: return@flow
        if (fresh != null) runCatching { cacheWrite(fresh) }
        emit(fresh)
    }

    // ── Writes: try-then-enqueue ────────────────────────────────────────

    /**
     * Enqueue a mutation for later sync push, WITHOUT trying a network call
     * first. Use this when the local Room write already happened (the local
     * database is the source of truth) and we just need to enqueue the same
     * operation for the Supabase push side of the cycle.
     *
     * CANONICAL-FINANCIAL-LOGIC.md §8.1 — the canonical sync pattern:
     *   1. Local write to Room (synchronous, source of truth).
     *   2. Enqueue the same operation to the sync queue.
     *   3. SyncWorker drains the queue in the background → calls upsert RPC.
     *
     * Returns the queue entry ID on success, null on failure (best-effort).
     */
    suspend fun enqueueOnly(
        entity: String,
        operation: String,
        payload: String,
        isMock: Boolean = false,
        sourceScreen: String? = null,
    ): String? = runCatching {
        syncService.enqueue(
            entity = entity, operation = operation, payload = payload,
            isMock = isMock, sourceScreen = sourceScreen,
        )
    }.getOrNull()

    /**
     * Attempt [mutation]. If it throws a network/offline error AND the
     * device is currently offline, enqueue the operation to [SyncService]
     * and return [Result.Err] with [AppError.CODE_OFFLINE] so the UI can
     * show "queued for sync".
     *
     * For online errors (validation, server, etc.) the original error is
     * returned without enqueuing.
     *
     * @param entity Sync entity type ("parent", "student", "payment", ...)
     * @param operation Operation type ("create", "update", "delete", ...)
     * @param payload JSON-serializable mutation descriptor (used by
     *                [SupabaseSyncDao.pushXxx] during drain).
     * @param isMock True when the mutation is on demo/mock data — flagged
     *               so [SyncWorker] skips it (defense in depth).
     * @param sourceScreen Optional screen tag for debugging.
     * @param mutation The actual Supabase call to attempt.
     */
    suspend fun <T : Any> tryThenEnqueue(
        entity: String,
        operation: String,
        payload: () -> String,
        isMock: Boolean = false,
        sourceScreen: String? = null,
        mutation: suspend () -> T,
    ): Result<T> {
        return try {
            Result.Ok(mutation())
        } catch (e: Exception) {
            val error = Errors.fromException(e)
            if (error.code == Errors.CODE_NETWORK || error.code == Errors.CODE_OFFLINE
                || error.code == Errors.CODE_TIMEOUT
                || !onlineDetector.isOnline()
            ) {
                // Offline — enqueue for later sync
                runCatching {
                    syncService.enqueue(
                        entity = entity,
                        operation = operation,
                        payload = payload(),
                        isMock = isMock,
                        sourceScreen = sourceScreen,
                    )
                }
                Result.Err(Errors.offline(
                    "Mutation mise en file d'attente — sera synchronisée quand la connexion reviendra.",
                ))
            } else {
                Result.Err(error)
            }
        }
    }

    // ── Cache DAO accessors (for repositories that don't inject DAOs directly) ──

    suspend fun upsertParents(rows: List<ParentCacheEntity>) = runCatching { parentCacheDao.upsertAll(rows) }
    suspend fun upsertStudents(rows: List<StudentCacheEntity>) = runCatching { studentCacheDao.upsertAll(rows) }
    suspend fun upsertPayments(rows: List<PaymentCacheEntity>) = runCatching { paymentCacheDao.upsertAll(rows) }
    suspend fun upsertLedger(rows: List<LedgerCacheEntity>) = runCatching { ledgerCacheDao.upsertAll(rows) }

    suspend fun listCachedParents(): List<ParentCacheEntity> = runCatching { parentCacheDao.listAll() }.getOrDefault(emptyList())
    suspend fun listCachedStudents(): List<StudentCacheEntity> = runCatching { studentCacheDao.listAll() }.getOrDefault(emptyList())
    suspend fun listCachedPayments(): List<PaymentCacheEntity> = runCatching { paymentCacheDao.listAll() }.getOrDefault(emptyList())
    suspend fun listCachedLedger(): List<LedgerCacheEntity> = runCatching { ledgerCacheDao.listAll() }.getOrDefault(emptyList())

    suspend fun getCachedParent(id: String): ParentCacheEntity? = runCatching { parentCacheDao.getById(id) }.getOrNull()
    suspend fun getCachedStudent(id: String): StudentCacheEntity? = runCatching { studentCacheDao.getById(id) }.getOrNull()
    suspend fun getCachedPayment(id: String): PaymentCacheEntity? = runCatching { paymentCacheDao.getById(id) }.getOrNull()

    fun observeCachedParents(): Flow<List<ParentCacheEntity>> = parentCacheDao.observeAll()
    fun observeCachedStudents(): Flow<List<StudentCacheEntity>> = studentCacheDao.observeAll()
    fun observeCachedPayments(): Flow<List<PaymentCacheEntity>> = paymentCacheDao.observeAll()
    fun observeCachedLedger(): Flow<List<LedgerCacheEntity>> = ledgerCacheDao.observeAll()

    /** JSON serializer for payload encoding (repositories can reuse). */
    fun json(): Json = json
}
