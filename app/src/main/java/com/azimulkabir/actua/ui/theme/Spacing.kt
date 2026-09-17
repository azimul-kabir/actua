package com.azimulkabir.actua.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small, app-wide spacing scale. Screens should reach for these instead of
 * hard-coded padding/margin values so horizontal margins, section gaps and
 * row insets stay consistent across Budget, Accounts, Transactions, Reports,
 * Manage and Settings.
 */
object Spacing {
    val none: Dp = 0.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 24.dp
    val xxl: Dp = 32.dp

    /** Standard left/right margin for screen content. */
    val screenHorizontal: Dp = lg

    /** Vertical gap between distinct sections on a screen. */
    val sectionGap: Dp = xl

    /** Vertical padding inside a standard list row. */
    val rowVertical: Dp = md
}
