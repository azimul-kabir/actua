package com.azimulkabir.actua.ui.accounts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.ui.components.EmojiIconPicker

internal val LocalAccountEmojis = compositionLocalOf<Map<String, String>> { emptyMap() }

internal class AccountIconEditor(
    val budgetId: String,
    val emojis: Map<String, String>,
    val set: (accountId: String, key: String?) -> Unit,
)

internal val LocalAccountIconEditor = compositionLocalOf<AccountIconEditor?> { null }

internal sealed interface AccountIcon {
    data class Emoji(val value: String) : AccountIcon
    data class Type(val type: String, val isCreditCard: Boolean) : AccountIcon
}

internal fun resolveAccountIcon(
    showIcons: Boolean,
    emoji: String?,
    type: String,
    isCreditCard: Boolean,
): AccountIcon? = when {
    !showIcons -> null
    emoji != null -> AccountIcon.Emoji(emoji)
    else -> AccountIcon.Type(type, isCreditCard)
}

@Composable
internal fun accountIconSize(nameStyle: TextStyle, defaultSize: Dp = 24.dp): Dp {
    val bodySize = MaterialTheme.typography.bodyLarge.fontSize
    return with(LocalDensity.current) {
        // Preserve the existing slot at the default font scale and follow the name's scaled size.
        (nameStyle.fontSize * (defaultSize.value / bodySize.value)).toDp()
    }
}

@Composable
internal fun AccountIconGlyph(
    icon: AccountIcon,
    nameStyle: TextStyle,
    size: Dp,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    when (icon) {
        is AccountIcon.Type -> Icon(
            accountTypeIcon(icon.type, icon.isCreditCard), contentDescription = null,
            modifier = modifier.size(size), tint = tint,
        )
        is AccountIcon.Emoji -> Box(
            modifier.size(size).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            Text(
                icon.value,
                style = nameStyle.copy(lineHeight = TextUnit.Unspecified, letterSpacing = 0.sp),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
internal fun EditAccountIconSheet(accountId: String, onDismiss: () -> Unit) {
    val editor = LocalAccountIconEditor.current ?: return
    EmojiIconPicker(
        currentEmoji = editor.emojis[accountId],
        onDismiss = onDismiss,
        onSelect = { key -> editor.set(accountId, key); onDismiss() },
        title = R.string.account_icon,
        resetTab = R.string.settings_app_icon_default,
        resetAction = R.string.reset_to_default,
    )
}
