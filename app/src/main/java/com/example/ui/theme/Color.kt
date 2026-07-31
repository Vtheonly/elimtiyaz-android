package com.example.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * El-Imtiyaz brand palette — reuses the desktop color tokens (plan §02.06).
 * Material 3 roles are mapped from these tokens in [Theme.kt].
 *
 * Dark theme is the default (matches the desktop). Light theme is provided
 * for accessibility and outdoor visibility.
 */

// ── Brand tokens ───────────────────────────────────────────────────────────
val PrimaryBlue    = Color(0xFF349BD4)
val DeepBlue       = Color(0xFF2B7FB0)
val LightBlue      = Color(0xFF6EC1E4)
val SlateGray      = Color(0xFF3B464C)
val WarmGold       = Color(0xFFC8A98C)
val SuccessGreen   = Color(0xFF3FA66E)
val DangerRed      = Color(0xFFC0504D)
val WarningOrange  = Color(0xFFC8A98C)
val InfoBlue       = Color(0xFF349BD4)

// ── Dark theme surfaces ────────────────────────────────────────────────────
val DarkBackground       = Color(0xFF1A1B1C)
val DarkSurface          = Color(0xFF242526)
val DarkSurfaceVariant   = Color(0xFF2A2B2D)
val DarkElevatedSurface  = Color(0xFF313234)

// ── Light theme surfaces ───────────────────────────────────────────────────
val LightBackground      = Color(0xFFF7F8FA)
val LightSurface         = Color(0xFFFFFFFF)
val LightSurfaceVariant  = Color(0xFFEFF1F4)
val LightElevatedSurface = Color(0xFFFFFFFF)

// ── Text colors ────────────────────────────────────────────────────────────
val DarkTextPrimary   = Color(0xFFF5F6F7)
val DarkTextSecondary = Color(0xFFB0B3B8)
val DarkTextMuted     = Color(0xFF6E7176)

val LightTextPrimary   = Color(0xFF1A1B1C)
val LightTextSecondary = Color(0xFF4A4D52)
val LightTextMuted     = Color(0xFF7A7D82)

// ── Outline / borders ──────────────────────────────────────────────────────
val DarkOutline  = Color(0xFF3D3E40)
val LightOutline = Color(0xFFD1D5DB)

// ── Role accent colors (for role dashboards) ───────────────────────────────
val RoleAdmin       = Color(0xFF8B7226)   // SuperAdmin — gold
val RoleFinancial   = Color(0xFF428FA9)   // FinancialOfficer — teal
val RoleTeacher     = Color(0xFF3FA66E)   // Teacher — green
val RoleSupport     = Color(0xFF6EC1E4)   // SupportStaff — light blue
val RoleManager     = Color(0xFF9A5C32)   // Manager — copper
val RoleBuyer       = Color(0xFF7B5EA7)   // Buyer — purple
val RoleDriver      = Color(0xFFC0504D)   // Driver — red
val RoleWarehouse   = Color(0xFF5D7052)   // WarehouseWorker — forest
val RoleWorker      = Color(0xFF6E7176)   // Worker — gray
