package com.example.ui.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Material 3 typography scale — bold geometric identity.
 *
 * Heavy display weights (Black/ExtraBold) for headlines, crisp Regular/Medium
 * body. T-462 (UI-327): the LATIN backbone is now [ElInter] — the owner's
 * "clean, modern sans-serif (like Inter)" mandate; non-Latin scripts keep
 * the automatic system fallback (per-glyph), so Arabic/CJK rendering is
 * unchanged. Cost: six bundled cuts (~2.5 MB) for a consistent, modern
 * weight rhythm from caption (Medium) to display (Black).
 */
val ElTypography = Typography(
    displayLarge  = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.Black,      fontSize = 56.sp, lineHeight = 60.sp, letterSpacing = (-1.5).sp),
    displayMedium = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.Black,      fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-1.0).sp),
    displaySmall  = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.ExtraBold,  fontSize = 36.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),

    headlineLarge  = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.Bold,      fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.25).sp),
    headlineSmall  = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.Bold,      fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = 0.sp),

    titleLarge  = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.Bold,      fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.sp),
    titleMedium = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.SemiBold,  fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall  = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.SemiBold,  fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),

    bodyLarge  = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.25.sp),
    bodyMedium = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.2.sp),
    bodySmall  = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),

    labelLarge  = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall  = TextStyle(fontFamily = ElInter, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
