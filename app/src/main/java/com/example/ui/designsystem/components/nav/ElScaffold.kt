package com.example.ui.designsystem.components.nav

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.designsystem.theme.ElTheme

/**
 * The unified El-Imtiyaz scaffold — every screen should use this instead of
 * the raw M3 [Scaffold].
 *
 * Wraps [Scaffold] but applies the El-Imtiyaz theme tokens for the background
 * (a subtle vertical gradient between [ElColors.background] and [ElColors.surface]
 * for depth) and applies a soft slide-in / fade-in for the content area on
 * first composition. The motion uses [ElTheme.motion.normal] for a settled,
 * confident feel.
 *
 * Slots mirror M3: [topBar], [bottomBar], [floatingActionButton], [snackbarHost],
 * and [content]. The content lambda receives [PaddingValues] so it can inset
 * itself past the bars — pass through to your scroll container.
 *
 * The legacy `ui.components.ElScaffold` exposes `content: @Composable () -> Unit`
 * without padding values; the modern version changes the signature to take
 * `PaddingValues` so screens correctly avoid the bottom bar. Restored screens
 * should migrate to this signature.
 *
 * @param modifier              Outer modifier (fillMaxSize by default).
 * @param topBar                Top bar slot, typically [ElTopBar].
 * @param bottomBar             Bottom bar slot, typically [ElBottomBar].
 * @param floatingActionButton  FAB slot, typically [ElFab].
 * @param snackbarHost          Snackbar host slot, typically [ElSnackbarHost].
 * @param contentColor          Override the default content color.
 * @param content               Main content, receives the inner padding.
 */
@Composable
fun ElScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    contentColor: Color = ElTheme.colors.onBackground,
    content: @Composable (PaddingValues) -> Unit,
) {
    val colors = ElTheme.colors

    // A subtle background gradient gives the screen depth without distracting
    // from data — primary in light, deeper in dark. The colors object already
    // provides `heroBrush` (vertical gradient between background and surface),
    // which is exactly what we want here.
    val backgroundBrush: Brush = colors.heroBrush

    // Content enter animation — fade + subtle upward translation.
    val contentAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush),
        topBar = topBar,
        bottomBar = bottomBar,
        snackbarHost = snackbarHost,
        floatingActionButton = floatingActionButton,
        containerColor = Color.Transparent,
        contentColor = contentColor,
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets,
    ) { paddingValues ->
        // The Scaffold composable inlines the content into a Box that already
        // consumes the background — wrapping in an alpha modifier gives us the
        // soft entrance without breaking inset handling.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(contentAlpha.value),
        ) {
            content(paddingValues)
        }
    }
}
