package com.example.ui.features.settings

import com.example.core.Role
import com.example.ui.designsystem.components.display.ElTagTone

/**
 * Role → tag tone for the profile card.
 *
 * T-044 pass 2 (2026-09-03): replaced [roleColor] (a raw-Color helper consumed
 * only by the legacy ElTag) with this tone mapping for the design-system ElTag.
 * Semantic mapping of the old colors: blue → INFO, green → SUCCESS,
 * orange → WARNING, purple/grey → NEUTRAL (the design-system tag palette has
 * no purple; NEUTRAL keeps the tag legible without inventing a new tone).
 */
internal fun roleTone(role: Role): ElTagTone = when (role) {
    Role.SUPER_ADMIN, Role.MANAGER -> ElTagTone.INFO
    Role.FINANCIAL_OFFICER -> ElTagTone.SUCCESS
    Role.SUPPORT_STAFF -> ElTagTone.WARNING
    else -> ElTagTone.NEUTRAL
}

/** ISO 639-1 → display label. */
