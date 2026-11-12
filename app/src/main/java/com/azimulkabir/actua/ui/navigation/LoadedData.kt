package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.TransactionStatusFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads [load] on [Dispatchers.IO] and returns its latest result, starting with [initial].
 *
 * The previous result stays on screen while a reload runs, so an edit or sync never flashes
 * empty content. [budgetKey] identifies the open budget: changing it discards the previous
 * value and cancels any load still running for the old budget, so one budget's data is never
 * shown under another. [reloadKeys] (a data version, a filter) restart the load but keep the
 * previous value. While [enabled] is false nothing is loaded and the last value is kept.
 */
@Composable
internal fun <T> rememberLoaded(
    budgetKey: Any,
    vararg reloadKeys: Any?,
    initial: T,
    enabled: Boolean = true,
    load: () -> T,
): T {
    var value by remember(budgetKey) { mutableStateOf(initial) }
    LaunchedEffect(budgetKey, enabled, *reloadKeys) {
        if (enabled) value = withContext(Dispatchers.IO) { load() }
    }
    return value
}

/**
 * The transaction list for [filter]. The unfiltered history is already in [all], so the filter
 * is only loaded separately when it narrows the list.
 */
internal fun transactionsForStatusFilter(
    filter: TransactionStatusFilter,
    all: List<Transaction>,
    filtered: List<Transaction>,
): List<Transaction> = if (filter == TransactionStatusFilter.ALL) all else filtered
