package com.azimulkabir.actua.ui.navigation

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.PieChartOutline
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.navigation.TabBarLayout
import com.azimulkabir.actua.data.navigation.TabBarLayoutPlanner
import com.azimulkabir.actua.data.navigation.TabItem
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.dragReorderHandle
import kotlinx.coroutines.delay

private const val ROW_HEIGHT_DP = 64
private const val EDGE_SCROLL_ZONE_PX = 100f
private const val EDGE_SCROLL_SPEED_PX = 14f
private const val EDGE_SCROLL_POLL_DELAY_MS = 16L

private fun TabItem.icon(): ImageVector = when (this) {
    TabItem.HOME -> Icons.Outlined.Home
    TabItem.BUDGET -> Icons.Outlined.PieChartOutline
    TabItem.TRANSACTIONS -> Icons.Outlined.ReceiptLong
    TabItem.ACCOUNTS -> Icons.Outlined.AccountBalanceWallet
    TabItem.REPORTS -> Icons.Outlined.BarChart
    TabItem.ADD -> Icons.Outlined.Add
    TabItem.MANAGE -> Icons.Outlined.Tune
}

/**
 * Show/hide and reorder the bottom tab bar. Manage is pinned and always visible (see
 * [TabBarLayoutPlanner]), so it renders without a drag handle or visibility switch.
 *
 * Drag mutates [localOrder] in memory only; [onLayoutChange] fires once per committed change
 * (drag end, a switch flip, an arrow tap, or Restore Defaults) rather than on every row crossed
 * during a drag — mirroring [com.azimulkabir.actua.ui.home.CustomizeHomeScreen], which this screen
 * is deliberately modeled on.
 */
@Composable
fun CustomizeTabBarScreen(
    layout: TabBarLayout,
    onBack: () -> Unit,
    onLayoutChange: (TabBarLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sanitized = remember(layout) { TabBarLayoutPlanner.sanitize(layout) }
    var localOrder by remember(sanitized) { mutableStateOf(sanitized.order) }
    var localHidden by remember(sanitized) { mutableStateOf(sanitized.hidden) }
    var draggingTab by remember { mutableStateOf<TabItem?>(null) }
    var dragStartOrder by remember { mutableStateOf(sanitized.order) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    LaunchedEffect(sanitized) {
        if (draggingTab == null) {
            localOrder = sanitized.order
            localHidden = sanitized.hidden
        }
    }

    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT_DP.dp.toPx() }

    fun autoScrollDirection(tabItem: TabItem): Int {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == tabItem.name } ?: return 0
        val top = item.offset + dragOffsetPx
        val bottom = top + item.size
        return when {
            top < EDGE_SCROLL_ZONE_PX -> -1
            bottom > info.viewportEndOffset - EDGE_SCROLL_ZONE_PX -> 1
            else -> 0
        }
    }

    fun checkSwaps(tabItem: TabItem) {
        while (dragOffsetPx > rowHeightPx / 2) {
            val stepped = TabBarLayoutPlanner.moveTabDown(localOrder, tabItem)
            if (stepped != null) { localOrder = stepped; dragOffsetPx -= rowHeightPx } else { dragOffsetPx = rowHeightPx / 2; break }
        }
        while (dragOffsetPx < -rowHeightPx / 2) {
            val stepped = TabBarLayoutPlanner.moveTabUp(localOrder, tabItem)
            if (stepped != null) { localOrder = stepped; dragOffsetPx += rowHeightPx } else { dragOffsetPx = -rowHeightPx / 2; break }
        }
    }

    fun onDragStart(tabItem: TabItem) {
        draggingTab = tabItem
        dragStartOrder = localOrder
        dragOffsetPx = 0f
    }

    fun onDrag(tabItem: TabItem, deltaY: Float) {
        dragOffsetPx += deltaY
        checkSwaps(tabItem)
    }

    fun onDragEnd(tabItem: TabItem) {
        if (TabBarLayoutPlanner.hasMoved(dragStartOrder, localOrder, tabItem)) {
            onLayoutChange(TabBarLayout(localOrder, localHidden))
        }
        draggingTab = null
        dragOffsetPx = 0f
    }

    // A single continuous loop (rather than a scrollBy launched per drag delta, which would flood
    // the dispatcher with concurrent coroutines) so the list keeps scrolling and the dragged row
    // keeps swapping with its new neighbors even while the finger holds still at the edge. Feeding
    // the actual scrolled amount back into dragOffsetPx keeps the row glued to the finger instead
    // of drifting as the list's item offsets shift underneath it.
    LaunchedEffect(draggingTab) {
        val tabItem = draggingTab ?: return@LaunchedEffect
        while (true) {
            val direction = autoScrollDirection(tabItem)
            if (direction != 0) {
                val scrolled = listState.scrollBy(direction * EDGE_SCROLL_SPEED_PX)
                dragOffsetPx += scrolled
                checkSwaps(tabItem)
            }
            delay(EDGE_SCROLL_POLL_DELAY_MS)
        }
    }

    fun stepByButton(tabItem: TabItem, direction: Int) {
        val stepped = if (direction < 0) TabBarLayoutPlanner.moveTabUp(localOrder, tabItem)
            else TabBarLayoutPlanner.moveTabDown(localOrder, tabItem)
        if (stepped == null) return
        localOrder = stepped
        onLayoutChange(TabBarLayout(stepped, localHidden))
    }

    fun setHidden(tabItem: TabItem, hidden: Boolean) {
        val updated = TabBarLayoutPlanner.setHidden(TabBarLayout(localOrder, localHidden), tabItem, hidden)
        localOrder = updated.order
        localHidden = updated.hidden
        onLayoutChange(updated)
    }

    val reorderable = remember(localOrder) { localOrder.filterNot { it == TabItem.MANAGE } }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = "Customize Tab Bar", onBack = onBack) {
            IconButton(onClick = {
                val defaults = TabBarLayout.default()
                localOrder = defaults.order
                localHidden = defaults.hidden
                onLayoutChange(defaults)
            }) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = "Restore default tab bar")
            }
        }
        Text(
            "Drag a handle to reorder, or use the up/down arrows. Manage always stays available, " +
                "and 3 to 5 tabs must stay visible.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(localOrder, key = { it.name }) { tabItem ->
                val isDragging = draggingTab == tabItem
                val reorderIndex = reorderable.indexOf(tabItem)
                val hidden = tabItem in localHidden
                val currentLayout = TabBarLayout(localOrder, localHidden)
                val canToggle = TabBarLayoutPlanner.setHidden(currentLayout, tabItem, !hidden) != currentLayout
                TabItemRow(
                    tabItem = tabItem,
                    hidden = hidden,
                    canToggleHidden = canToggle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT_DP.dp)
                        .graphicsLayer { translationY = if (isDragging) dragOffsetPx else 0f }
                        .alpha(if (isDragging) 0.85f else 1f),
                    onDragStart = { onDragStart(tabItem) },
                    onDrag = { deltaY -> onDrag(tabItem, deltaY) },
                    onDragEnd = { onDragEnd(tabItem) },
                    onMoveUp = { stepByButton(tabItem, -1) },
                    onMoveDown = { stepByButton(tabItem, +1) },
                    onHiddenChange = { h -> setHidden(tabItem, h) },
                    canMoveUp = reorderIndex > 0,
                    canMoveDown = reorderIndex in 0 until reorderable.size - 1,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun TabItemRow(
    tabItem: TabItem,
    hidden: Boolean,
    canToggleHidden: Boolean,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onHiddenChange: (Boolean) -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    val pinned = tabItem == TabItem.MANAGE
    Row(modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (!pinned) {
            Icon(
                Icons.Outlined.DragHandle,
                contentDescription = "Drag to reorder ${tabItem.label}",
                modifier = Modifier
                    .size(44.dp)
                    .padding(8.dp)
                    .dragReorderHandle(
                        key = tabItem,
                        onDragStart = onDragStart,
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                    ),
            )
        } else {
            Box(Modifier.size(44.dp))
        }
        Icon(tabItem.icon(), contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                tabItem.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Ellipsis,
            )
            // The "+ Add" pseudo-tab is the one row whose effect isn't a screen it navigates to,
            // so it needs an explicit explanation the other rows don't.
            if (tabItem == TabItem.ADD) {
                Text(
                    "Replaces the floating + Transaction button",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!pinned) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.semantics { contentDescription = "Move ${tabItem.label} up" },
            ) { Icon(Icons.Outlined.KeyboardArrowUp, null) }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.semantics { contentDescription = "Move ${tabItem.label} down" },
            ) { Icon(Icons.Outlined.KeyboardArrowDown, null) }
            Switch(
                checked = !hidden,
                onCheckedChange = { checked -> onHiddenChange(!checked) },
                enabled = canToggleHidden,
                modifier = Modifier.semantics { contentDescription = "Show ${tabItem.label} in the bottom bar" },
            )
        } else {
            Text("Required", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
