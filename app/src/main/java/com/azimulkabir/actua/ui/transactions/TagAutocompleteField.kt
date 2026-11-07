package com.azimulkabir.actua.ui.transactions

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.ui.theme.Sizes
import com.azimulkabir.actua.data.budget.model.ActualTag
import com.azimulkabir.actua.ui.components.findTagOccurrences
import com.azimulkabir.actua.ui.components.parseTagColor
import com.azimulkabir.actua.ui.components.tagChipForeground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TagAutocompleteField(
    value: String,
    tags: List<ActualTag>,
    onValueChange: (String) -> Unit,
    onCreateTag: (String) -> ActualTag?,
    label: String = stringResource(R.string.transaction_notes),
    modifier: Modifier = Modifier,
    /** Renders the field as an icon row inside Add transaction's form cards instead of an outlined field. */
    rowIcon: ImageVector? = null,
    placeholder: String = "",
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val bringIntoView = remember { BringIntoViewRequester() }
    LaunchedEffect(value) {
        if (value != fieldValue.text) fieldValue = fieldValue.copy(text = value, selection = TextRange(value.length))
    }
    val token = activeTagToken(fieldValue.text, fieldValue.selection.end)
    val matches = token?.let { matchingTags(tags, it.name) }.orEmpty()
    val showCreate = token?.let { canCreateTag(it.name, tags) } == true
    val expanded = focused && token != null && (matches.isNotEmpty() || showCreate)
    val tagColors = remember(tags) { tags.mapNotNull { tag -> tag.color?.let { tag.tag to it } }.toMap() }
    val darkTheme = isSystemInDarkTheme()
    val defaultTagColor = MaterialTheme.colorScheme.primary
    val tagHighlight = remember(tagColors, darkTheme, defaultTagColor) {
        tagHighlightTransformation(tagColors, darkTheme, defaultTagColor)
    }

    // The suggestions sit under the field, which the keyboard can cover, so scroll the whole block into view.
    LaunchedEffect(expanded, matches.size, showCreate) {
        if (expanded) {
            withFrameNanos { } // let the row be laid out first
            bringIntoView.bringIntoView()
        }
    }
    Column(modifier.bringIntoViewRequester(bringIntoView)) {
        BasicTextField(
            value = fieldValue,
            onValueChange = { next -> fieldValue = next; onValueChange(next.text) },
            singleLine = true,
            textStyle = (if (rowIcon != null) MaterialTheme.typography.bodyLarge else LocalTextStyle.current)
                .copy(color = MaterialTheme.colorScheme.onSurface),
            visualTransformation = tagHighlight,
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { innerTextField ->
                val insertTag: @Composable () -> Unit = {
                    IconButton(onClick = {
                        val cursor = fieldValue.selection.end
                        val text = fieldValue.text.replaceRange(cursor, cursor, "#")
                        fieldValue = TextFieldValue(text, TextRange(cursor + 1))
                        onValueChange(text)
                        focusRequester.requestFocus()
                    }) { Text("#", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (rowIcon != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth().heightIn(min = Sizes.formRowMinHeight)
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(rowIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Box {
                                if (fieldValue.text.isEmpty()) {
                                    Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                innerTextField()
                            }
                        }
                        insertTag()
                    }
                } else OutlinedTextFieldDefaults.DecorationBox(
                    value = fieldValue.text,
                    innerTextField = innerTextField,
                    enabled = true,
                    singleLine = true,
                    isError = false,
                    visualTransformation = tagHighlight,
                    interactionSource = interactionSource,
                    label = { Text(label) },
                    trailingIcon = insertTag,
                )
            },
        )
        // An inline row rather than a popup menu: it scrolls sideways through every matching tag, takes touches like
        // any other part of the form, and is only present while a #tag is being typed.
        if (expanded) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(matches, key = { it.id }) { tag ->
                    SuggestionChip(
                        onClick = {
                            val current = activeTagToken(fieldValue.text, fieldValue.selection.end) ?: return@SuggestionChip
                            val (text, cursor) = replaceActiveTag(fieldValue.text, current, tag.tag)
                            fieldValue = TextFieldValue(text, TextRange(cursor)); onValueChange(text)
                        },
                        icon = { Box(Modifier.size(14.dp).background(parseTagSuggestionColor(tag.color), CircleShape)) },
                        label = {
                            Text(
                                "#${tag.tag}",
                                modifier = Modifier.testTag("tagSuggestion-${tag.id}"),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
                if (showCreate && token != null) item(key = "create") {
                    SuggestionChip(
                        onClick = {
                            val current = activeTagToken(fieldValue.text, fieldValue.selection.end) ?: return@SuggestionChip
                            val created = onCreateTag(current.name) ?: return@SuggestionChip
                            val (text, cursor) = replaceActiveTag(fieldValue.text, current, created.tag)
                            fieldValue = TextFieldValue(text, TextRange(cursor)); onValueChange(text)
                        },
                        label = { Text(stringResource(R.string.transaction_create_tag, token.name)) },
                    )
                }
            }
        }
    }
}

/** Highlights recognized `#tag` tokens in-place while typing, matching [com.azimulkabir.actua.ui.components.TagNoteText]'s post-save styling. */
internal fun tagHighlightTransformation(
    tagColors: Map<String, String>,
    darkTheme: Boolean,
    /** For tags with no stored color, as [com.azimulkabir.actua.ui.components.TagNoteText] draws them. */
    defaultTagColor: Color,
): VisualTransformation = VisualTransformation { text ->
    val occurrences = findTagOccurrences(text.text)
    if (occurrences.isEmpty()) return@VisualTransformation TransformedText(text, OffsetMapping.Identity)
    val annotated = buildAnnotatedString {
        append(text.text)
        occurrences.forEach { occurrence ->
            val color = parseTagColor(tagColors[occurrence.name]) ?: defaultTagColor
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

private fun parseTagSuggestionColor(value: String?): Color = runCatching {
    Color(android.graphics.Color.parseColor(value ?: "#808080"))
}.getOrDefault(Color.Gray)
