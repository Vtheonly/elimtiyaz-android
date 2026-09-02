package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.infrastructure.notifications.EXTRA_DEEPLINK_ROUTE
import com.example.infrastructure.notifications.EXTRA_DEEPLINK_TYPE
import com.example.infrastructure.notifications.NOTIFICATION_CLICK_ACTION
import com.example.infrastructure.notifications.NotificationDeepLink
import com.example.ui.designsystem.theme.ElImtiyazTheme
import com.example.ui.navigation.AppNavHost
import com.example.ui.permissions.PermissionState
import com.example.ui.permissions.rememberNotificationPermissionState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleNotificationDeepLink(intent)
        setContent {
            ElImtiyazTheme {
                val notificationPerm = rememberNotificationPermissionState(
                    autoRequest = true,
                )
                LaunchedEffect(notificationPerm.state) {
                    when (notificationPerm.state) {
                        PermissionState.Granted -> android.util.Log.i(
                            "MainActivity",
                            "POST_NOTIFICATIONS granted",
                        )
                        PermissionState.Denied,
                        PermissionState.PermanentlyDenied,
                        PermissionState.NotDetermined -> Unit
                    }
                }

                // Centered Responsive Container (Full screen on phones, framed on tablets/Waydroid)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0F0F14)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 480.dp)
                            .clipToBounds(),
                    ) {
                        AppNavHost()
                    }
                }
            }
        }
    }

    /**
     * PUSH-101 (T-127): a notification tap arrives as an intent whose action
     * is [NOTIFICATION_CLICK_ACTION] (the EF's android click_action, matched
     * by the manifest intent-filter) carrying the notification type +
     * optional route extras. Publish to [NotificationDeepLink] so the
     * bottom-nav host selects the matching hub once the user is signed in.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNotificationDeepLink(intent)
    }

    private fun handleNotificationDeepLink(intent: Intent?) {
        if (intent?.action != NOTIFICATION_CLICK_ACTION) return
        val type = intent.getStringExtra(EXTRA_DEEPLINK_TYPE) ?: return
        val route = intent.getStringExtra(EXTRA_DEEPLINK_ROUTE)
        NotificationDeepLink.publish(NotificationDeepLink.Pending(type = type, route = route))
        android.util.Log.i("MainActivity", "Notification deep-link: type=$type route=$route")
    }
}