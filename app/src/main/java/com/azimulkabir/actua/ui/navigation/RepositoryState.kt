package com.azimulkabir.actua.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.azimulkabir.actua.data.ActuaRepository
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.ui.settings.BudgetSelectionItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class RepositoryState(
    val version: Int,
    val repository: ActuaRepository,
    val budgets: List<BudgetSelectionItem>,
)

/** Retain the previous screen behind the loading dialog until its replacement is ready. */
@Composable
internal fun rememberRepository(context: Context, version: Int, onLoaded: () -> Unit = {}): RepositoryState? {
    val onRepositoryLoaded by rememberUpdatedState(onLoaded)
    var loaded by remember { mutableStateOf<RepositoryState?>(null) }
    LaunchedEffect(context, version) {
        var pending: ActuaRepository? = null
        try {
            val replacement = withContext(Dispatchers.IO) {
                val repository = ActuaRepository(context).also { pending = it }
                RepositoryState(version, repository, BudgetFileManager(context).listLocalBudgets().map {
                    BudgetSelectionItem(it.id, it.budgetName ?: it.id)
                })
            }
            // Invalidate projections in the same state update that publishes the new repository.
            onRepositoryLoaded()
            loaded = replacement
            pending = null
        } finally {
            // withContext may be cancelled after construction but before delivering the result.
            pending?.close()
        }
    }
    val repository = loaded?.repository
    DisposableEffect(repository) {
        onDispose { repository?.close() }
    }
    return loaded
}
