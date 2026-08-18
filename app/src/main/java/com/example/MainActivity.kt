package com.example

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
}