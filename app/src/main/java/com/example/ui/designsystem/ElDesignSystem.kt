@file:JvmName("ElDesignSystem")

package com.example.ui.designsystem

// ── Theme tokens ───────────────────────────────────────────────────────────
import com.example.ui.designsystem.theme.ElTheme
import com.example.ui.designsystem.theme.ElColors
import com.example.ui.designsystem.theme.ElSpacing
import com.example.ui.designsystem.theme.ElMotion
import com.example.ui.designsystem.theme.ElElevation
import com.example.ui.designsystem.theme.ElElevationSpec
import com.example.ui.designsystem.theme.ElBorders
import com.example.ui.designsystem.theme.ElTextStyles

// ── Buttons ────────────────────────────────────────────────────────────────
import com.example.ui.designsystem.components.button.ElButton
import com.example.ui.designsystem.components.button.ElIconButton
import com.example.ui.designsystem.components.button.ElFab
import com.example.ui.designsystem.components.button.ElButtonVariant
import com.example.ui.designsystem.components.button.ElButtonSize

// ── Cards ──────────────────────────────────────────────────────────────────
import com.example.ui.designsystem.components.card.ElCard
import com.example.ui.designsystem.components.card.ElStatCard
import com.example.ui.designsystem.components.card.ElGradientStatCard
import com.example.ui.designsystem.components.card.ElCardVariant
import com.example.ui.designsystem.components.card.ElCardSize

// ── Inputs ─────────────────────────────────────────────────────────────────
import com.example.ui.designsystem.components.input.ElTextField
import com.example.ui.designsystem.components.input.ElDropdown
import com.example.ui.designsystem.components.input.ElDropdownOption
import com.example.ui.designsystem.components.input.ElSearchBar
import com.example.ui.designsystem.components.input.ElSwitch
import com.example.ui.designsystem.components.input.ElCheckbox
import com.example.ui.designsystem.components.input.ElRadioButton
import com.example.ui.designsystem.components.input.ElDatePicker
import com.example.ui.designsystem.components.input.ElMoneyInput

// ── Feedback ───────────────────────────────────────────────────────────────
import com.example.ui.designsystem.components.feedback.ElLoadingBlock
import com.example.ui.designsystem.components.feedback.ElLinearLoader
import com.example.ui.designsystem.components.feedback.ElLinearProgress
import com.example.ui.designsystem.components.feedback.ElSpinner
import com.example.ui.designsystem.components.feedback.ElEmptyState
import com.example.ui.designsystem.components.feedback.ElSnackbar
import com.example.ui.designsystem.components.feedback.ElSnackbarHost
import com.example.ui.designsystem.components.feedback.ElSnackbarHostState
import com.example.ui.designsystem.components.feedback.ElSnackbarVisuals

// ── Display ────────────────────────────────────────────────────────────────
import com.example.ui.designsystem.components.display.ElChip
import com.example.ui.designsystem.components.display.ElChipGroup
import com.example.ui.designsystem.components.display.ElBadge
import com.example.ui.designsystem.components.display.ElAvatar
import com.example.ui.designsystem.components.display.ElDivider
import com.example.ui.designsystem.components.display.ElSectionHeader
import com.example.ui.designsystem.components.display.ElInfoRow
import com.example.ui.designsystem.components.display.ElTag
import com.example.ui.designsystem.components.display.ElAlertBanner
import com.example.ui.designsystem.components.display.ElTagTone
import com.example.ui.designsystem.components.display.ElTagSize
import com.example.ui.designsystem.components.display.ElAlertSeverity
import com.example.ui.designsystem.components.display.ElGradient
import com.example.ui.designsystem.components.display.ElSnackbarSeverity

// ── Nav ────────────────────────────────────────────────────────────────────
import com.example.ui.designsystem.components.nav.ElScaffold
import com.example.ui.designsystem.components.nav.ElTopBar
import com.example.ui.designsystem.components.nav.ElBottomBar
import com.example.ui.designsystem.components.nav.ElNavRail
import com.example.ui.designsystem.components.nav.ElNavDestination

// ── Data ───────────────────────────────────────────────────────────────────
import com.example.ui.designsystem.components.data.ElTable
import com.example.ui.designsystem.components.data.ElTableColumn
import com.example.ui.designsystem.components.data.ElTableRow
import com.example.ui.designsystem.components.data.ElColumnAlign
import com.example.ui.designsystem.components.data.ElListItem
import com.example.ui.designsystem.components.data.ElBarChart
import com.example.ui.designsystem.components.data.ElBarChartItem
import com.example.ui.designsystem.components.data.ElLineChart
import com.example.ui.designsystem.components.data.ElLineChartPoint
import com.example.ui.designsystem.components.data.ElDonutChart
import com.example.ui.designsystem.components.data.ElDonutSegment
import com.example.ui.designsystem.components.data.ElSparkline
import com.example.ui.designsystem.components.data.ElProgressRing

// ── Overlays ───────────────────────────────────────────────────────────────
import com.example.ui.designsystem.overlays.ElDialogShell
import com.example.ui.designsystem.overlays.ElDialogContent
import com.example.ui.designsystem.overlays.ElBottomSheet
import com.example.ui.designsystem.overlays.ElSheetContent
import com.example.ui.designsystem.overlays.ElToast
import com.example.ui.designsystem.overlays.ElTooltip
import com.example.ui.designsystem.overlays.ElContextMenu
import com.example.ui.designsystem.overlays.ElConfirmationDialog
import com.example.ui.designsystem.overlays.ElToastTone

// ── Foundation ─────────────────────────────────────────────────────────────
import com.example.ui.designsystem.foundation.pressClickable
import com.example.ui.designsystem.foundation.noRippleClickable
import com.example.ui.designsystem.foundation.pressScale
import com.example.ui.designsystem.foundation.elShadow
import com.example.ui.designsystem.foundation.elMoneyFormat
import com.example.ui.designsystem.foundation.elMoneyParse
import com.example.ui.designsystem.foundation.elThousandsFormat
import com.example.ui.designsystem.foundation.elPercentFormat

/**
 * El-Imtiyaz Design System — public API barrel.
 *
 * Import this object for one-line access to the most-used composables.
 * For full component coverage, import directly from the subpackages.
 *
 * Usage:
 *   import com.example.ui.designsystem.ElDesignSystem.*
 *
 *   ElButton(text = "Save", onClick = { … })
 *   ElCard(variant = ElCardVariant.ELEVATED) { … }
 *
 * The barrel re-exports all public composables, type enums, and foundation
 * helpers added by the design system so consumers don't need to maintain a
 * long list of package-qualified imports.
 *
 * All re-exports above are intentionally flat — Kotlin will resolve them
 * through this file's package (`com.example.ui.designsystem`), so callers
 * can `import com.example.ui.designsystem.ElButton` directly without going
 * through this object.
 */
@Suppress("unused", "ObjectPropertyName")
object ElDesignSystem {
    const val VERSION = "1.1.0"
    const val NAME = "Electric Violet & Sunshine"

    /** Convenience accessor for the theme object — matches `ElTheme`. */
    val theme get() = ElTheme
}
