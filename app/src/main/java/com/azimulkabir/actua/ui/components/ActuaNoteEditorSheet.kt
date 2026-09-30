package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Notes
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.ui.theme.Spacing

/**
 * Bottom-sheet editor for an account or category note: a titled card holding a multi-line field
 * and a full-width Save button, matching the Add transaction design. The sheet's content insets
 * keep the field and button above the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActuaNoteEditorSheet(
    initialNote: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf(initialNote) }
    val focusRequester = remember { FocusRequester() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth()) {
            ActuaSheetTitle(if (initialNote.isBlank()) "Add note" else "Edit note")
            ActuaSheetCard {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Notes, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(Spacing.lg))
                    BasicTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        minLines = 4,
                        maxLines = 10,
                        modifier = Modifier.weight(1f).heightIn(min = 96.dp).focusRequester(focusRequester)
                            .semantics { contentDescription = placeholder },
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) {
                                    Text(placeholder, style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                inner()
                            }
                        },
                    )
                }
            }
            ActuaPrimaryActionBar(
                text = "Save",
                icon = Icons.Outlined.Check,
                onClick = { onSave(draft) },
                modifier = Modifier.padding(bottom = Spacing.sm),
            )
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
