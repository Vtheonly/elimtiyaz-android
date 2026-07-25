package com.elimtiyaz.core.rbac

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.elimtiyaz.core.common.Session

/**
 * Composition-Local plumbing for the gating system.
 *
 * - [LocalSession] provides the current [Session] (NULL when logged out).
 * - [LocalFeatureFlagProvider] provides the [FeatureFlagProvider] (defaults to
 *   [NoOpFeatureFlagProvider]).
 *
 * The app's root composable (in :app) installs both via [androidx.compose.runtime.CompositionLocalProvider].
 * Screens then call [gated] / [gatedOrNull] / [accessStateOf] which read these
 * locals automatically — no Hilt injection needed inside composables.
 */
val LocalSession = compositionLocalOf<Session?> { null }

val LocalFeatureFlagProvider = staticCompositionLocalOf<FeatureFlagProvider> { NoOpFeatureFlagProvider }

/**
 * Evaluate a [FeatureNode] against the current composition locals.
 *
 * Use this inside composables when you need to know the access state of a node.
 * Returns [AccessState.Enabled] by default when no session is installed (so
 * preview-friendly).
 */
@Composable
fun accessStateOf(node: FeatureNode): AccessState {
    val session = LocalSession.current
    val flags = LocalFeatureFlagProvider.current
    return FeatureGate.evaluate(node, session, flags)
}

/**
 * Evaluate a raw [AccessRequirement] against the current composition locals.
 */
@Composable
fun accessStateOf(requirement: AccessRequirement, hideWhenUnauthenticated: Boolean = false): AccessState {
    val session = LocalSession.current
    val flags = LocalFeatureFlagProvider.current
    return FeatureGate.evaluateRequirement(requirement, session, flags, hideWhenUnauthenticated)
}

/**
 * Returns TRUE when the node is accessible given the current session.
 * Convenience wrapper for `accessStateOf(node) is AccessState.Enabled`.
 */
@Composable
fun isAccessible(node: FeatureNode): Boolean =
    accessStateOf(node) is AccessState.Enabled

/**
 * Returns TRUE when the requirement is met given the current session.
 */
@Composable
fun isRequirementMet(requirement: AccessRequirement): Boolean =
    accessStateOf(requirement) is AccessState.Enabled
