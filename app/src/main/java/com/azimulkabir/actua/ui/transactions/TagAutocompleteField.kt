package com.azimulkabir.actua.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.azimulkabir.actua.data.budget.model.ActualTag
import com.azimulkabir.actua.ui.components.findTagOccurrences
import com.azimulkabir.actua.ui.components.parseTagColor
import com.azimulkabir.actua.ui.components.tagChipBackground
import com.azimulkabir.actua.ui.components.tagChipForeground

/** Corner radius of the in-line tag pill, matching [com.azimulkabir.actua.ui.components.TagNoteText]'s chip rounding. */
private val TagChipCornerRadius = 6.dp
private val TagChipHorizontalPadding = 4.dp
private val TagChipVerticalPadding = 1.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagAutocompleteField(
    value: String,
    tags: List<ActualTag>,
    onValueChange: (String) -> Unit,
    onCreateTag: (String) -> ActualTag?,
    label: String = "Notes",
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    var dismissedToken by remember { mutableStateOf<String?>(null) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(value) {
        if (value != fieldValue.text) fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
    }
    val token = activeTagToken(fieldValue.text, fieldValue.selection.end)
    val tokenKey = token?.let { "${it.start}:${it.endExclusive}:${it.name}" }
    if (tokenKey != dismissedToken && dismissedToken != null) dismissedToken = null
    val matches = token?.let { matchingTags(tags, it.name).take(6) }.orEmpty()
    val showCreate = token?.let { canCreateTag(it.name, tags) } == true
    val expanded = focused && token != null && tokenKey != dismissedToken && (matches.isNotEmpty() || showCreate)
    val tagColors = remember(tags) { tags.mapNotNull { tag -> tag.color?.let { tag.tag to it } }.toMap() }
    val darkTheme = isSystemInDarkTheme()
    val tagHighlight = remember(tagColors, darkTheme) { tagHighlightTransformation(tagColors, darkTheme) }

    Column(modifier) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { next -> fieldValue = next; onValueChange(next.text) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            visualTransformation = tagHighlight,
            interactionSource = interactionSource,
            onTextLayout = { textLayout = it },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .drawBehind {
                    val layout = textLayout ?: return@drawBehind
                    tagChipRects(
                        text = fieldValue.text,
                        tagColors = tagColors,
                        darkTheme = darkTheme,
                        layout = layout,
                        horizontalPaddingPx = TagChipHorizontalPadding.toPx(),
                        verticalPaddingPx = TagChipVerticalPadding.toPx(),
                    ).forEach { (rect, color) ->
                        drawRoundRect(
                            color = color,
                            topLeft = rect.topLeft,
                            size = rect.size,
                            cornerRadius = CornerRadius(TagChipCornerRadius.toPx()),
                        )
                    }
                },
            decorationBox = { innerTextField ->
                OutlinedTextFieldDefaults.DecorationBox(
                    value = fieldValue.text,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    isError = false,
                    visualTransformation = tagHighlight,
                    interactionSource = interactionSource,
                    label = { Text(label) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val cursor = fieldValue.selection.end
                            val text = fieldValue.text.replaceRange(cursor, cursor, "#")
                            fieldValue = TextFieldValue(text, TextRange(cursor + 1))
                            onValueChange(text)
                            focusRequester.requestFocus()
                        }) { Text("#", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    },
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { dismissedToken = tokenKey },
            properties = PopupProperties(focusable = false),
            modifier = Modifier.fillMaxWidth(0.92f),
        ) {
            matches.forEach { tag ->
                DropdownMenuItem(
                    leadingIcon = { Box(Modifier.size(14.dp).background(parseTagSuggestionColor(tag.color), CircleShape)) },
                    text = { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "#${tag.tag}",
                            modifier = Modifier.weight(1f).testTag("tagSuggestion-${tag.id}"),
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                        tag.description?.takeIf(String::isNotBlank)?.let {
                            Text(
                                it,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } },
                    onClick = {
                        val current = activeTagToken(fieldValue.text, fieldValue.selection.end) ?: return@DropdownMenuItem
                        val (text, cursor) = replaceActiveTag(fieldValue.text, current, tag.tag)
                        fieldValue = TextFieldValue(text, TextRange(cursor)); onValueChange(text)
                    },
                )
            }
            if (showCreate && token != null) DropdownMenuItem(text = { Text("Create #${token.name}") }, onClick = {
                val current = activeTagToken(fieldValue.text, fieldValue.selection.end) ?: return@DropdownMenuItem
                val created = onCreateTag(current.name) ?: return@DropdownMenuItem
                val (text, cursor) = replaceActiveTag(fieldValue.text, current, created.tag)
                fieldValue = TextFieldValue(text, TextRange(cursor)); onValueChange(text)
            })
        }
    }
}

/** Highlights recognized `#tag` tokens in-place while typing, matching [com.azimulkabir.actua.ui.components.TagNoteText]'s post-save styling. */
internal fun tagHighlightTransformation(
    tagColors: Map<String, String>,
    darkTheme: Boolean,
): VisualTransformation = VisualTransformation { text ->
    val occurrences = findTagOccurrences(text.text)
    if (occurrences.isEmpty()) return@VisualTransformation TransformedText(text, OffsetMapping.Identity)
    val annotated = buildAnnotatedString {
        append(text.text)
        occurrences.forEach { occurrence ->
            val color = parseTagColor(tagColors[occurrence.name]) ?: return@forEach
            addStyle(
                SpanStyle(
                    color = tagChipForeground(color, darkTheme),
                    fontWeight = FontWeight.SemiBold,
                ),
                occurrence.start,
                occurrence.endExclusive,
            )
        }
    }
    TransformedText(annotated, OffsetMapping.Identity)
}

/**
 * Computes a rounded pill rect (in the text field's own draw coordinates) behind each
 * recognized `#tag` occurrence, so the in-line highlight matches [com.azimulkabir.actua.ui.components.TagNoteText]'s
 * rounded chip instead of a flat rectangular [SpanStyle] background.
 */
internal fun tagChipRects(
    text: String,
    tagColors: Map<String, String>,
    darkTheme: Boolean,
    layout: TextLayoutResult,
    horizontalPaddingPx: Float,
    verticalPaddingPx: Float,
): List<Pair<Rect, Color>> {
    val occurrences = findTagOccurrences(text)
    if (occurrences.isEmpty()) return emptyList()
    return occurrences.mapNotNull { occurrence ->
        val color = parseTagColor(tagColors[occurrence.name]) ?: return@mapNotNull null
        val startBox = layout.getBoundingBox(occurrence.start)
        val endBox = layout.getBoundingBox(occurrence.endExclusive - 1)
        val rect = Rect(
            left = startBox.left - horizontalPaddingPx,
            top = minOf(startBox.top, endBox.top) - verticalPaddingPx,
            right = endBox.right + horizontalPaddingPx,
            bottom = maxOf(startBox.bottom, endBox.bottom) + verticalPaddingPx,
        )
        rect to tagChipBackground(color, darkTheme)
    }
}

private fun parseTagSuggestionColor(value: String?): Color = runCatching {
    Color(android.graphics.Color.parseColor(value ?: "#808080"))
}.getOrDefault(Color.Gray)
