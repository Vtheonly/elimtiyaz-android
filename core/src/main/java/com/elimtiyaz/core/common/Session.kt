package com.elimtiyaz.core.common

/**
 * The authenticated session. Held in memory by the AuthRepository and
 * observed by the UI via StateFlow. Persisted to DataStore for warm-start.
 *
 * Permissions are precomputed at sign-in so the UI never has to wait on
 * RBAC checks. Refresh them only on role switch.
 */
data class Session(
    val userId: String,
    val tenantId: String,
    val email: String,
    val displayName: String,
    val avatarUrl: String? = null,
    val role: Role,
    val permissions: Set<Permission>,
    val accessToken: String,
    val refreshToken: String? = null,
    val expiresAt: Long,             // epoch millis
    val locale: String = "fr",
) {
    fun can(permission: Permission): Boolean = permission in permissions
    fun hasRole(role: Role): Boolean = this.role == role
    fun hasAnyRole(vararg roles: Role): Boolean = role in roles
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt - 60_000

    companion object {
        /** Convenience for the redirect-to-web-portal screen when parent/student logs in. */
        val WebPortalOnly: Set<Permission> = emptySet()
    }
}
