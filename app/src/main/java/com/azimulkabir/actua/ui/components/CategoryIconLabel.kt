package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.isSpecified

internal val LocalCategoryEmojis = compositionLocalOf<Map<String, String>> { emptyMap() }

@Composable
internal fun CategoryIconLabel(
    text: String,
    emoji: String?,
    reserveSlot: Boolean,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    if (!reserveSlot) {
        Text(text, modifier, color = color, style = style, maxLines = maxLines, overflow = overflow)
        return
    }
    val nameSize = style.fontSize.takeIf { it.isSpecified } ?: MaterialTheme.typography.bodyLarge.fontSize
    val fontSize = nameSize * 1.15f
    val (width, gap) = with(LocalDensity.current) { fontSize.toDp() to (nameSize * 0.3f).toDp() }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(width).clearAndSetSemantics {}, contentAlignment = Alignment.Center) {
            if (emoji != null) Text(emoji, fontSize = fontSize, style = style, maxLines = 1, softWrap = false)
        }
        Spacer(Modifier.width(gap))
        Text(text, color = color, style = style, maxLines = maxLines, overflow = overflow)
    }
}
