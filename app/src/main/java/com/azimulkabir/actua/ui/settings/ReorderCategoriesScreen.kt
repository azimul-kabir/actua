package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.CategoryReorderPlanner
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup
import kotlin.math.roundToInt

private const val ROW_HEIGHT_DP = 56

/**
 * Flattened, index-addressable view of [groups] used for both the drag gesture's hit-testing
 * and the up/down accessible controls. A [ReorderRow.Group] always immediately precedes its own
 * [ReorderRow.Category] rows, so a drop target's row identity alone determines the destination group.
 */
private sealed interface ReorderRow {
    data class Group(val group: ActualCategoryGroup) : ReorderRow
    data class Category(val groupId: String, val id: String, val name: String, val hidden: Boolean) : ReorderRow
}

private fun flatten(groups: List<ActualCategoryGroup>): List<ReorderRow> = groups.flatMap { group ->
    listOf(ReorderRow.Group(group)) + group.categories.map { ReorderRow.Category(group.id, it.id, it.name, it.hidden) }
}

@Composable
fun ReorderCategoriesScreen(
    groups: List<ActualCategoryGroup>,
    onBack: () -> Unit,
    onMoveCategory: (CategoryReorderPlanner.CategoryMove) -> Unit,
    onMoveGroup: (CategoryReorderPlanner.GroupMove) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Local copy so each drag/button move applies instantly; it re-syncs to the persisted order
    // (identified by the id sequence) whenever that changes underneath it, e.g. after a sync.
    val orderKey = remember(groups) { groups.joinToString("|") { g -> g.id + ":" + g.categories.joinToString(",") { it.id } } }
    var localGroups by remember(orderKey) { mutableStateOf(groups) }
    val rows = remember(localGroups) { flatten(localGroups) }
    val listState = rememberLazyListState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT_DP.dp.toPx() }

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetPx by remember { mutableStateOf(0f) }

    fun commitCategoryMove(categoryId: String, groupId: String, beforeId: String?) {
        val result = CategoryReorderPlanner.moveCategory(localGroups, categoryId, groupId, beforeId) ?: return
        localGroups = result.first
        onMoveCategory(result.second)
    }

    fun commitGroupMove(groupId: String, beforeGroupId: String?) {
        val result = CategoryReorderPlanner.moveGroup(localGroups, groupId, beforeGroupId) ?: return
        localGroups = result.first
        onMoveGroup(result.second)
    }

    fun applyHover(fromIndex: Int, hoverIndex: Int) {
        val clamped = hoverIndex.coerceIn(0, rows.lastIndex)
        if (clamped == fromIndex) return
        when (val moving = rows[fromIndex]) {
            is ReorderRow.Group -> {
                val targetRow = rows.getOrNull(clamped)
                val targetGroupId = when (targetRow) {
                    is ReorderRow.Group -> targetRow.group.id
                    is ReorderRow.Category -> targetRow.groupId
                    null -> null
                }
                if (targetGroupId != null && targetGroupId != moving.group.id) commitGroupMove(moving.group.id, targetGroupId)
                else if (targetRow == null) commitGroupMove(moving.group.id, null)
            }
            is ReorderRow.Category -> {
                val targetRow = rows.getOrNull(clamped)
                when (targetRow) {
                    is ReorderRow.Group -> commitCategoryMove(moving.id, targetRow.group.id, targetRow.group.categories.firstOrNull()?.id)
                    is ReorderRow.Category -> commitCategoryMove(moving.id, targetRow.groupId, targetRow.id.takeUnless { it == moving.id })
                    null -> {
                        val lastGroup = localGroups.lastOrNull() ?: return
                        commitCategoryMove(moving.id, lastGroup.id, null)
                    }
                }
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
            Text("Reorder Categories", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        Text(
            "Drag a handle to reorder, or use the up/down arrows. Categories can be dragged into another group.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(rows.size, key = { index ->
                when (val row = rows[index]) {
                    is ReorderRow.Group -> "group:${row.group.id}"
                    is ReorderRow.Category -> "category:${row.id}"
                }
            }) { index ->
                val row = rows[index]
                val isDragging = draggingIndex == index
                val rowModifier = Modifier
                    .fillMaxWidth()
                    .height(ROW_HEIGHT_DP.dp)
                    .graphicsLayer { translationY = if (isDragging) dragOffsetPx else 0f }
                    .alpha(if (isDragging) 0.85f else 1f)
                when (row) {
                    is ReorderRow.Group -> GroupReorderRow(
                        row.group,
                        modifier = rowModifier,
                        onDragStart = { draggingIndex = index; dragOffsetPx = 0f },
                        onDrag = { deltaY ->
                            dragOffsetPx += deltaY
                            applyHover(index, index + (dragOffsetPx / rowHeightPx).roundToInt())
                        },
                        onDragEnd = { draggingIndex = null; dragOffsetPx = 0f },
                        onMoveUp = { CategoryReorderPlanner.moveGroupUp(localGroups, row.group.id)?.let { (g, m) -> localGroups = g; onMoveGroup(m) } },
                        onMoveDown = { CategoryReorderPlanner.moveGroupDown(localGroups, row.group.id)?.let { (g, m) -> localGroups = g; onMoveGroup(m) } },
                    )
                    is ReorderRow.Category -> CategoryReorderRow(
                        row,
                        modifier = rowModifier,
                        onDragStart = { draggingIndex = index; dragOffsetPx = 0f },
                        onDrag = { deltaY ->
                            dragOffsetPx += deltaY
                            applyHover(index, index + (dragOffsetPx / rowHeightPx).roundToInt())
                        },
                        onDragEnd = { draggingIndex = null; dragOffsetPx = 0f },
                        onMoveUp = { CategoryReorderPlanner.moveCategoryUp(localGroups, row.id)?.let { (g, m) -> localGroups = g; onMoveCategory(m) } },
                        onMoveDown = { CategoryReorderPlanner.moveCategoryDown(localGroups, row.id)?.let { (g, m) -> localGroups = g; onMoveCategory(m) } },
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun GroupReorderRow(
    group: ActualCategoryGroup,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = modifier) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!group.isIncome) {
                Icon(
                    Icons.Outlined.DragHandle,
                    contentDescription = "Drag to reorder ${group.name} group",
                    modifier = Modifier
                        .size(28.dp)
                        .pointerInput(group.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDragEnd = { onDragEnd() },
                                onDragCancel = { onDragEnd() },
                                onDrag = { change, offset -> change.consume(); onDrag(offset.y) },
                            )
                        },
                )
            } else Box(Modifier.size(28.dp))
            Text(
                group.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
            if (group.hidden) Icon(Icons.Outlined.VisibilityOff, "Hidden group", modifier = Modifier.size(18.dp).padding(end = 8.dp))
            if (!group.isIncome) {
                IconButton(onClick = onMoveUp, modifier = Modifier.semantics { contentDescription = "Move ${group.name} group up" }) {
                    Icon(Icons.Outlined.KeyboardArrowUp, null)
                }
                IconButton(onClick = onMoveDown, modifier = Modifier.semantics { contentDescription = "Move ${group.name} group down" }) {
                    Icon(Icons.Outlined.KeyboardArrowDown, null)
                }
            }
        }
    }
}

@Composable
private fun CategoryReorderRow(
    row: ReorderRow.Category,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
) {
    Row(modifier.padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Outlined.DragHandle,
            contentDescription = "Drag to reorder ${row.name}",
            modifier = Modifier
                .size(24.dp)
                .pointerInput(row.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onDrag = { change, offset -> change.consume(); onDrag(offset.y) },
                    )
                },
        )
        Text(
            row.name,
            style = MaterialTheme.typography.bodyLarge,
            overflow = TextOverflow.Ellipsis,
            color = if (row.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        if (row.hidden) Icon(Icons.Outlined.VisibilityOff, "Hidden category", modifier = Modifier.size(16.dp).padding(end = 8.dp))
        IconButton(onClick = onMoveUp, modifier = Modifier.semantics { contentDescription = "Move ${row.name} category up" }) {
            Icon(Icons.Outlined.KeyboardArrowUp, null)
        }
        IconButton(onClick = onMoveDown, modifier = Modifier.semantics { contentDescription = "Move ${row.name} category down" }) {
            Icon(Icons.Outlined.KeyboardArrowDown, null)
        }
    }
}
