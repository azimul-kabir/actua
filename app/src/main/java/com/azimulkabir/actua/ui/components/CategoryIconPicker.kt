package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.preferences.CategoryIcon
import com.azimulkabir.actua.data.preferences.isCategoryEmoji
import com.azimulkabir.actua.ui.theme.Spacing

internal class CategoryIconEditor(
    val emojis: Map<String, String>,
    val set: (categoryId: String, key: String?) -> Unit,
    val showIcons: Boolean = false,
)

internal val LocalCategoryIconEditor = compositionLocalOf<CategoryIconEditor?> { null }

@Composable
internal fun EditCategoryIconSheet(categoryId: String, onDismiss: () -> Unit) {
    val editor = LocalCategoryIconEditor.current ?: return
    CategoryIconPicker(
        currentEmoji = editor.emojis[categoryId],
        onDismiss = onDismiss,
        onSelect = { key ->
            editor.set(categoryId, key)
            onDismiss()
        },
    )
}

private val curatedEmoji = listOf(
    "🍕", "🍎", "☕", "🍔", "🛒", "🏠", "🚗", "🚌", "🚲", "✈️",
    "⛽", "💡", "💧", "📱", "💻", "🎮", "🎬", "🎵", "📚", "🎓",
    "👕", "👟", "💊", "🏥", "🐶", "🐱", "🎁", "🎉", "💰", "💳",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryIconPicker(currentEmoji: String?, onDismiss: () -> Unit, onSelect: (String?) -> Unit) {
    var none by rememberSaveable { mutableStateOf(false) }
    var input by rememberSaveable(currentEmoji) { mutableStateOf(currentEmoji.orEmpty()) }
    val valid = remember(input) { isCategoryEmoji(input) }
    val invalid = input.isNotEmpty() && !valid
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Spacing.lg)) {
            Text(stringResource(R.string.category_icon), style = MaterialTheme.typography.titleLarge)
            PrimaryTabRow(selectedTabIndex = if (none) 1 else 0) {
                Tab(selected = !none, onClick = { none = false }, text = { Text(stringResource(R.string.category_icon_emoji)) })
                Tab(selected = none, onClick = { none = true }, text = { Text(stringResource(R.string.category_icon_none)) })
            }
            if (none) {
                TextButton(onClick = { onSelect(null) }) { Text(stringResource(R.string.category_icon_remove)) }
            } else {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.md),
                    singleLine = true,
                    isError = invalid,
                    label = { Text(stringResource(R.string.category_icon_enter_emoji)) },
                    supportingText = if (invalid) {
                        { Text(stringResource(R.string.category_icon_invalid_emoji)) }
                    } else null,
                )
                TextButton(enabled = valid, onClick = { onSelect(CategoryIcon("emoji", input).toString()) }) {
                    Text(stringResource(R.string.category_icon_use_emoji))
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = (maxWidth / 52.dp).toInt().coerceIn(1, 6)
                    Column {
                        curatedEmoji.chunked(columns).forEach { row ->
                            Row(Modifier.fillMaxWidth()) {
                                row.forEach { emoji ->
                                    TextButton(
                                        onClick = { onSelect(CategoryIcon("emoji", emoji).toString()) },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                    ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
