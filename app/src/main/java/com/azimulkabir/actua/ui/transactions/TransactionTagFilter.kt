package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.data.budget.TagSyntax
import com.azimulkabir.actua.model.Transaction

/**
 * Returns true when [notes] contains an exact hashtag token for [tag], case-sensitively like
 * Actual's `hasTags` transaction filter. A `#` that follows another `#` never opens a tag.
 */
internal fun notesContainTag(notes: String?, tag: String): Boolean {
    val wanted = tag.trim().removePrefix("#")
    if (wanted.isBlank()) return false

    var index = 0
    val text = notes.orEmpty()
    while (index < text.length) {
        if (text[index] != '#') { index += 1; continue }
        val run = TagSyntax.hashRun(text, index)
        if (run > 1) { index += run; continue }

        val start = index + 1
        val end = TagSyntax.nameEnd(text, start)
        if (end > start && text.substring(start, end) == wanted) return true
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
