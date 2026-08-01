package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.navigation.AppNavHost
import com.example.ui.theme.ElImtiyazTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity host for the entire app.
 *
 * The activity is intentionally thin — all routing logic lives in
 * [AppNavHost]. Hilt injects nothing at the Activity level (the
 * SessionManager is consumed inside AppNavHost via hiltViewModel),
 * keeping this class free of business logic.
 *
 * The splash gate (showing a branded loader while the session is being
 * restored) happens inside [AppNavHost], not here — the activity just
 * hosts the composition.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElImtiyazTheme {
                AppNavHost()
            }
        }
    }
}
