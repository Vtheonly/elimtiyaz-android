package com.example.ui.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * Extended semantic text styles beyond Material 3's [Typography].
 *
 * These cover use cases the M3 scale doesn't: numeric displays for amounts,
 * overlines for eyebrows, captions, action labels, and centered stat blocks.
 *
 * Access via [ElTheme.textStyles].
 */
@Immutable
data class ElTextStyles(
    /** Numeric display for amounts, balances, KPIs. */
    val numeric: TextStyle = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Black,
        fontSize = 32.sp, lineHeight = 36.sp, letterSpacing = (-0.5).sp,
    ),
    /** Large numeric — for hero KPIs. */
    val numericHero: TextStyle = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Black,
        fontSize = 48.sp, lineHeight = 52.sp, letterSpacing = (-1.0).sp,
    ),
    /** Small numeric — for inline figures. */
    val numericSmall: TextStyle = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.sp,
    ),
    /** Overline / eyebrow — above headlines. */
    val overline: TextStyle = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.5.sp,
    ),
    /** Caption — for image captions, footnotes. */
    val caption: TextStyle = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,
        fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp,
    ),
    /** Action — for buttons, links, CTAs. */
    val action: TextStyle = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 0.25.sp,
    ),
    /** Counter / badge text. */
    val badge: TextStyle = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Bold,
        fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.5.sp,
    ),
    /** Centered numeric — for stat blocks. */
    val statCentered: TextStyle = TextStyle(
        fontFamily = FontFamily.Default, fontWeight = FontWeight.Black,
        fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.5).sp,
        textAlign = TextAlign.Center,
    ),
)

val LocalElTextStyles = staticCompositionLocalOf { ElTextStyles() }
