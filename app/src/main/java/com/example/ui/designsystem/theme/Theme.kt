package com.example.ui.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme

/**
 * The unified El-Imtiyaz theme — single entry point.
 *
 * Publishes Material 3 [colorScheme][MaterialTheme.colorScheme],
 * [typography][MaterialTheme.typography], [shapes][MaterialTheme.shapes] plus
 * the extended [ElColors], [ElSpacing], [ElElevation], [ElBorders], [ElMotion],
 * and [ElTextStyles] via their respective CompositionLocals.
 *
 * @param darkTheme Follow system by default.
 * @param dynamicColor When true (Android 12+), use Material You wallpaper
 *   colors. Off by default to preserve the bold geometric brand identity.
 */
@Composable
fun ElImtiyazTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseColors = if (darkTheme) DarkElColors else LightElColors

    val materialScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        else -> baseColors.toMaterialScheme()
    }

    ApplyEdgeToEdge(darkTheme)

    CompositionLocalProvider(
        LocalElColors provides baseColors,
        LocalElSpacing provides ElSpacing(),
        LocalElElevation provides ElElevation(),
        LocalElBorders provides ElBorders(),
        LocalElMotion provides ElMotion(),
        LocalElTextStyles provides ElTextStyles(),
        LocalElShadowColor provides baseColors.shadowColor,
    ) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = ElTypography,
            shapes = ElShapes,
            content = content,
        )
    }
}

/**
 * Configures edge-to-edge layout and aligns system bar icon contrast with
 * the active theme. Extracted from the main theme composable for SRP.
 */
@Composable
private fun ApplyEdgeToEdge(darkTheme: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !darkTheme
        controller.isAppearanceLightNavigationBars = !darkTheme
        @Suppress("DEPRECATION")
        window.statusBarColor = Color.Transparent.toArgb()
        @Suppress("DEPRECATION")
        window.navigationBarColor = Color.Transparent.toArgb()
    }
}
