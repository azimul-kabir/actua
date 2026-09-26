package com.azimulkabir.actua.ui.accounts

import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.AccountDragReorder
import com.azimulkabir.actua.data.budget.AccountReorderPlanner
import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.dragReorderHandle
import kotlinx.coroutines.launch

private const val ROW_HEIGHT_DP = 56
private const val EDGE_SCROLL_ZONE_PX = 100f
private const val EDGE_SCROLL_SPEED_PX = 14f

/** Dedicated drag-to-reorder screen for the accounts list (issue #597). */
@Composable
fun ReorderAccountsScreen(
    accounts: List<ActualAccount>,
    onBack: () -> Unit,
    onMoveAccount: (AccountReorderPlanner.AccountMove) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val orderKey = remember(accounts) { accounts.joinToString("|") { it.id } }
    var localAccounts by remember { mutableStateOf(accounts) }
    var draggingAccountId by remember { mutableStateOf<String?>(null) }
    var dragStartAccounts by remember { mutableStateOf(accounts) }
    var dragOffsetPx by remember { mutableStateOf(0f) }
    LaunchedEffect(orderKey) {
        if (draggingAccountId == null) localAccounts = accounts
    }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val rowHeightPx = with(density) { ROW_HEIGHT_DP.dp.toPx() }

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
        dragStartAccounts = localAccounts
        dragOffsetPx = 0f
    }

    fun onDrag(accountId: String, deltaY: Float) {
        dragOffsetPx += deltaY
        while (dragOffsetPx > rowHeightPx / 2) {
            val stepped = AccountReorderPlanner.moveAccountDown(localAccounts, accountId)?.first
            if (stepped != null) { localAccounts = stepped; dragOffsetPx -= rowHeightPx } else { dragOffsetPx = rowHeightPx / 2; break }
        }
        while (dragOffsetPx < -rowHeightPx / 2) {
            val stepped = AccountReorderPlanner.moveAccountUp(localAccounts, accountId)?.first
            if (stepped != null) { localAccounts = stepped; dragOffsetPx += rowHeightPx } else { dragOffsetPx = -rowHeightPx / 2; break }
        }
        val direction = autoScrollDirection(accountId)
        if (direction != 0) scope.launch { listState.scrollBy(direction * EDGE_SCROLL_SPEED_PX) }
    }

    fun onDragEnd(accountId: String) {
        if (AccountDragReorder.hasMoved(dragStartAccounts, localAccounts, accountId)) {
            val move = AccountDragReorder.finalMove(localAccounts, accountId)
            if (move != null && !onMoveAccount(move)) localAccounts = dragStartAccounts
        }
        draggingAccountId = null
        dragOffsetPx = 0f
    }

    fun stepByButton(accountId: String, direction: Int) {
        val stepped = if (direction < 0) AccountReorderPlanner.moveAccountUp(localAccounts, accountId)?.first
            else AccountReorderPlanner.moveAccountDown(localAccounts, accountId)?.first
        if (stepped == null) return
        val move = AccountDragReorder.finalMove(stepped, accountId) ?: return
        if (onMoveAccount(move)) localAccounts = stepped
    }

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = "Reorder Accounts", onBack = onBack)
        Text(
            "Drag a handle to reorder, or use the up/down arrows.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            items(localAccounts.size, key = { index -> "account:${localAccounts[index].id}" }) { index ->
                val account = localAccounts[index]
                val isDragging = draggingAccountId == account.id
                AccountReorderRow(
                    account = account,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT_DP.dp)
                        .graphicsLayer { translationY = if (isDragging) dragOffsetPx else 0f }
                        .alpha(if (isDragging) 0.85f else 1f),
                    onDragStart = { onDragStart(account.id) },
                    onDrag = { deltaY -> onDrag(account.id, deltaY) },
                    onDragEnd = { onDragEnd(account.id) },
                    onMoveUp = { stepByButton(account.id, -1) },
                    onMoveDown = { stepByButton(account.id, +1) },
                    canMoveUp = index > 0,
                    canMoveDown = index < localAccounts.size - 1,
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun AccountReorderRow(
    account: ActualAccount,
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
