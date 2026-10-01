package com.example.ui.designsystem.overlays

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.designsystem.theme.ElImtiyazTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T-458 (128th session) — the ElInfoTip rendering-state test (the Android
 * mirror of the desktop's t-447-statistics-tooltips suite, at the semantic
 * level — ARCH-012's discipline: no screenshots).
 *
 * Pins the component's contract: the ⓘ affordance carries the entry's
 * title as its content description (talkback), the testTag convention
 * (stat-tip-<dashed-key>), the TAP opens the popup with the title + the
 * three meta-labelled fields, and the honest-empty rule (an unknown key
 * renders NOTHING — never a fabricated text).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ElInfoTipTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `a valid key renders the affordance with the entry title as content description`() {
        composeTestRule.setContent {
            ElImtiyazTheme {
                ElInfoTip(tip = "statStrip.median")
            }
        }
        composeTestRule
            .onNodeWithTag("stat-tip-statStrip-median")
            .assertIsDisplayed()
        composeTestRule
            .onNodeWithContentDescription(StatsTips.statStrip.getValue("median").title)
            .assertIsDisplayed()
    }

    @Test
    fun `tapping the affordance opens the popup with the title and the meta-labelled fields`() {
        composeTestRule.setContent {
            ElImtiyazTheme {
                ElInfoTip(tip = "statStrip.median")
            }
        }
        composeTestRule.onNodeWithTag("stat-tip-statStrip-median").performClick()
        composeTestRule.waitForIdle()
        val entry = StatsTips.statStrip.getValue("median")
        composeTestRule.onNodeWithText(entry.title).assertExists()
        composeTestRule.onNodeWithText(StatsTips.metaMeasures).assertExists()
        composeTestRule.onNodeWithText(StatsTips.metaCalc).assertExists()
        // The popup content renders the MEASURES value (the tooltip's body).
        composeTestRule.onNodeWithText(entry.measures).assertExists()
    }

    @Test
    fun `an unknown key renders NOTHING - the honest-empty contract`() {
        composeTestRule.setContent {
            ElImtiyazTheme {
                ElInfoTip(tip = "noSuchSection.noSuchEntry")
            }
        }
        composeTestRule.onNodeWithTag("stat-tip-noSuchSection-noSuchEntry").assertDoesNotExist()
    }
}
