package com.azimulkabir.actua.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Component sizes shared by the form cards, grouped lists and action buttons, so rows and
 * buttons have the same height and dividers line up with the text on every screen.
 */
object Sizes {
    /** Minimum height of an icon row in a form card. */
    val formRowMinHeight: Dp = 64.dp

    /** Minimum height of a row in a dense grouped list (accounts, budget categories). */
    val compactRowMinHeight: Dp = 56.dp

    /** Start inset of a divider inside a card, lining it up with the text after a row icon. */
    val cardDividerInset: Dp = 56.dp

    /** Height of the full-width primary action pinned at the bottom of a form. */
    val primaryButtonHeight: Dp = 56.dp

    /** Height of a full-width secondary (tonal) action inside a form. */
    val secondaryButtonHeight: Dp = 52.dp
}
