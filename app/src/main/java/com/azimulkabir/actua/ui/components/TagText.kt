package com.azimulkabir.actua.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import com.azimulkabir.actua.data.budget.TagMetadataStore
import com.azimulkabir.actua.data.sync.SyncSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class TagOccurrence(
    val name: String,
    val start: Int,
    val endExclusive: Int,
)

/**
 * Finds Actual-style tags without changing the note text.
 *
 * A single # starts a tag and whitespace or another # ends it. A doubled ## is
 * treated as escaped text, matching Actual's note-tag convention.
 */
internal fun findTagOccurrences(notes: String): List<TagOccurrence> {
    if (notes.isEmpty()) return emptyList()
    val result = mutableListOf<TagOccurrence>()
    var index = 0
    while (index < notes.length) {
        if (notes[index] != '#') {
            index++
            continue
        }
        if (index + 1 < notes.length && notes[index + 1] == '#') {
            index += 2
            continue
        }
        val start = index
        var end = index + 1
        while (end < notes.length && !notes[end].isWhitespace() && notes[end] != '#') end++
        if (end > start + 1) {
            result += TagOccurrence(notes.substring(start + 1, end), start, end)
        }
        index = if (end > index) end else index + 1
    }
    return result
}

internal fun parseTagColor(raw: String?): Color? {
    val value = raw?.trim() ?: return null
    if (!value.startsWith('#')) return null
    val hex = value.drop(1)
    val argb = when (hex.length) {
        6 -> hex.toLongOrNull(16)?.let { 0xFF000000L or it }
        8 -> hex.toLongOrNull(16)
        else -> null
    } ?: return null
    return Color(argb)
}

internal fun tagForeground(background: Color): Color =
    if (background.luminance() > 0.45f) Color.Black else Color.White

@Composable
fun rememberActualTagColors(refreshKey: Any? = Unit): Map<String, String> {
    val context = LocalContext.current
    val syncDataGeneration by SyncSignals.dataGeneration.collectAsState()
    val colors by produceState<Map<String, String>>(
        initialValue = emptyMap(),
        key1 = context,
        key2 = refreshKey to syncDataGeneration,
    ) {
        value = withContext(Dispatchers.IO) {
            TagMetadataStore(context).activeTagColors(syncDataGeneration)
        }
    }
    return colors
}

fun coloredTagText(
    notes: String,
    tagColors: Map<String, String>,
): AnnotatedString {
    if (notes.isEmpty() || tagColors.isEmpty()) return AnnotatedString(notes)
    return buildAnnotatedString {
        append(notes)
        findTagOccurrences(notes).forEach { occurrence ->
            val background = parseTagColor(tagColors[occurrence.name]) ?: return@forEach
            addStyle(
                SpanStyle(
                    color = tagForeground(background),
                    background = background,
                    fontWeight = FontWeight.Medium,
                ),
                occurrence.start,
                occurrence.endExclusive,
            )
        }
    }
}
