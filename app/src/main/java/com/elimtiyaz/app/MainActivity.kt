package com.elimtiyaz.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.elimtiyaz.app.navigation.ElImtiyazNavHost
import com.elimtiyaz.core.designsystem.ElimtiyazTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single-activity entry. Hosts the Compose root and the app's NavHost.
 *
 * The Compose tree is layered as:
 *   ElimtiyazTheme → SessionProvider → ElImtiyazNavHost
 *
 * [SessionProvider] installs the current [com.elimtiyaz.core.common.Session]
 * into a CompositionLocal so every screen below can call
 * [com.elimtiyaz.core.rbac.accessStateOf] without injecting AuthRepository.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ElimtiyazTheme {
                SessionProvider {
                    ElImtiyazNavHost()
                }
            }
        }
    }
}
