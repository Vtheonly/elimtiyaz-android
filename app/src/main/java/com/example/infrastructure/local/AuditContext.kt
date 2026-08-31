package com.example.infrastructure.local

import com.example.infrastructure.room.AuditLogEntity
import com.example.session.SessionManager
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Session-aware audit + tenant context (T-051 / WEAK-011 + TENANT-104).
 *
 * WHY THIS EXISTS: the two file-private `audit()` / `auditLog()` helpers in
 * LocalRepositories.kt / LocalRepositories2.kt hardcoded the DEMO tenant
 * UUID into EVERY audit row and never captured `actorRole` — making
 * multi-tenant deployments impossible and role-based audit queries empty.
 * Entity constructors in the local repositories hardcoded the demo tenant
 * the same way (TENANT-104).
 *
 * This class is the single session-aware source for BOTH values:
 *  - [tenantId] — the signed-in user's real tenant; falls back to the demo
 *    tenant ONLY when no session exists (first-launch seeding / dev mode —
 *    the same situation DatabaseSeeder legitimately seeds for).
 *  - [actorRole] — the session's role code (desktop BUSINESS-003 parity),
 *    or null when no session is active.
 *
 * The repositories inject this and build audit rows / stamp entity tenants
 * through it, replacing the hardcoded values. `audit()` / `auditLog()` keep
 * the exact parameter shape of the helpers they replace so the call sites
 * only gain the receiver.
 */
@Singleton
class AuditContext @Inject constructor(
    // dagger.Lazy BREAKS the construction cycle: LocalAuthRepository needs
    // AuditContext, while SessionManager needs the bound AuthRepository (=
    // LocalAuthRepository). SessionManager is only resolved on first use.
    private val sessionManager: dagger.Lazy<SessionManager>,
) {
    /** The session's real tenant, or the demo tenant when no session exists. */
    fun tenantId(): String = sessionManager.get().currentTenantId() ?: DEMO_TENANT_ID

    /** The session's primary role code, or null when not signed in. */
    fun actorRole(): String? = sessionManager.get().current()?.role?.code

    /** Session-aware audit row (replaces the file-private `audit` helper). */
    fun audit(
        action: String,
        entityType: String,
        entityId: String,
        actorId: String,
        actorName: String,
        after: String? = null,
    ) = AuditLogEntity(
        id = "aud-${UUID.randomUUID()}",
        tenantId = tenantId(),
        action = action,
        entityType = entityType,
        entityId = entityId,
        actorId = actorId,
        actorName = actorName,
        // T-051/WEAK-011 — capture the actor's role (was always null).
        actorRole = actorRole(),
        beforeJson = null,
        afterJson = after,
        note = null,
        createdAt = Instant.now().toString(),
    )

    /** Identical contract kept under the LocalRepositories2 helper's name. */
    fun auditLog(
        action: String,
        entityType: String,
        entityId: String,
        actorId: String,
        actorName: String,
        after: String? = null,
    ) = audit(action, entityType, entityId, actorId, actorName, after)

    companion object {
        /** The seeding/demo tenant (DatabaseSeeder's target) — fallback ONLY for the signed-out state. */
        const val DEMO_TENANT_ID = "00000000-0000-0000-0000-000000000001"
    }
}
