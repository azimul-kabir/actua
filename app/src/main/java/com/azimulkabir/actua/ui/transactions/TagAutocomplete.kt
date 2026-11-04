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

/**
 * Replaces the active `#token` with the chosen tag and leaves the cursor after one trailing space, so the
 * cursor is no longer inside the tag and the suggestion popup closes. A space is only added when the text
 * after the token does not already start with whitespace; an existing one is stepped over instead.
 */
internal fun replaceActiveTag(text: String, token: ActiveTagToken, tag: String): Pair<String, Int> {
    val replacement = "#$tag"
    val afterTag = token.start + replacement.length
    val updated = text.replaceRange(token.start, token.endExclusive, replacement)
    return if (updated.getOrNull(afterTag)?.isWhitespace() == true) {
        updated to afterTag + 1
    } else {
        updated.replaceRange(afterTag, afterTag, " ") to afterTag + 1
    }
}
