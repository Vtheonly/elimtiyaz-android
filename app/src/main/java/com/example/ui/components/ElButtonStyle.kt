package com.example.ui.components

import androidx.compose.runtime.Composable

// ── ElButton ───────────────────────────────────────────────────────────────

enum class ElButtonStyle { Primary, Secondary, Danger, Ghost }

/**
 * Custom button with gradient fill (primary), tonal surface (secondary),
 * or transparent (ghost). Includes pressed-state animation.
 */
@Composable
