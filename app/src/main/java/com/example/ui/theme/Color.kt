package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * El-Imtiyaz brand palette — a refined design system with its own identity.
 *
 * The palette is built around a deep ocean blue primary with warm gold accents,
 * evoking the institutional prestige of an Algerian private school. Dark theme
 * uses a near-black charcoal with subtle blue undertones for depth.
 *
 * Material 3 roles are mapped from these tokens in [Theme.kt].
 */

// ── Brand tokens ───────────────────────────────────────────────────────────
val PrimaryBlue    = Color(0xFF2E8BC0)
val DeepBlue       = Color(0xFF1B6CA8)
val LightBlue      = Color(0xFF5DADE2)
val SlateGray      = Color(0xFF3B464C)
val WarmGold       = Color(0xFFD4A574)
val DeepGold       = Color(0xFFB8893F)
val SuccessGreen   = Color(0xFF2D9B6B)
val DangerRed      = Color(0xFFD04A4A)
val WarningOrange  = Color(0xFFE0922F)
val InfoBlue       = Color(0xFF2E8BC0)

// ── Dark theme surfaces (charcoal with blue undertones) ────────────────────
val DarkBackground       = Color(0xFF16181D)
val DarkSurface          = Color(0xFF1C1F26)
val DarkSurfaceVariant   = Color(0xFF252932)
val DarkElevatedSurface  = Color(0xFF2A2E38)

// ── Light theme surfaces (warm off-white) ──────────────────────────────────
val LightBackground      = Color(0xFFF5F6F8)
val LightSurface         = Color(0xFFFFFFFF)
val LightSurfaceVariant  = Color(0xFFEDEFF3)
val LightElevatedSurface = Color(0xFFFFFFFF)

// ── Text colors ────────────────────────────────────────────────────────────
val DarkTextPrimary   = Color(0xFFECEFF4)
val DarkTextSecondary = Color(0xFFA0A6B0)
val DarkTextMuted     = Color(0xFF6B7280)

val LightTextPrimary   = Color(0xFF1A1D23)
val LightTextSecondary = Color(0xFF4A5260)
val LightTextMuted     = Color(0xFF7A8290)

// ── Outline / borders ──────────────────────────────────────────────────────
val DarkOutline  = Color(0xFF353A45)
val LightOutline = Color(0xFFD1D5DB)

// ── Glassmorphism / frosted surfaces ───────────────────────────────────────
val DarkGlassTint      = Color(0xFFFFFFFF).copy(alpha = 0.04f)
val LightGlassTint     = Color(0xFFFFFFFF).copy(alpha = 0.65f)
val DarkGlassBorder    = Color(0xFFFFFFFF).copy(alpha = 0.08f)
val LightGlassBorder   = Color(0xFFFFFFFF).copy(alpha = 0.8f)

// ── Shadow colors (for custom elevation) ───────────────────────────────────
val DarkShadowColor    = Color(0xFF000000).copy(alpha = 0.3f)
val LightShadowColor   = Color(0xFF1B6CA8).copy(alpha = 0.08f)

// ── Gradients (signature visual element) ───────────────────────────────────

/** Primary brand gradient — used for hero cards, login, FABs. */
val PrimaryGradient = listOf(PrimaryBlue, DeepBlue)

/** Primary brand gradient — diagonal variant for hero banners. */
val PrimaryGradientDiagonal = listOf(DeepBlue, PrimaryBlue, LightBlue)

/** Gold accent gradient — used for premium / achievement elements. */
val GoldGradient = listOf(WarmGold, DeepGold)

/** Success gradient — used for positive financial figures. */
val SuccessGradient = listOf(SuccessGreen, Color(0xFF1E7A4F))

/** Danger gradient — used for overdue / alert elements. */
val DangerGradient = listOf(DangerRed, Color(0xFFA8362F))

/** Dark surface gradient — subtle elevation for dark theme cards. */
val DarkSurfaceGradient = listOf(DarkElevatedSurface, DarkSurface)

/** Light surface gradient — subtle elevation for light theme cards. */
val LightSurfaceGradient = listOf(Color(0xFFFFFFFF), Color(0xFFF8F9FB))

/** Dark hero gradient — for screen backgrounds with depth. */
val DarkHeroGradient = listOf(DarkSurface, DarkBackground)

/** Light hero gradient — for screen backgrounds with depth. */
val LightHeroGradient = listOf(LightBackground, Color(0xFFECEEF3))

/** Brush helpers ─────────────────────────────────────────────────────────── */

fun primaryGradientBrush() = Brush.horizontalGradient(PrimaryGradient)
fun primaryDiagonalGradientBrush() = Brush.linearGradient(PrimaryGradientDiagonal)
fun goldGradientBrush() = Brush.horizontalGradient(GoldGradient)
fun successGradientBrush() = Brush.horizontalGradient(SuccessGradient)
fun dangerGradientBrush() = Brush.horizontalGradient(DangerGradient)
fun darkSurfaceGradientBrush() = Brush.verticalGradient(DarkSurfaceGradient)
fun lightSurfaceGradientBrush() = Brush.verticalGradient(LightSurfaceGradient)
fun darkHeroGradientBrush() = Brush.verticalGradient(DarkHeroGradient)
fun lightHeroGradientBrush() = Brush.verticalGradient(LightHeroGradient)

// ── Role accent colors (for role dashboards) ───────────────────────────────
val RoleAdmin       = Color(0xFFD4A574)   // SuperAdmin — gold
val RoleFinancial   = Color(0xFF2E8BC0)   // FinancialOfficer — blue
val RoleTeacher     = Color(0xFF2D9B6B)   // Teacher — green
val RoleSupport     = Color(0xFF5DADE2)   // SupportStaff — light blue
val RoleManager     = Color(0xFFB8893F)   // Manager — deep gold
val RoleBuyer       = Color(0xFF7B5EA7)   // Buyer — purple
val RoleDriver      = Color(0xFFD04A4A)   // Driver — red
val RoleWarehouse   = Color(0xFF5D7052)   // WarehouseWorker — forest
val RoleWorker      = Color(0xFF6B7280)   // Worker — gray