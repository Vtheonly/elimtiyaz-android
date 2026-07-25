package com.elimtiyaz.core.rbac

/**
 * Optional non-RBAC feature flag provider. Implementations read from
 * a remote config, paid-plan status, or local build flags.
 *
 * The default implementation ([NoOpFeatureFlagProvider]) returns `true` for
 * every flag — i.e. all feature flags are considered ON unless explicitly
 * overridden. This keeps the gating logic opt-in: existing screens that
 * don't use feature flags are unaffected.
 *
 * Bind a real implementation via Hilt when remote config is wired up.
 */
interface FeatureFlagProvider {
    fun isEnabled(flag: String): Boolean
}

/** Default — every flag is ON. */
object NoOpFeatureFlagProvider : FeatureFlagProvider {
    override fun isEnabled(flag: String): Boolean = true
}
