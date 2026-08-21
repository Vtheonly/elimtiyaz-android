package com.example.domain.repository

import com.example.core.Result
import com.example.domain.model.AuditLog
import kotlinx.coroutines.flow.Flow

/** Audit log repository contract. */
interface AuditRepository {
    fun observe(limit: Int = 100): Flow<List<AuditLog>>
    fun observeByEntity(entityType: String, entityId: String): Flow<List<AuditLog>>
    suspend fun query(filter: AuditFilter): Result<List<AuditLog>>
    suspend fun log(input: AuditLogInput): Result<AuditLog>
}

/** Filter for [AuditRepository.query]. */
data class AuditFilter(
    val action: String? = null, val entityType: String? = null,
    val entityId: String? = null, val actorId: String? = null,
    val from: String? = null, val to: String? = null,
    val limit: Int = 100, val offset: Int = 0,
)

/** Input payload for [AuditRepository.log]. */
data class AuditLogInput(
    val action: String, val entityType: String, val entityId: String,
    val beforeJson: String? = null, val afterJson: String? = null,
    val note: String? = null,
    // TIER 3 R19 FIX: previously the audit log always recorded `actorId = "system"`
    // even when the caller knew the real actor (the logged-in user). This made
    // the audit trail useless for accountability — every action looked like it
    // was performed by the system. The actor fields are now optional on the
    // input; when the caller omits them, the repository falls back to "system".
    val actorId: String? = null,
    val actorName: String? = null,
    val actorRole: String? = null,
)
