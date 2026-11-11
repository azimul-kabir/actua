package com.azimulkabir.actua.widget

/** What a single-account or single-category widget has to show. */
internal sealed interface SingleChoice<out T> {
    /** The chosen item, or the default one when nothing was chosen. */
    data class Found<T>(val item: T) : SingleChoice<T>

    /** An item was chosen, but it is no longer in the budget (deleted, closed or hidden). */
    data object NotFound : SingleChoice<Nothing>

    /** Nothing was chosen and there is nothing to default to. */
    data object Empty : SingleChoice<Nothing>
}

/**
 * Finds the item a single-choice widget shows among [items]. With no [selectedId], which is the case when
 * setup was skipped, it is the first item, so the widget still shows something sensible. With a
 * [selectedId] that no longer exists it is [SingleChoice.NotFound], never a silent switch to another item.
 */
internal fun <T> resolveSingle(items: List<T>, selectedId: String?, id: (T) -> String): SingleChoice<T> {
    if (selectedId == null) return items.firstOrNull()?.let { SingleChoice.Found(it) } ?: SingleChoice.Empty
    val match = items.firstOrNull { id(it) == selectedId }
    return if (match != null) SingleChoice.Found(match) else SingleChoice.NotFound
}
