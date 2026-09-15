package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.model.Transaction

/**
 * Returns true when [notes] contains an exact hashtag token for [tag].
 *
 * Actual treats `##` as an escaped literal hash. Matching is intentionally
 * case-insensitive for discovery/filtering, while rename semantics remain
 * case-sensitive in the canonical tag writer.
 */
internal fun notesContainTag(notes: String?, tag: String): Boolean {
    val wanted = tag.trim().removePrefix("#")
    if (wanted.isBlank()) return false

    var index = 0
    val text = notes.orEmpty()
    while (index < text.length) {
        if (text[index] != '#') {
            index += 1
            continue
        }
        if (index + 1 < text.length && text[index + 1] == '#') {
            index += 2
            continue
        }

        val start = index + 1
        var end = start
        while (end < text.length && text[end] != '#' && !text[end].isWhitespace()) end += 1
        if (end > start && text.substring(start, end).equals(wanted, ignoreCase = true)) return true
        index = if (end > index) end else index + 1
    }
    return false
}

/** Applies a managed-tag filter without interfering with the existing account/category/search filters. */
internal fun List<Transaction>.filterByTag(tag: String?): List<Transaction> =
    tag?.takeIf { it.isNotBlank() }?.let { selected ->
        filter { transaction ->
            notesContainTag(transaction.notes, selected) ||
                transaction.splits.any { split -> notesContainTag(split.notes, selected) }
        }
    } ?: this
