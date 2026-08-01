package com.example.ui.designsystem.components.display

/**
 * El-Imtiyaz Design System — Display Component Type Tokens.
 *
 * Shared enums for the display-family components (tag, alert banner,
 * gradient stat card) and the snackbar severity used by feedback
 * overlays. Kept in one file so screens can import the whole set with
 * a single line.
 *
 * Components in the system MUST consume these enums instead of ad-hoc
 * booleans or string codes — this guarantees exhaustive `when` coverage
 * and a single source of truth for visual variants.
 */

/** Tone of an [ElTag] — drives background tint and foreground color. */
enum class ElTagTone {
    /** Neutral / surface-variant — default for generic labels. */
    NEUTRAL,
    /** Info — sky blue tint. */
    INFO,
    /** Success — emerald tint. */
    SUCCESS,
    /** Warning — tangerine tint. */
    WARNING,
    /** Danger — rose tint, for error states. */
    DANGER,
}

/** Size bucket of an [ElTag]. */
enum class ElTagSize {
    /** 10sp text, tight padding — inline badges. */
    SM,
    /** 12sp text, comfortable padding — standalone chips. */
    MD,
}

/** Severity of an [ElAlertBanner] — drives background tint and leading icon. */
enum class ElAlertSeverity {
    /** Info — sky blue. */
    INFO,
    /** Success — emerald. */
    SUCCESS,
    /** Warning — tangerine. */
    WARNING,
    /** Danger — rose. */
    DANGER,
}

/**
 * Gradient family for an [ElGradientStatCard].
 *
 * Each value maps to a two-color linear gradient pair resolved at
 * composition time via [elGradientPair].
 */
enum class ElGradient {
    /** Brand violet pair — generic KPI. */
    BRAND,
    /** Revenue — emerald pair (cash-in). */
    REVENUE,
    /** Debt — rose pair (owed). */
    DEBT,
    /** Attendance — sky blue pair. */
    ATTENDANCE,
    /** Success — emerald bright pair. */
    SUCCESS,
    /** Warning — tangerine pair. */
    WARNING,
    /** Danger — deep rose pair. */
    DANGER,
}

/** Severity of an [ElSnackbar] — drives background tint and accent. */
enum class ElSnackbarSeverity {
    /** Info — surface with primary accent. */
    INFO,
    /** Success — emerald tinted. */
    SUCCESS,
    /** Warning — tangerine tinted. */
    WARNING,
    /** Danger — rose tinted. */
    DANGER,
}
