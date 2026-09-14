package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.data.budget.TagMetadataStore
import com.azimulkabir.actua.data.sync.SyncSignals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class TagOccurrence(
    val name: String,
    val start: Int,
    val endExclusive: Int,
)

internal sealed interface TagNoteSegment {
    val text: String

    data class Plain(override val text: String) : TagNoteSegment

    data class Tag(
        val name: String,
        val color: Color,
    ) : TagNoteSegment {
        override val text: String = "#$name"
    }
}

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

internal fun tagSegments(
    notes: String,
    tagColors: Map<String, String>,
): List<TagNoteSegment> {
    if (notes.isEmpty()) return emptyList()
    val recognized = findTagOccurrences(notes).mapNotNull { occurrence ->
        parseTagColor(tagColors[occurrence.name])?.let { occurrence to it }
    }
    if (recognized.isEmpty()) return listOf(TagNoteSegment.Plain(notes))

    return buildList {
        var cursor = 0
        recognized.forEach { (occurrence, color) ->
            if (occurrence.start > cursor) {
                add(TagNoteSegment.Plain(notes.substring(cursor, occurrence.start)))
            }
            add(TagNoteSegment.Tag(occurrence.name, color))
            cursor = occurrence.endExclusive
        }
        if (cursor < notes.length) add(TagNoteSegment.Plain(notes.substring(cursor)))
    }
}

internal fun tagChipForeground(color: Color, darkTheme: Boolean): Color = when {
    darkTheme && color.luminance() < 0.55f -> lerp(color, Color.White, 0.46f)
    !darkTheme && color.luminance() > 0.55f -> lerp(color, Color.Black, 0.38f)
    else -> color
}

internal fun tagChipBackground(color: Color, darkTheme: Boolean): Color =
    color.copy(alpha = if (darkTheme) 0.24f else 0.14f)

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagNoteText(
    notes: String,
    tagColors: Map<String, String>,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
) {
    val segments = remember(notes, tagColors) { tagSegments(notes, tagColors) }
    val darkTheme = isSystemInDarkTheme()

    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        segments.forEachIndexed { index, segment ->
            when (segment) {
                is TagNoteSegment.Plain -> Text(segment.text, style = style)
                is TagNoteSegment.Tag -> Surface(
                    color = tagChipBackground(segment.color, darkTheme),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.padding(
                        start = if (index > 0 && segments[index - 1] is TagNoteSegment.Tag) 2.dp else 0.dp,
                    ),
                ) {
                    Text(
                        text = segment.text,
                        style = style,
                        color = tagChipForeground(segment.color, darkTheme),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}
