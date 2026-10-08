package com.azimulkabir.actua.data.budget

/**
 * Tag syntax shared with Actual: a tag is a `#` that doesn't follow another `#`, then every character
 * up to JS whitespace (`\s`) or the next `#` (`(?<!#)#([^#\s]+)` in loot-core).
 */
internal object TagSyntax {
    /** JS `\s` inside a regex character class: ECMAScript WhiteSpace and LineTerminator. */
    const val JS_WHITESPACE = "\\t\\n\\u000B\\f\\r \\u00A0\\u1680\\u2000-\\u200A\\u2028\\u2029\\u202F\\u205F\\u3000\\uFEFF"

    /** True for the characters JS `\s` matches; Kotlin's isWhitespace differs at the edges. */
    fun isWhitespace(char: Char): Boolean = when (char) {
        '\t', '\n', '\u000B', '\u000C', '\r', ' ', ' ', ' ', ' ', ' ', ' ', ' ', '　', '﻿' -> true
        else -> char in ' '..' '
    }

    /** End of the tag name starting at [start]: the next `#`, JS whitespace or the end of [text]. */
    fun nameEnd(text: String, start: Int): Int {
        var end = start
        while (end < text.length && text[end] != '#' && !isWhitespace(text[end])) end++
        return end
    }

    /** Length of the run of `#` at [index]. Only a run of exactly one can start a tag. */
    fun hashRun(text: String, index: Int): Int {
        var end = index
        while (end < text.length && text[end] == '#') end++
        return end - index
    }
}
