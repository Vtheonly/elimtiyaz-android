package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.ui.designsystem.theme.ElImtiyazTheme
import com.example.ui.navigation.AppNavHost
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
