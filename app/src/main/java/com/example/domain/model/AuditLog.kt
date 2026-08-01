package com.example.domain.model

import kotlinx.serialization.Serializable

/**
 * Audit log entry — every mutation is recorded here server-side via the
 * `write_audit_log` RPC. Used for compliance, forensics, and the audit UI.
 */
@Serializable
data class AuditLog(
    val id: String,
    val tenantId: String,
    val action: String,
    val entityType: String,
    val entityId: String,
    val actorId: String,
    val actorName: String,
    val actorRole: String? = null,
    val beforeJson: String? = null,
    val afterJson: String? = null,
    val note: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val occurredAt: String,
)
