package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.data.budget.model.ActualTag

internal data class ActiveTagToken(val name: String, val start: Int, val endExclusive: Int)

internal fun activeTagToken(text: String, cursor: Int): ActiveTagToken? {
    if (cursor !in 0..text.length) return null
    var start = cursor
    while (start > 0 && !text[start - 1].isWhitespace() && text[start - 1] != '#') start--
    if (start == 0 || text[start - 1] != '#') return null
    val hash = start - 1
    if (hash > 0 && text[hash - 1] == '#') return null
    var end = cursor
    while (end < text.length && !text[end].isWhitespace() && text[end] != '#') end++
    val name = text.substring(start, cursor)
    if (name.any { it == '#' || it.isWhitespace() }) return null
    return ActiveTagToken(name, hash, end)
}

internal fun matchingTags(tags: List<ActualTag>, query: String): List<ActualTag> = tags.asSequence()
    .filterNot { it.hidden }
    .filter { query.isBlank() || it.tag.contains(query, ignoreCase = true) }
    .sortedWith(compareBy<ActualTag>({ !it.tag.equals(query, true) }, { !it.tag.startsWith(query, true) }, { it.tag.lowercase() }).thenBy { it.tag })
    .toList()

internal fun canCreateTag(query: String, tags: List<ActualTag>): Boolean =
    query.isNotBlank() && query.none { it == '#' || it.isWhitespace() } &&
        tags.none { it.tag.equals(query, ignoreCase = false) }

internal fun replaceActiveTag(text: String, token: ActiveTagToken, tag: String): Pair<String, Int> {
    val replacement = "#$tag"
    val updated = text.replaceRange(token.start, token.endExclusive, replacement)
    return updated to token.start + replacement.length
}
