package com.example.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.core.Session

/**
 * CompositionLocal for the current session.
 *
 * Provided at the app root by [AppNavHost]. UI components read it for
 * RBAC checks ([com.example.core.FeatureGate.evaluate]) and tenant-aware
 * display. Returns null when the user is not signed in (auth gate state).
 */
val LocalSession = staticCompositionLocalOf<Session?> { null }

/**
 * CompositionLocal for the current tenant context (display name, locale,
 * currency, timezone). Loaded once at sign-in from the `tenants` table.
 *
 * Used for topbar display + locale/currency formatting. Not used for
 * data filtering — RLS enforces tenant isolation server-side.
 */
data class TenantContext(
    val tenantId: String,
    val displayName: String,
    val locale: String,
    val currency: String,
    val timezone: String,
)

val LocalTenantContext = staticCompositionLocalOf<TenantContext?> { null }
