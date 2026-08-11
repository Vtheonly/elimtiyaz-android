package com.example.ui.permissions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay

/**
 * Centralized permission helpers.
 *
 * This file fixes the previous "scattered permissions" problem where each
 * screen independently called `rememberLauncherForActivityResult` with no
 * shared handling of:
 *   - the "permanently denied" state (user checked "Don't ask again"),
 *   - re-checking the permission on app re-entry (user might have just
 *     granted it in system settings),
 *   - rationales.
 *
 * The helpers below provide a single, predictable permission flow that
 * every screen SHOULD use instead of rolling its own.
 */

/** The states a runtime permission can be in, from this app's point of view. */
sealed class PermissionState {
    /** We haven't asked yet, or the user dismissed the dialog without deciding. */
    object NotDetermined : PermissionState()

    /** Permission is granted. */
    object Granted : PermissionState()

    /** Permission was denied but NOT permanently — we can ask again. */
    object Denied : PermissionState()

    /** Permission was permanently denied (`shouldShowRequestPermissionRationale` returns false AFTER a denial). */
    object PermanentlyDenied : PermissionState()
}

/**
 * Check the current state of a single permission.
 *
 * `permanentlyDenied` is inferred from the contract:
 *   - If `checkSelfPermission` is GRANTED → Granted.
 *   - If the activity says we SHOULD show a rationale → Denied (can re-ask).
 *   - If neither granted nor should-show-rationale → NotDetermined
 *     (the caller disambiguates "never asked" vs "permanently denied" by
 *     tracking whether we've ever asked).
 */
fun Context.permissionState(permission: String, activity: Activity? = null): PermissionState {
    val granted = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    if (granted) return PermissionState.Granted
    activity?.let {
        if (it.shouldShowRequestPermissionRationale(permission)) {
            return PermissionState.Denied
        }
    }
    return PermissionState.NotDetermined
}

/**
 * Open the system app-details settings screen so the user can manually
 * grant a permanently-denied permission. Used by screens after they detect
 * [PermissionState.PermanentlyDenied].
 */
fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addCategory(Intent.CATEGORY_DEFAULT)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
        addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
    }
    runCatching { startActivity(intent) }
}

/**
 * Compose-side remember for a SINGLE permission.
 *
 * Tracks:
 *   - the current [PermissionState],
 *   - whether we've ever asked the user (persisted across recompositions
 *     via a SharedPreferences flag — survives config changes),
 *   - a [request] callback to fire the system permission dialog,
 *   - an [openSettings] callback to jump to system settings.
 *
 * Usage:
 *   val perm = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
 *   when (perm.state) {
 *       PermissionState.NotDetermined -> LaunchedEffect(Unit) { perm.request() }
 *       PermissionState.Denied -> { /* show rationale + retry button */ }
 *       PermissionState.PermanentlyDenied -> { /* show "open settings" button */ }
 *       PermissionState.Granted -> { /* proceed */ }
 *   }
 */
@Composable
fun rememberPermissionState(
    permission: String,
    onResult: ((PermissionState) -> Unit)? = null,
): RememberedPermissionState {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember {
        context.getSharedPreferences("el_imtiyaz_permissions", Context.MODE_PRIVATE)
    }
    val askedKey = "asked_$permission"

    var state by remember {
        mutableStateOf(context.permissionState(permission, activity))
    }
    var hasAsked by remember {
        mutableStateOf(prefs.getBoolean(askedKey, false))
    }

    // Re-evaluate the state on every recomposition — the user might have
    // just granted the permission in system settings while the app was
    // backgrounded.
    LaunchedEffect(permission) {
        state = context.permissionState(permission, activity)
        if (state is PermissionState.NotDetermined && hasAsked && activity != null) {
            if (!activity.shouldShowRequestPermissionRationale(permission)) {
                state = PermissionState.PermanentlyDenied
            }
        }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        prefs.edit().putBoolean(askedKey, true).apply()
        hasAsked = true
        state = if (granted) {
            PermissionState.Granted
        } else if (activity != null && !activity.shouldShowRequestPermissionRationale(permission)) {
            PermissionState.PermanentlyDenied
        } else {
            PermissionState.Denied
        }
        onResult?.invoke(state)
    }

    return remember(state, hasAsked) {
        RememberedPermissionState(
            state = state,
            request = { launcher.launch(permission) },
            openSettings = { context.openAppSettings() },
        )
    }
}

/** The handle returned by [rememberPermissionState]. */
class RememberedPermissionState(
    val state: PermissionState,
    val request: () -> Unit,
    val openSettings: () -> Unit,
)

/**
 * Convenience: request POST_NOTIFICATIONS on Android 13+ if not already
 * granted. No-op on lower API levels. This is the FIX for the previous
 * bug where POST_NOTIFICATIONS was declared in the manifest but never
 * requested at runtime, so FCM notifications were silently dropped on
 * Android 13+.
 *
 * Returns the current granted state + a [request] callback the caller can
 * wire to a "Enable notifications" button. The first call fires the
 * system dialog automatically (via LaunchedEffect) unless [autoRequest]
 * is false.
 */
@Composable
fun rememberNotificationPermissionState(
    autoRequest: Boolean = true,
    onResult: ((PermissionState) -> Unit)? = null,
): RememberedPermissionState {
    // POST_NOTIFICATIONS only exists on Android 13+ — on lower APIs the
    // permission is granted at install time and there's nothing to request.
    if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) {
        return remember { RememberedPermissionState(PermissionState.Granted, {}, {}) }
    }
    val perm = rememberPermissionState(
        android.Manifest.permission.POST_NOTIFICATIONS,
        onResult = onResult,
    )
    if (autoRequest && perm.state is PermissionState.NotDetermined) {
        LaunchedEffect(Unit) {
            // Tiny delay so the launcher is registered before we fire.
            delay(100)
            perm.request()
        }
    }
    return perm
}
