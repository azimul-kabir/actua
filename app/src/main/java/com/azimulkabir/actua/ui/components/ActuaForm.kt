package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.azimulkabir.actua.ui.theme.AmountTypography
import com.azimulkabir.actua.ui.theme.Sizes
import com.azimulkabir.actua.ui.theme.Spacing

/**
 * Building blocks of the Add transaction design, shared so forms, grouped lists and sheets
 * across the app use the same cards, rows, amount hero and actions.
 */

/** A rounded, borderless group of [ActuaFormRow]s separated by [ActuaCardDivider]s. */
@Composable
fun ActuaFormCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(content = content)
    }
}

/** Divider between rows of a card, inset by [inset] so it lines up with the row text. */
@Composable
fun ActuaCardDivider(modifier: Modifier = Modifier, inset: Dp = Sizes.cardDividerInset) {
    HorizontalDivider(
        modifier = modifier.padding(start = inset),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * One field or setting as an icon row: label over value, an optional caption, and a trailing
 * value, switch ([checked]) or chevron. A null [value] shows [label] as the row's only line.
 */
@Composable
fun ActuaFormRow(
    icon: ImageVector,
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    valueIsPlaceholder: Boolean = false,
    caption: String? = null,
    supportingValue: String? = null,
    enabled: Boolean = true,
    checked: Boolean? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val interaction = when {
        onClick == null || !enabled -> Modifier
        checked != null -> Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { onClick() })
        else -> Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = Sizes.formRowMinHeight)
            .then(interaction)
            .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) muted else muted.copy(alpha = 0.6f))
        Spacer(Modifier.width(Spacing.lg))
        Column(Modifier.weight(1f)) {
            if (value == null) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (valueIsPlaceholder || !enabled) muted else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
        }
        supportingValue?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = muted,
                maxLines = 1,
                modifier = Modifier.padding(start = Spacing.sm),
            )
        }
        when {
            checked != null -> Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
                modifier = Modifier.padding(start = Spacing.sm),
            )
            trailing != null -> trailing()
            onClick != null && enabled -> Icon(Icons.Outlined.ChevronRight, contentDescription = null, tint = muted)
        }
    }
}

/**
 * A single-line text input styled as an [ActuaFormRow], for free-text fields such as a name
 * inside an [ActuaFormCard]. Tapping anywhere on the row focuses the input.
 */
@Composable
fun ActuaFormTextField(
    icon: ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth(),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.formRowMinHeight)
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = muted)
                Spacer(Modifier.width(Spacing.lg))
                Column(Modifier.weight(1f)) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = muted)
                    Box {
                        if (value.isEmpty()) {
                            Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = muted)
                        }
                        innerTextField()
                    }
                }
            }
        },
    )
}

/** Primary-colored label above a group of cards or rows, e.g. a picker or settings section. */
@Composable
fun ActuaGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 20.dp, bottom = 6.dp, start = Spacing.xs),
    )
}

/** Where a row sits in an [ActuaGroupedItem] group; decides which corners are rounded. */
enum class GroupPosition {
    First, Middle, Last, Only;

    val isFirst: Boolean get() = this == First || this == Only
    val isLast: Boolean get() = this == Last || this == Only

    companion object {
        /** Position of the item at [index] in a group of [count] items. */
        fun of(index: Int, count: Int): GroupPosition {
            require(count > 0 && index in 0 until count) { "index $index is outside a group of $count" }
            return when {
                count == 1 -> Only
                index == 0 -> First
                index == count - 1 -> Last
                else -> Middle
            }
        }
    }
}

/** [base] with only the corners at the outside of a group rounded. */
internal fun groupedItemShape(position: GroupPosition, base: CornerBasedShape): CornerBasedShape = base.copy(
    topStart = if (position.isFirst) base.topStart else ZeroCornerSize,
    topEnd = if (position.isFirst) base.topEnd else ZeroCornerSize,
    bottomEnd = if (position.isLast) base.bottomEnd else ZeroCornerSize,
    bottomStart = if (position.isLast) base.bottomStart else ZeroCornerSize,
)

/**
 * One item of a card-styled group in a lazy list. A card can't wrap several lazy items, so each
 * item draws its own slice of the card: screen margins, card background, the rounded corners for
 * its [position] and a divider above it inset by [dividerInset] unless it is the first row.
 */
@Composable
fun ActuaGroupedItem(
    position: GroupPosition,
    modifier: Modifier = Modifier,
    dividerInset: Dp = Sizes.cardDividerInset,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .padding(horizontal = Spacing.screenHorizontal)
            .fillMaxWidth()
            .clip(groupedItemShape(position, MaterialTheme.shapes.large))
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        if (!position.isFirst) ActuaCardDivider(inset = dividerInset)
        content()
    }
}

enum class ActuaHeroSize { Large, Medium }

/**
 * A centered amount with an optional [caption] above it, a [supportingText] line, an [action]
 * (e.g. a sign switch chip) and a [footnote] below it. [Large][ActuaHeroSize.Large] is for
 * editors; [Medium][ActuaHeroSize.Medium] is for overview screens that list content below.
 */
@Composable
fun ActuaHeroAmount(
    amount: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
    captionColor: Color = amountColor,
    size: ActuaHeroSize = ActuaHeroSize.Large,
    supportingText: String? = null,
    contentDescription: String = listOfNotNull(caption, amount).joinToString(" "),
    onClickLabel: String? = null,
    onClick: (() -> Unit)? = null,
    amountTrailing: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
    footnote: String? = null,
) {
    val style = when (size) {
        ActuaHeroSize.Large -> AmountTypography.heroAmount.let {
            if (amount.length > HERO_LONG_AMOUNT_LENGTH) it.copy(fontSize = 32.sp) else it
        }
        ActuaHeroSize.Medium -> AmountTypography.mediumHeroAmount.let {
            if (amount.length > HERO_LONG_AMOUNT_LENGTH) it.copy(fontSize = 26.sp) else it
        }
    }
    Column(
        modifier = modifier.fillMaxWidth().padding(top = Spacing.sm, bottom = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        caption?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = captionColor) }
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.large)
                .then(
                    if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, onClick = onClick)
                    else Modifier,
                )
                .padding(horizontal = Spacing.lg, vertical = Spacing.xs)
                .semantics(mergeDescendants = true) { this.contentDescription = contentDescription },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(amount, style = style, color = amountColor, maxLines = 1, textAlign = TextAlign.Center)
            amountTrailing?.invoke()
        }
        supportingText?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        action?.invoke()
        footnote?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Amounts longer than this (sign and currency included) use a smaller hero size to fit. */
private const val HERO_LONG_AMOUNT_LENGTH = 11

/** The form's main action, full width and pinned at the bottom (above the keyboard). */
@Composable
fun ActuaPrimaryActionBar(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.md)
            .height(Sizes.primaryButtonHeight),
        shape = MaterialTheme.shapes.large,
    ) {
        icon?.let { Icon(it, contentDescription = null) }
        Text(text, modifier = Modifier.padding(start = if (icon != null) Spacing.sm else Spacing.none))
    }
}

/** A full-width tonal action inside a form, e.g. "Split into multiple categories". */
@Composable
fun ActuaSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(Sizes.secondaryButtonHeight),
        shape = MaterialTheme.shapes.large,
    ) {
        icon?.let { Icon(it, contentDescription = null) }
        Text(text, modifier = Modifier.padding(start = if (icon != null) Spacing.sm else Spacing.none))
    }
}
