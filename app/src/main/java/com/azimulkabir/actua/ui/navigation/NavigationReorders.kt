package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.budget.AccountGroupReorderPlanner
import com.azimulkabir.actua.data.budget.AccountReorderPlanner
import com.azimulkabir.actua.data.budget.CategoryReorderPlanner
import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountGroup
import com.azimulkabir.actua.data.budget.model.ActualCategoryGroup

internal data class ReorderSnapshot(
    val categories: List<ActualCategoryGroup> = emptyList(),
    val accounts: List<ActualAccount> = emptyList(),
    val accountGroups: List<ActualAccountGroup> = emptyList(),
)

internal class NavigationReorders(private val repository: ActuaRepository, val queue: ReorderQueue<ReorderSnapshot>) {
    fun category(move: CategoryReorderPlanner.CategoryMove) = queue.submit(R.string.navigation_reordering_category, { state ->
        state.copy(categories = CategoryReorderPlanner.moveCategory(state.categories, move.categoryId, move.groupId, move.beforeCategoryId)?.first ?: state.categories)
    }) { repository.moveCategory(move) }

    fun categoryGroup(move: CategoryReorderPlanner.GroupMove) = queue.submit(R.string.navigation_reordering_category_group, { state ->
        state.copy(categories = CategoryReorderPlanner.moveGroup(state.categories, move.groupId, move.beforeGroupId)?.first ?: state.categories)
    }) { repository.moveCategoryGroup(move) }

    fun account(move: AccountReorderPlanner.AccountMove) = queue.submit(R.string.navigation_reordering_account, { state ->
        val ordered = AccountReorderPlanner.moveAccount(state.accounts, move.accountId, move.beforeAccountId)?.first ?: state.accounts
        state.copy(accounts = if (move.updateGroupId) ordered.map {
            if (it.id == move.accountId) it.copy(groupId = move.targetGroupId) else it
        } else ordered)
    }) { repository.moveAccount(move) }

    fun accountGroup(move: AccountGroupReorderPlanner.GroupMove) = queue.submit(R.string.navigation_reordering_account_group, { state ->
        val ordered = AccountGroupReorderPlanner.moveGroup(state.accountGroups, move.groupId, move.beforeGroupId)?.first ?: state.accountGroups
        // Group headers are sorted by sortOrder when chunks are built; give the optimistic order matching ranks.
        state.copy(accountGroups = ordered.mapIndexed { index, group -> group.copy(sortOrder = index.toDouble()) })
    }) { repository.moveAccountGroup(move) }
}

@Composable
internal fun rememberNavigationReorders(
    repository: ActuaRepository,
    mutations: NavigationMutationRunner,
    onChanged: () -> Unit,
): NavigationReorders {
    val scope = rememberCoroutineScope()
    val latestMutations by rememberUpdatedState(mutations)
    val latestChanged by rememberUpdatedState(onChanged)
    val reorders = remember(repository) {
        NavigationReorders(repository, ReorderQueue(
            parentScope = scope,
            initial = ReorderSnapshot(),
            read = { ReorderSnapshot(repository.categoryGroupsForReorder(), repository.accountsForReorder(), repository.accountGroupsForReorder().sortedWith(compareBy({ it.sortOrder }, { it.id }))) },
            onChanged = { latestChanged() },
            onFailure = { label, error -> latestMutations.reorderFailed(label ?: R.string.navigation_loading_budget, error) },
            repository = repository,
        ))
    }
    DisposableEffect(reorders) { onDispose { reorders.queue.close() } }
    return reorders
}
