package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.ReleveEntry
import kotlinx.coroutines.flow.Flow

/**
 * Relevé (clock-in/out ledger) repository.
 *
 * Per desktop plan §09.05:
 * - Append-only (no UPDATE/DELETE on `releve_entries`).
 * - A teacher CANNOT record their own Relevé entry (server trigger `prevent_self_releve_entry`).
 * - `durationMinutes` is computed server-side as a GENERATED column.
 *
 * The mobile app uses this for the ReleveScreen (clock-in/out form) and
 * PersonnelDetailScreen (weekly hours compliance).
 */
interface ReleveRepository {

    /**
     * Observe Relevé entries for a staff member over a date range (inclusive).
     *
     * @param personnelId The staff member's id.
     * @param fromIso ISO date (`yyyy-MM-dd`) — inclusive lower bound.
     * @param toIso ISO date (`yyyy-MM-dd`) — inclusive upper bound.
     */
    fun observeByPersonnel(
        personnelId: String,
        fromIso: String,
        toIso: String,
    ): Flow<Result<List<ReleveEntry>>>

    /**
     * Append a new Relevé entry.
     *
     * @return The created entry (with server-assigned id + computed `durationMinutes`).
     */
    suspend fun logEntry(
        entry: ReleveEntry,
        actorId: String,
        actorName: String,
    ): Result<ReleveEntry>
}
