package com.example.ui.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * El-Imtiyaz Design System — Color Tokens
 *
 * Visual identity: "Electric Violet & Sunshine" — a bold geometric palette
 * pairing a saturated violet primary with a high-contrast amber accent.
 *
 * Components MUST NOT reference these constants directly — go through
 * [ElTheme.colors] so light/dark switching works correctly.
 */

// ── Brand: Primary (Electric Violet) ────────────────────────────────────────
val Violet500 = Color(0xFF6366F1)
val Violet600 = Color(0xFF4F46E5)   // primary (light)
val Violet700 = Color(0xFF4338CA)
val Violet400 = Color(0xFF818CF8)   // primary (dark)
val Violet300 = Color(0xFFA5B4FC)
val Violet50  = Color(0xFFEEF2FF)

// ── Brand: Secondary (Sunshine Amber) ───────────────────────────────────────
val Amber400 = Color(0xFFFBBF24)    // secondary (dark)
val Amber500 = Color(0xFFF59E0B)    // secondary (light)
val Amber600 = Color(0xFFD97706)
val Amber100 = Color(0xFFFEF3C7)

// ── Brand: Tertiary (Hot Pink) ──────────────────────────────────────────────
val Pink400  = Color(0xFFF472B6)
val Pink500  = Color(0xFFEC4899)
val Pink600  = Color(0xFFDB2777)

// ── Semantic: Success (Emerald) ─────────────────────────────────────────────
val Emerald400 = Color(0xFF34D399)  // success (dark)
val Emerald500 = Color(0xFF10B981)  // success (light)
val Emerald600 = Color(0xFF059669)
val Emerald100 = Color(0xFFD1FAE5)

// ── Semantic: Warning (Tangerine) ───────────────────────────────────────────
val Tangerine400 = Color(0xFFFB923C)
val Tangerine500 = Color(0xFFF97316)  // warning (light)
val Tangerine600 = Color(0xFFEA580C)
val Tangerine100 = Color(0xFFFFEDD5)

// ── Semantic: Danger (Rose Red) ─────────────────────────────────────────────
val Rose400 = Color(0xFFF87171)   // danger (dark)
val Rose500 = Color(0xFFEF4444)   // danger (light)
val Rose600 = Color(0xFFDC2626)
val Rose100 = Color(0xFFFEE2E2)

// ── Semantic: Info (Sky Blue) ───────────────────────────────────────────────
val Sky400 = Color(0xFF38BDF8)   // info (dark)
val Sky500 = Color(0xFF0EA5E9)   // info (light)
val Sky600 = Color(0xFF0284C7)
val Sky100 = Color(0xFFE0F2FE)

// ── Neutral surfaces: Light theme ───────────────────────────────────────────
val LightBackground       = Color(0xFFFAFAFB)
val LightSurface          = Color(0xFFFFFFFF)
val LightSurfaceVariant   = Color(0xFFF1F2F6)
val LightSurfaceElevated  = Color(0xFFFFFFFF)
val LightInverseSurface   = Color(0xFF1A1B22)

// ── Neutral surfaces: Dark theme ────────────────────────────────────────────
val DarkBackground        = Color(0xFF0A0A0F)
val DarkSurface           = Color(0xFF13131A)
val DarkSurfaceVariant    = Color(0xFF1C1C26)
val DarkSurfaceElevated   = Color(0xFF23232F)
val DarkInverseSurface    = Color(0xFFE6E7EC)

// ── Text colors ─────────────────────────────────────────────────────────────
val LightTextPrimary   = Color(0xFF0F0F14)
val LightTextSecondary = Color(0xFF4B5563)
val LightTextMuted     = Color(0xFF9CA3AF)
val LightTextOnColor   = Color(0xFFFFFFFF)

val DarkTextPrimary    = Color(0xFFF5F6FA)
val DarkTextSecondary  = Color(0xFFB4B9C7)
val DarkTextMuted      = Color(0xFF6B7280)
val DarkTextOnColor    = Color(0xFF0F0F14)

// ── Outlines / borders ──────────────────────────────────────────────────────
val LightOutline        = Color(0xFFE5E7EB)
val LightOutlineStrong  = Color(0xFFD1D5DB)
val LightOutlineVariant = Color(0xFFF3F4F6)

val DarkOutline         = Color(0xFF2A2A36)
val DarkOutlineStrong   = Color(0xFF3A3A48)
val DarkOutlineVariant  = Color(0xFF1F1F2A)

// ── Scrim / shadow / glass ──────────────────────────────────────────────────
val LightScrim        = Color(0xFF0F0F14).copy(alpha = 0.48f)
val DarkScrim         = Color(0xFF000000).copy(alpha = 0.64f)
val LightShadowColor  = Color(0xFF4F46E5).copy(alpha = 0.10f)
val DarkShadowColor   = Color(0xFF000000).copy(alpha = 0.40f)
val LightGlassTint    = Color(0xFFFFFFFF).copy(alpha = 0.70f)
val LightGlassBorder  = Color(0xFFFFFFFF).copy(alpha = 0.90f)
val DarkGlassTint     = Color(0xFFFFFFFF).copy(alpha = 0.05f)
val DarkGlassBorder   = Color(0xFFFFFFFF).copy(alpha = 0.10f)

// ── Role accents (used by RBAC dashboards & avatars) ────────────────────────
val RoleAdmin      = Amber500
val RoleFinancial  = Violet600
val RoleTeacher    = Emerald500
val RoleSupport    = Sky500
val RoleManager    = Pink500
val RoleBuyer      = Color(0xFF8B5CF6)
val RoleDriver     = Tangerine500
val RoleWarehouse  = Color(0xFF84CC16)
val RoleWorker     = Color(0xFF64748B)
