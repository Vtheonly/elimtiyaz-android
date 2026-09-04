package com.example.ui.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.ui.designsystem.components.tabs.ElScrollableTabRow
import com.example.ui.designsystem.theme.ElImtiyazTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T-044 pass 3a — semantic tests for the DS `ElScrollableTabRow`, the
 * documented prerequisite for migrating the 6-tab FinancialsHub (and the
 * other ModernSecondaryTabRow call sites) onto the design system.
 *
 * Deliberately SEMANTIC assertions, not screenshots: ARCH-012 recorded that
 * roborazzi's captureRoboImage does not write PNGs inside the repair
 * container, so a screenshot gate here would be a silent no-op. The
 * semantics cover the migration-critical contract instead: every label
 * renders exactly once, exactly the selected pill carries the selected
 * flag, taps propagate the index, a programmatic selection change past the
 * visible viewport does not crash (the auto-scroll parity with the legacy
 * tab row), and an out-of-range index selects nothing.
 *
 * LAZY-COMPOSITION caveat (found live in this session): LazyRow only
 * composes the pills currently visible, so off-screen labels DO NOT EXIST
 * in the semantics tree. Every assertion on a specific pill therefore
 * scrolls it into view first via the row's stable test tag — the same
 * interaction a real user performs.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ElScrollableTabRowTest {

  @get:Rule val composeTestRule = createComposeRule()

  private val financialHubTabs =
    listOf("Paiements", "Tranches", "Dépenses", "Reçus", "Soldes", "Rapprochement")

  /** Renders the row once per test and waits for the initial auto-scroll to settle. */
  private fun setContent(selectedIndex: Int, onSelected: (Int) -> Unit = {}) {
    composeTestRule.setContent {
      ElImtiyazTheme {
        ElScrollableTabRow(
          tabs = financialHubTabs,
          selectedIndex = selectedIndex,
          onSelected = onSelected,
        )
      }
    }
    composeTestRule.waitForIdle()
  }

  /** Brings a pill into the composed viewport before asserting on it. */
  private fun scrollToTab(label: String) {
    composeTestRule
      .onNodeWithTag("el_scrollable_tab_row")
      .performScrollToNode(hasText(label))
    composeTestRule.waitForIdle()
  }

  @Test
  fun all_tab_labels_render_exactly_once() {
    setContent(0)
    for (label in financialHubTabs) {
      scrollToTab(label)
      composeTestRule
        .onAllNodesWithText(label, useUnmergedTree = true)
        .assertCountEquals(1)
    }
  }

  @Test
  fun exactly_the_selected_index_is_selected() {
    setContent(2)
    scrollToTab("Dépenses")
    composeTestRule.onAllNodesWithText("Dépenses")[0].assertIsSelected()
    scrollToTab("Paiements")
    composeTestRule.onAllNodesWithText("Paiements")[0].assertIsNotSelected()
  }

  @Test
  fun tapping_a_tab_reports_its_index() {
    var reported = -1
    setContent(0, onSelected = { reported = it })
    scrollToTab("Reçus")
    composeTestRule.onNodeWithText("Reçus").performClick()
    assertEquals(3, reported)
  }

  @Test
  fun programmatic_selection_past_the_viewport_does_not_crash() {
    var selected by mutableStateOf(0)
    composeTestRule.setContent {
      ElImtiyazTheme {
        ElScrollableTabRow(
          tabs = financialHubTabs,
          selectedIndex = selected,
          onSelected = {},
        )
      }
    }
    composeTestRule.waitForIdle()
    // Deep-link style programmatic switch to the LAST tab — the auto-scroll
    // effect must animate to the far end without throwing (6 tabs overflow a
    // phone width; this is the exact FinancialsHub deep-link scenario).
    selected = 5
    composeTestRule.waitForIdle()
    composeTestRule.onAllNodesWithText("Rapprochement")[0].assertIsSelected()
  }

  @Test
  fun out_of_range_index_selects_nothing_and_does_not_crash() {
    setContent(99)
    // The `selectedIndex in tabs.indices` guard must reject the bogus index:
    // NO pill anywhere in the tree carries the selected flag.
    composeTestRule.onAllNodes(isSelected()).assertCountEquals(0)
    // The row is still composed and usable afterwards.
    scrollToTab("Paiements")
    composeTestRule.onAllNodesWithText("Paiements")[0].assertIsNotSelected()
  }
}
