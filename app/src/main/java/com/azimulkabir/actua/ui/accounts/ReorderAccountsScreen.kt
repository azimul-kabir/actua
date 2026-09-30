package com.azimulkabir.actua.ui.accounts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.AccountDragReorder
import com.azimulkabir.actua.data.budget.AccountReorderPlanner
import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountGroup
import com.azimulkabir.actua.ui.components.ActuaGroupedItem
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.GroupPosition
import com.azimulkabir.actua.ui.components.dragReorderHandle
import com.azimulkabir.actua.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val ROW_HEIGHT_DP = 56
private const val EDGE_SCROLL_ZONE_PX = 100f
private const val EDGE_SCROLL_SPEED_PX = 14f
private const val CLOSED_SECTION_TITLE = "Closed accounts"

/**
 * One reorderable bucket of accounts on the Reorder Accounts screen: a section
 * (On budget / Off budget / Closed accounts) optionally split further into one of
 * Actual's experimental account groups. Dragging or stepping an account only ever
 * reorders it within its own chunk — it never crosses a section or group boundary,
 * since [ActualAccount.sortOrder] is a single shared field and a stray move here
 * would silently reshuffle accounts the user never touched.
 */
internal data class ReorderChunk(
    val sectionTitle: String,
    val groupName: String?,
    val accounts: List<ActualAccount>,
) {
    val key: String get() = "$sectionTitle|${groupName ?: ""}"
}

/** Splits accounts into section chunks, then splits each section by account group, mirroring AccountsScreen. */
internal fun buildReorderChunks(accounts: List<ActualAccount>, groups: List<ActualAccountGroup>): List<ReorderChunk> {
    val groupsById = groups.associateBy { it.id }
    val sections = listOf(
        "On budget" to accounts.filter { !it.offBudget && !it.closed },
        "Off budget" to accounts.filter { it.offBudget && !it.closed },
        CLOSED_SECTION_TITLE to accounts.filter { it.closed },
    ).filter { (_, sectionAccounts) -> sectionAccounts.isNotEmpty() }

    return sections.flatMap { (title, sectionAccounts) ->
        if (sectionAccounts.none { it.groupId != null }) {
            listOf(ReorderChunk(title, null, sectionAccounts))
        } else {
            val grouped = sectionAccounts.groupBy { it.groupId }
            val orderedGroupIds = grouped.keys.filterNotNull()
                .sortedWith(compareBy({ groupsById[it]?.sortOrder ?: 0.0 }, { it }))
            val groupChunks = orderedGroupIds.map { groupId ->
                ReorderChunk(title, groupsById[groupId]?.name ?: "Group", grouped.getValue(groupId))
            }
            val ungrouped = grouped[null].orEmpty()
            if (ungrouped.isEmpty()) groupChunks else groupChunks + ReorderChunk(title, null, ungrouped)
        }
    }
}

/** Each account's 1-based position within its section, counted across all of that section's groups. */
internal fun serialsBySection(chunks: List<ReorderChunk>): Map<String, Int> {
    val serials = mutableMapOf<String, Int>()
    chunks.groupBy { it.sectionTitle }.forEach { (_, sectionChunks) ->
        var position = 1
        sectionChunks.forEach { chunk -> chunk.accounts.forEach { serials[it.id] = position++ } }
    }
    return serials
}

/** Dedicated drag-to-reorder screen for the accounts list (issue #597, grouping/collapsing in #617). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReorderAccountsScreen(
    accounts: List<ActualAccount>,
    onBack: () -> Unit,
    onMoveAccount: (AccountReorderPlanner.AccountMove) -> Boolean,
    modifier: Modifier = Modifier,
    groups: List<ActualAccountGroup> = emptyList(),
) {
    val orderKey = remember(accounts, groups) {
        accounts.joinToString("|") { it.id } + "::" + groups.joinToString("|") { "${it.id}:${it.sortOrder}" }
    }
    var localChunks by remember { mutableStateOf(buildReorderChunks(accounts, groups)) }
    var draggingAccountId by remember { mutableStateOf<String?>(null) }
    var dragStartChunks by remember { mutableStateOf(localChunks) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    var collapsedSections by remember { mutableStateOf(setOf(CLOSED_SECTION_TITLE)) }
    var collapsedGroups by remember { mutableStateOf(emptySet<String>()) }
    LaunchedEffect(orderKey) {
        if (draggingAccountId == null) localChunks = buildReorderChunks(accounts, groups)
    }
    val serials = remember(localChunks) { serialsBySection(localChunks) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT_DP.dp.toPx() }

    fun chunkIndexOf(accountId: String) = localChunks.indexOfFirst { chunk -> chunk.accounts.any { it.id == accountId } }

    fun autoScrollDirection(accountId: String): Int {
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == "account:$accountId" } ?: return 0
        val top = item.offset + dragOffsetPx
        val bottom = top + item.size
        return when {
            top < EDGE_SCROLL_ZONE_PX -> -1
            bottom > info.viewportEndOffset - EDGE_SCROLL_ZONE_PX -> 1
            else -> 0
        }
    }

    fun onDragStart(accountId: String) {
        draggingAccountId = accountId
        dragStartChunks = localChunks
        dragOffsetPx = 0f
    }

    fun onDrag(accountId: String, deltaY: Float) {
        dragOffsetPx += deltaY
        while (dragOffsetPx > rowHeightPx / 2) {
            val chunkIndex = chunkIndexOf(accountId)
            val chunk = localChunks.getOrNull(chunkIndex)
            val stepped = chunk?.let { AccountReorderPlanner.moveAccountDown(it.accounts, accountId)?.first }
            if (chunk != null && stepped != null) {
                localChunks = localChunks.toMutableList().apply { this[chunkIndex] = chunk.copy(accounts = stepped) }
                dragOffsetPx -= rowHeightPx
            } else { dragOffsetPx = rowHeightPx / 2; break }
        }
        while (dragOffsetPx < -rowHeightPx / 2) {
            val chunkIndex = chunkIndexOf(accountId)
            val chunk = localChunks.getOrNull(chunkIndex)
            val stepped = chunk?.let { AccountReorderPlanner.moveAccountUp(it.accounts, accountId)?.first }
            if (chunk != null && stepped != null) {
                localChunks = localChunks.toMutableList().apply { this[chunkIndex] = chunk.copy(accounts = stepped) }
                dragOffsetPx += rowHeightPx
            } else { dragOffsetPx = -rowHeightPx / 2; break }
        }
        val direction = autoScrollDirection(accountId)
        if (direction != 0) scope.launch { listState.scrollBy(direction * EDGE_SCROLL_SPEED_PX) }
    }

    fun onDragEnd(accountId: String) {
        val chunkIndex = chunkIndexOf(accountId)
        val originalAccounts = dragStartChunks.getOrNull(chunkIndex)?.accounts
        val currentAccounts = localChunks.getOrNull(chunkIndex)?.accounts
        if (originalAccounts != null && currentAccounts != null && AccountDragReorder.hasMoved(originalAccounts, currentAccounts, accountId)) {
            val move = AccountDragReorder.finalMove(currentAccounts, accountId)
            if (move != null && !onMoveAccount(move)) localChunks = dragStartChunks
        }
        draggingAccountId = null
        dragOffsetPx = 0f
    }

    fun stepByButton(accountId: String, direction: Int) {
        val chunkIndex = chunkIndexOf(accountId)
        val chunk = localChunks.getOrNull(chunkIndex) ?: return
        val stepped = if (direction < 0) AccountReorderPlanner.moveAccountUp(chunk.accounts, accountId)?.first
            else AccountReorderPlanner.moveAccountDown(chunk.accounts, accountId)?.first
        if (stepped == null) return
        val move = AccountDragReorder.finalMove(stepped, accountId) ?: return
        if (onMoveAccount(move)) localChunks = localChunks.toMutableList().apply { this[chunkIndex] = chunk.copy(accounts = stepped) }
    }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = "Reorder Accounts", onBack = onBack)
        Text(
            "Drag a handle to reorder, or use the up/down arrows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Spacing.xl),
        ) {
            val sectionTitles = localChunks.map { it.sectionTitle }.distinct()
            sectionTitles.forEach { sectionTitle ->
                val sectionCollapsed = sectionTitle in collapsedSections
                stickyHeader(key = "section:$sectionTitle") {
                    ReorderSectionHeader(
                        title = sectionTitle,
                        collapsed = sectionCollapsed,
                        onClick = {
                            collapsedSections = if (sectionCollapsed) collapsedSections - sectionTitle
                            else collapsedSections + sectionTitle
                        },
                    )
                }
                localChunks.filter { it.sectionTitle == sectionTitle }.forEach { chunk ->
                    val groupCollapsed = chunk.groupName != null && chunk.key in collapsedGroups
                    if (chunk.groupName != null) {
                        item(key = "group:${chunk.key}") {
                            AnimatedVisibility(visible = !sectionCollapsed) {
                                ReorderGroupHeader(
                                    name = chunk.groupName,
                                    collapsed = groupCollapsed,
                                    onClick = {
                                        collapsedGroups = if (groupCollapsed) collapsedGroups - chunk.key
                                        else collapsedGroups + chunk.key
                                    },
                                )
                            }
                        }
                    }
                    if (!sectionCollapsed && !groupCollapsed) {
                        chunk.accounts.forEachIndexed { index, account ->
                            item(key = "account:${account.id}") {
                                val isDragging = draggingAccountId == account.id
                                ActuaGroupedItem(
                                    position = GroupPosition.of(index, chunk.accounts.size),
                                    modifier = Modifier
                                        .graphicsLayer { translationY = if (isDragging) dragOffsetPx else 0f }
                                        .alpha(if (isDragging) 0.85f else 1f),
                                ) {
                                    AccountReorderRow(
                                        account = account,
                                        serial = serials[account.id] ?: (index + 1),
                                        modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT_DP.dp),
                                        onDragStart = { onDragStart(account.id) },
                                        onDrag = { deltaY -> onDrag(account.id, deltaY) },
                                        onDragEnd = { onDragEnd(account.id) },
                                        onMoveUp = { stepByButton(account.id, -1) },
                                        onMoveDown = { stepByButton(account.id, +1) },
                                        canMoveUp = index > 0,
                                        canMoveDown = index < chunk.accounts.size - 1,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReorderSectionHeader(title: String, collapsed: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, tween(220), label = "reorder section")
    // Sticky, so it keeps an opaque page background under the label rather than a tinted bar.
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = Spacing.screenHorizontal, end = Spacing.screenHorizontal, top = Spacing.lg, bottom = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).padding(start = Spacing.xs),
            )
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (collapsed) "Expand $title" else "Collapse $title",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.rotate(rotation),
            )
        }
    }
}

@Composable
private fun ReorderGroupHeader(name: String, collapsed: Boolean, onClick: () -> Unit) {
    val rotation by animateFloatAsState(if (collapsed) -90f else 0f, tween(220), label = "reorder group")
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(start = Spacing.screenHorizontal + Spacing.xs, end = Spacing.screenHorizontal, top = Spacing.md, bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.KeyboardArrowDown,
                contentDescription = if (collapsed) "Expand $name" else "Collapse $name",
                modifier = Modifier.size(18.dp).rotate(rotation),
            )
            Text(
                name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun AccountReorderRow(
    account: ActualAccount,
    serial: Int,
    modifier: Modifier = Modifier,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
) {
    Row(modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "$serial.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(28.dp),
        )
        Icon(
            Icons.Outlined.DragHandle,
            contentDescription = "Drag to reorder ${account.name} account",
            modifier = Modifier
                .size(44.dp)
                .padding(8.dp)
                .dragReorderHandle(
                    key = account.id,
                    onDragStart = onDragStart,
                    onDrag = onDrag,
                    onDragEnd = onDragEnd,
                ),
        )
        Text(
            account.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        IconButton(
            onClick = onMoveUp,
            enabled = canMoveUp,
            modifier = Modifier.semantics { contentDescription = "Move ${account.name} account up" },
        ) { Icon(Icons.Outlined.KeyboardArrowUp, null) }
        IconButton(
            onClick = onMoveDown,
            enabled = canMoveDown,
            modifier = Modifier.semantics { contentDescription = "Move ${account.name} account down" },
        ) { Icon(Icons.Outlined.KeyboardArrowDown, null) }
    }
}
