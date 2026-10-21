package com.azimulkabir.actua.ui.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Height of the pill with labels under the icons, and without them. */
internal val FloatingTabBarLabeledHeight = 66.dp
internal val FloatingTabBarIconOnlyHeight = 56.dp
private val FloatingTabBarSideMargin = 12.dp
private const val MaxLabelFontScale = 1.3f
private val FloatingTabBarBottomMargin = 8.dp

/**
 * Bottom padding a scrolling screen needs so its last row clears the floating pill. Root screens
 * draw behind the (transparent) area around the pill, so the pill's height, its margins and the
 * system navigation inset all count.
 */
@Composable
fun floatingBarClearance(): Dp =
    FloatingTabBarLabeledHeight + FloatingTabBarBottomMargin + 8.dp +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/**
 * A rounded pill that floats above the system navigation area, in place of a full-width bar.
 * Items are laid out by [FloatingNavigationBarItem], so the tab list, its order and the Add item
 * come from the caller (Customize tab bar) exactly as they did with Material's `NavigationBar`.
 *
 * The bar sits in the Scaffold's bottom slot, so screens end above it; the area around the pill is
 * transparent and shows the screen background.
 */
@Composable
internal fun FloatingNavigationBar(
    showLabels: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val height: Dp = if (showLabels) FloatingTabBarLabeledHeight else FloatingTabBarIconOnlyHeight
    // The pill has a fixed height, so its labels stop growing past 130% font scale; at 200% they
    // would overflow it. The labels remain the accessibility names regardless of size.
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, minOf(density.fontScale, MaxLabelFontScale))) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(start = FloatingTabBarSideMargin, end = FloatingTabBarSideMargin, bottom = FloatingTabBarBottomMargin),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(height),
            shape = RoundedCornerShape(height / 2),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Row(
                Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 4.dp).selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                content = content,
            )
        }
    }
    }
}

/**
 * One tab of the [FloatingNavigationBar]. Same contract as Material's `NavigationBarItem`: a
 * selectable tab with an icon and an optional label; the selected tab sits in a highlight capsule.
 * Unlabeled items keep their accessibility name through the icon's content description.
 */
@Composable
internal fun RowScope.FloatingNavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: (@Composable () -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    // The highlight fills the pill's inner height with fully round ends, so its curve follows the
    // pill's own (concentric, inset by the item padding) instead of being a separate rounded box.
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
        contentAlignment = Alignment.Center,
    ) {
        // Background, not Surface: a Surface clips its content to the capsule, which would shave
        // the ends off a wide label such as "Transactions".
        val container = if (selected) colors.primaryContainer else Color.Transparent
        val content = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant
        CompositionLocalProvider(LocalContentColor provides content) {
            Column(
                Modifier.fillMaxSize().background(container, CircleShape),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                icon()
                label?.invoke()
            }
        }
    }
}
