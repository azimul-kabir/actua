package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.model.Transaction

/**
 * Returns true when [notes] contains an exact hashtag token for [tag].
 * `##` is an escaped literal hash and is never treated as a tag opener.
 * Discovery is case-insensitive; canonical rename remains case-sensitive.
 */
internal fun notesContainTag(notes: String?, tag: String): Boolean {
    val wanted = tag.trim().removePrefix("#")
    if (wanted.isBlank()) return false

    var index = 0
    val text = notes.orEmpty()
    while (index < text.length) {
        if (text[index] != '#') { index += 1; continue }
        if (index + 1 < text.length && text[index + 1] == '#') { index += 2; continue }

        val start = index + 1
        var end = start
        while (end < text.length && text[end] != '#' && !text[end].isWhitespace()) end += 1
        if (end > start && text.substring(start, end).equals(wanted, ignoreCase = true)) return true
        index = if (end > index) end else index + 1
    }
    return false
}

/**
 * Applies a tag filter to committed transaction data. Parent and split notes
 * participate equally, so split-aware discovery stays consistent with search.
 */
internal fun List<Transaction>.filterByTag(tag: String?): List<Transaction> =
    tag?.takeIf { it.isNotBlank() }?.let { selected ->
        filter { transaction ->
            notesContainTag(transaction.notes, selected) ||
                transaction.splits.any { split -> notesContainTag(split.notes, selected) }
        }
    } ?: this
