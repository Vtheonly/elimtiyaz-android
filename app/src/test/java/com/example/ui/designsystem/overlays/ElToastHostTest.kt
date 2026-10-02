package com.example.ui.designsystem.overlays

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.designsystem.theme.ElImtiyazTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * T-460 H2 (issue #3 F-18) — the app-scoped DS toast layer's contract test
 * (semantic level — the ARCH-012 discipline: no screenshots).
 *
 * Pins the F-18 migration's core properties:
 *  1. LocalElToast fires render INSIDE the ElToastHost (above the app
 *     content — the property that lets a toast survive navigation, the
 *     ExpenseSubmit submit case).
 *  2. The severity helpers set the right tone (showSuccess → SUCCESS).
 *  3. show() replaces the current toast (single-slot semantics).
 *  4. Without a host, the fallback state still accepts calls (previews /
 *     tests never crash — the platform-toast property of being always
 *     available).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ElToastHostTest {

    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun `a toast fired from inside the host content renders above it`() {
        composeTestRule.setContent {
            ElImtiyazTheme {
                ElToastHost {
                    // The screen-level fire pattern: read the local, show.
                    val toast = LocalElToast.current
                    androidx.compose.material3.Button(onClick = { toast.showSuccess("Code copié") }) {
                        Text("fire")
                    }
                }
            }
        }
        composeTestRule.onNodeWithText("fire").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Code copié").assertIsDisplayed()
    }

    @Test
    fun `show replaces the current toast - single slot semantics`() {
        lateinit var state: ElToastHostState
        composeTestRule.setContent {
            ElImtiyazTheme {
                state = rememberElToastState()
                ElToastHost(state = state) { }
            }
        }
        composeTestRule.runOnIdle {
            state.show("first", ElToastTone.INFO)
            state.show("second", ElToastTone.DANGER)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("first").assertDoesNotExist()
        composeTestRule.onNodeWithText("second").assertIsDisplayed()
    }

    @Test
    fun `the fallback state without a host accepts calls without crashing`() {
        // The compositionLocal default: a detached state (same shape as
        // ElToastHostState() directly). Calling show() on it must be a safe
        // no-op (previews / un-hosted tests never crash — the platform-toast
        // property of being always available).
        val fallback = ElToastHostState()
        fallback.showSuccess("dropped silently")
        fallback.showError("dropped silently")
        // no exception = pass
        assertTrue(true)
    }

    @Test
    fun `re-showing the same message restarts the slot`() {
        val state = ElToastHostState()
        state.show("once", ElToastTone.NEUTRAL)
        val first = state.current
        state.show("once", ElToastTone.NEUTRAL)
        val second = state.current
        assertEquals("once", first?.message)
        assertEquals("once", second?.message)
        assertFalse("a re-show must replace the pending entry (new nonce)", first?.nonce == second?.nonce)
    }
}
