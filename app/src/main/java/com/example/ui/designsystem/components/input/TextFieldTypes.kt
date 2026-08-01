package com.example.ui.designsystem.components.input

/** Visual state of an [ElTextField], drives border + helper colors. */
enum class ElTextFieldState { DEFAULT, FOCUSED, ERROR, DISABLED }

/**
 * Resolves the active [ElTextFieldState] from inputs.
 */
internal fun resolveFieldState(
    enabled: Boolean,
    isError: Boolean,
): ElTextFieldState = when {
    !enabled -> ElTextFieldState.DISABLED
    isError -> ElTextFieldState.ERROR
    else -> ElTextFieldState.DEFAULT
}
