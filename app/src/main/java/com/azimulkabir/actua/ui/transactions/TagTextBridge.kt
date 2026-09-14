package com.azimulkabir.actua.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.azimulkabir.actua.ui.components.TagNoteText
import com.azimulkabir.actua.ui.components.TagStyledText

/**
 * Keeps existing transaction note call sites concise while routing tag-aware
 * content through the shared chip renderer. Plain Material3 Text calls are
 * unaffected because this overload only accepts [TagStyledText].
 */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun Text(
    text: TagStyledText,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    val arrangement = when (textAlign) {
        TextAlign.End, TextAlign.Right -> Arrangement.End
        TextAlign.Center -> Arrangement.Center
        else -> Arrangement.Start
    }
    TagNoteText(
        content = text,
        modifier = modifier,
        style = style,
        horizontalArrangement = arrangement,
    )
}
