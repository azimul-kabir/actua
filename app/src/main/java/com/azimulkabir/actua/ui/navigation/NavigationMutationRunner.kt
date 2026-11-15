package com.azimulkabir.actua.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.azimulkabir.actua.data.diagnostics.DiagnosticsLog
import com.azimulkabir.actua.ui.components.TransactionImpactCue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Feedback keeps the same navigation-composition lifetime as the original remember state. */
internal class NavigationMutationFeedback {
    val error = mutableStateOf<String?>(null)
    val impactCues = mutableStateOf<List<TransactionImpactCue>>(emptyList())
}

/**
 * Executes the existing mutation contracts without owning a repository or invalidation counter.
 * Constructed with current composition inputs so locale/repository callbacks cannot become stale.
 * The supplied composition scope still cancels asynchronous work when navigation is disposed.
 */
internal class NavigationMutationRunner(
    private val scope: CoroutineScope,
    feedback: NavigationMutationFeedback,
    private val labelFor: (Int) -> String,
    private val incompleteFormat: String,
    private val failedFormat: String,
    private val onInvalidated: () -> Unit,
    private val categoryAvailableCents: (Set<String>) -> Map<String, Long>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val onRejected: (String) -> Unit = { DiagnosticsLog.actionRejected(it) },
    private val onFailed: (String, Throwable) -> Unit = { label, error -> DiagnosticsLog.actionFailed(label, error) },
) {
    private var errorMessage by feedback.error
    private var transactionImpactCues by feedback.impactCues

    fun mutate(@StringRes labelRes: Int, onChanged: () -> Unit = {}, action: () -> Boolean): Job {
        val label = labelFor(labelRes)
        return scope.launch {
            val result = withContext(ioDispatcher) { runCatching(action) }
            result.fold(
                onSuccess = { changed ->
                    if (changed) {
                        onInvalidated()
                        onChanged()
                    } else {
                        onRejected(label)
                        errorMessage = incompleteFormat.format(label)
                    }
                },
                onFailure = { error ->
                    onFailed(label, error)
                    errorMessage = error.message?.takeIf(String::isNotBlank)
                        ?: failedFormat.format(label)
                },
            )
        }
    }

    fun mutateSync(@StringRes labelRes: Int, action: () -> Boolean): Boolean {
        val label = labelFor(labelRes)
        return runCatching(action).fold(
            onSuccess = { changed ->
                if (changed) onInvalidated() else {
                    onRejected(label)
                    errorMessage = incompleteFormat.format(label)
                }
                changed
            },
            onFailure = { error ->
                onFailed(label, error)
                errorMessage = error.message?.takeIf(String::isNotBlank)
                    ?: failedFormat.format(label)
                false
            },
        )
    }

    fun mutateWithImpactCue(@StringRes labelRes: Int, categories: Set<String>, onChanged: () -> Unit = {}, action: () -> Boolean): Job {
        val label = labelFor(labelRes)
        return scope.launch {
            val before = withContext(ioDispatcher) { categoryAvailableCents(categories) }
            val result = withContext(ioDispatcher) { runCatching(action) }
            result.fold(
                onSuccess = { changed ->
                    if (changed) {
                        onInvalidated()
                        val after = withContext(ioDispatcher) { categoryAvailableCents(categories) }
                        transactionImpactCues = impactCues(before, after)
                        onChanged()
                    } else {
                        onRejected(label)
                        errorMessage = incompleteFormat.format(label)
                    }
                },
                onFailure = { error ->
                    onFailed(label, error)
                    errorMessage = error.message?.takeIf(String::isNotBlank)
                        ?: failedFormat.format(label)
                },
            )
        }
    }

    fun mutateAsync(@StringRes labelRes: Int, action: () -> Boolean, onChanged: () -> Unit): Job {
        val label = labelFor(labelRes)
        return scope.launch {
            val result = withContext(ioDispatcher) { runCatching(action) }
            result.fold(
                onSuccess = { changed ->
                    if (changed) {
                        onInvalidated()
                        onChanged()
                    } else {
                        onRejected(label)
                        errorMessage = incompleteFormat.format(label)
                    }
                },
                onFailure = { error ->
                    onFailed(label, error)
                    errorMessage = error.message?.takeIf(String::isNotBlank)
                        ?: failedFormat.format(label)
                },
            )
        }
    }

    fun mutateAsyncWithImpactCue(@StringRes labelRes: Int, categories: Set<String>, action: () -> Boolean, onChanged: () -> Unit): Job {
        val label = labelFor(labelRes)
        return scope.launch {
            val before = withContext(ioDispatcher) { categoryAvailableCents(categories) }
            val result = withContext(ioDispatcher) { runCatching(action) }
            result.fold(
                onSuccess = { changed ->
                    if (changed) {
                        onInvalidated()
                        val after = withContext(ioDispatcher) { categoryAvailableCents(categories) }
                        transactionImpactCues = impactCues(before, after)
                        onChanged()
                    } else {
                        onRejected(label)
                        errorMessage = incompleteFormat.format(label)
                    }
                },
                onFailure = { error ->
                    onFailed(label, error)
                    errorMessage = error.message?.takeIf(String::isNotBlank)
                        ?: failedFormat.format(label)
                },
            )
        }
    }

    fun impactCues(before: Map<String, Long>, after: Map<String, Long>): List<TransactionImpactCue> =
        (before.keys + after.keys).distinct().mapNotNull { name ->
            val balanceBefore = before[name] ?: return@mapNotNull null
            val balanceAfter = after[name] ?: return@mapNotNull null
            if (balanceBefore == balanceAfter) null
            else TransactionImpactCue(name, balanceBeforeCents = balanceBefore, balanceAfterCents = balanceAfter)
        }

}
