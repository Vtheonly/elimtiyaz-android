package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.session.SessionManager
import com.example.ui.navigation.AppNavHost
import com.example.ui.theme.ElImtiyazTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Single-activity host for the entire app.
 *
 * The activity is intentionally thin — all routing logic lives in
 * [AppNavHost]. Hilt injects [SessionManager] so the activity can
 * observe session state and trigger initial navigation.
 *
 * The splash gate (showing a branded loader while the session is being
 * restored) happens inside [AppNavHost], not here — the activity just
 * hosts the composition.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val sessionState by sessionManager.state.collectAsState()
            ElImtiyazTheme {
                AppNavHost(sessionState = sessionState)
            }
        }
    }
}
