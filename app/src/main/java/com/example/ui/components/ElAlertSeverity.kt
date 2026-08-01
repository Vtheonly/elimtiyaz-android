package com.example.ui.components

import androidx.compose.runtime.Composable

// ── ElAlertBanner ───────────────────────────────────────────────────────────

enum class ElAlertSeverity { Info, Success, Warning, Danger }

/**
 * Alert banner with severity-based color, icon, and optional dismiss action.
 * Used for notifications, errors, and status messages.
 */
@Composable
