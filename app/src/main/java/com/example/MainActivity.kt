package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import com.example.ui.designsystem.theme.ElImtiyazTheme
import com.example.ui.navigation.AppNavHost
import com.example.ui.permissions.PermissionState
import com.example.ui.permissions.rememberNotificationPermissionState
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the entire app.
 *
 * FIX (login-blocks / crash): use the design-system [ElImtiyazTheme] which
 * provides ALL the CompositionLocals the design-system components depend on
 * (`LocalElColors`, `LocalElSpacing`, `LocalElElevation`, `LocalElBorders`,
 * `LocalElMotion`, `LocalElTextStyles`, `LocalElShadowColor`). The previous
 * import pointed at `com.example.ui.theme.ElImtiyazTheme` which only provided
 * `LocalElDesignTokens` + `LocalSemanticColors`, so any screen using an
 * `ElScaffold` from the design-system package crashed with
 * "ElColors not provided".
 *
 * FIX (notifications): request POST_NOTIFICATIONS on Android 13+ as soon as
 * the activity starts. The previous code declared the permission in the
 * manifest but never requested it at runtime — FCM notifications were
 * silently dropped on Android 13+ devices.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElImtiyazTheme {
                // Request POST_NOTIFICATIONS on Android 13+ as soon as the
                // activity is created. The helper no-ops on lower API levels.
                // Auto-request is gated on `NotDetermined` so we don't pester
                // the user after they've already decided.
                val notificationPerm = rememberNotificationPermissionState(
                    autoRequest = true,
                )
                // Log the outcome for debugging — no UI blocking here. The
                // user's decision takes effect on the NEXT FCM message.
                LaunchedEffect(notificationPerm.state) {
                    when (notificationPerm.state) {
                        PermissionState.Granted -> android.util.Log.i(
                            "MainActivity",
                            "POST_NOTIFICATIONS granted — FCM notifications will be delivered",
                        )
                        PermissionState.Denied -> android.util.Log.w(
                            "MainActivity",
                            "POST_NOTIFICATIONS denied — FCM notifications will be silently dropped",
                        )
                        PermissionState.PermanentlyDenied -> android.util.Log.w(
                            "MainActivity",
                            "POST_NOTIFICATIONS permanently denied — user must grant via system settings",
                        )
                        PermissionState.NotDetermined -> Unit
                    }
                }
                AppNavHost()
            }
        }
    }
}
