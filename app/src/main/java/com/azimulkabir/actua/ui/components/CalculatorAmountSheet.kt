package com.azimulkabir.actua.ui.components

import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

@Composable
fun CalculatorAmountSheet(
    title: String,
    initialCents: Long,
    conventionalAmountEntry: Boolean = false,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit,
    onExpressionChange: (String) -> Unit = {},
    onDone: () -> Unit = {},
    /**
     * Signed arithmetic: ± negates the number being typed and [onApply] receives a result that can be
     * negative, for a caller whose own sign control (an expense and income chip) combines with it.
     * Without it the amount is a size and the keypad never goes negative.
     */
    signedEntry: Boolean = false,
    /** When set, the ± key calls this (e.g. expense ↔ income) instead of negating the amount. */
    onToggleSign: (() -> Unit)? = null,
    toggleSignLabel: String? = null,
) {
    val calculator = remember(conventionalAmountEntry, signedEntry) {
        CalculatorAmountState(
            initialCents,
            allowsNegative = signedEntry,
            conventionalAmountEntry = conventionalAmountEntry,
        )
    }
    // Set by Clear and dropped by the next key, Paste or Undo; while set, the Clear button reads Undo.
    var undoSnapshot by remember { mutableStateOf<CalculatorAmountState.Snapshot?>(null) }
    Popup(
        alignment = Alignment.BottomCenter,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true, dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
        ) {
            Column {
                KeypadActionRow(
                    undoAvailable = undoSnapshot != null,
                    onClear = {
                        if (!calculator.isEmpty) {
                            undoSnapshot = calculator.snapshot()
                            calculator.clear()
                            onApply(calculator.cents)
                            onExpressionChange(calculator.expressionDisplay)
                        }
                    },
                    onUndo = {
                        undoSnapshot?.let(calculator::restore)
                        undoSnapshot = null
                        onApply(calculator.cents)
                        onExpressionChange(calculator.expressionDisplay)
                    },
                    onPasted = { cents ->
                        undoSnapshot = null
                        calculator.setCents(cents)
                        onApply(calculator.cents)
                        onExpressionChange(calculator.expressionDisplay)
                    },
                )
                CompactCalculatorPad(
                    calculator = calculator,
                    conventionalAmountEntry = conventionalAmountEntry,
                    showDisplay = false,
                    // Any key press commits a Clear, so Undo only lasts until the next key.
                    onValueChange = { cents -> undoSnapshot = null; onApply(cents) },
                    onExpressionChange = onExpressionChange,
                    onDone = { onApply(calculator.finish()); onDismiss(); onDone() },
                    onToggleSign = onToggleSign,
                    toggleSignLabel = toggleSignLabel,
                )
            }
        }
    }
}

/**
 * The slim row of text actions above the keys: Clear (which becomes Undo right after it is used) on the left
 * and Paste on the right. Each label stays on one line and is cut with an ellipsis rather than wrapping.
 */
@Composable
private fun KeypadActionRow(
    undoAvailable: Boolean,
    onClear: () -> Unit,
    onUndo: () -> Unit,
    onPasted: (Long) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KeypadTextButton(
            label = stringResource(if (undoAvailable) R.string.amount_undo else R.string.amount_clear),
            description = stringResource(
                if (undoAvailable) R.string.amount_undo_description else R.string.amount_clear_description,
            ),
            onClick = if (undoAvailable) onUndo else onClear,
            announceChanges = true,
            modifier = Modifier.weight(1f, fill = false),
        )
        PasteAmountButton(onPasted, Modifier.weight(1f, fill = false))
    }
}

/**
 * A Paste button. The clipboard is read only when it is tapped (Android 12+ tells the user when an app reads
 * it), the amount replaces the current entry, and a clipboard without a single amount leaves the entry
 * unchanged and says so.
 */
@Composable
private fun PasteAmountButton(onPasted: (Long) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val notFound = stringResource(R.string.amount_paste_not_found)
    KeypadTextButton(
        label = stringResource(R.string.amount_paste),
        description = stringResource(R.string.amount_paste_description),
        onClick = {
            val text = context.getSystemService(ClipboardManager::class.java)
                ?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
            val cents = PastedAmount.parseCents(text, CurrencyDisplay.decimalPlaces)
            if (cents != null) onPasted(cents) else Toast.makeText(context, notFound, Toast.LENGTH_SHORT).show()
        },
        modifier = modifier,
    )
}

/** A text-only button one line tall; a long label (large font, other language) is cut with an ellipsis. */
@Composable
private fun KeypadTextButton(
    label: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Has TalkBack announce the new label when it changes, as Clear becomes Undo. */
    announceChanges: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.height(36.dp).semantics {
            contentDescription = description
            if (announceChanges) liveRegion = LiveRegionMode.Polite
        },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
    ) {
        Text(label, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun CompactCalculatorPad(
    calculator: CalculatorAmountState,
    conventionalAmountEntry: Boolean = false,
    allowSign: Boolean = false,
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    moveMoneyMode: Boolean = false,
    displayLabel: String? = null,
    showDisplay: Boolean = true,
    onValueChange: (Long) -> Unit = {},
    onExpressionChange: (String) -> Unit = {},
    canFinish: (Long) -> Boolean = { true },
    onClose: (() -> Unit)? = null,
    onToggleSign: (() -> Unit)? = null,
    toggleSignLabel: String? = null,
    onDone: () -> Unit,
) {
    var revision by remember { mutableIntStateOf(0) }
    fun press(key: String) {
        when (key) {
            "C" -> calculator.clear()
            "±" -> onToggleSign?.invoke() ?: calculator.toggleSign()
            "⌫" -> calculator.backspace()
            "00" -> { calculator.digit(0); calculator.digit(0) }
            "." -> calculator.decimalPoint()
            "+" -> calculator.operator(CalculatorAmountState.Operator.ADD)
            "−" -> calculator.operator(CalculatorAmountState.Operator.SUBTRACT)
            "×" -> calculator.operator(CalculatorAmountState.Operator.MULTIPLY)
            "÷" -> calculator.operator(CalculatorAmountState.Operator.DIVIDE)
            "=" -> calculator.finish()
            "✓" -> if (canFinish(calculator.cents)) onDone()
            else -> calculator.digit(key.toInt())
        }
        revision++
        if (key != "✓") {
            onValueChange(calculator.cents)
            onExpressionChange(calculator.expressionDisplay)
        }
    }
    // Five equal columns; ✓ spans the last two (#1028).
    val rows = listOf(
        listOf("7", "8", "9", "×", "÷"),
        listOf("4", "5", "6", "+", "−"),
        listOf("1", "2", "3", "⌫", "="),
        listOf(".", "0", "±", "✓"),
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showDisplay) {
            Row(
                modifier = Modifier.fillMaxWidth().height(42.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                onClose?.let {
                    IconButton(onClick = it, modifier = Modifier.height(38.dp).width(38.dp)) {
                        Icon(Icons.Outlined.Close, contentDescription = "Close calculator")
                    }
                }
                Spacer(Modifier.weight(1f))
                displayLabel?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 10.dp),
                    )
                }
                Text(
                    calculator.display,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                Box(
                    Modifier.padding(start = 3.dp).height(26.dp).width(2.dp)
                        .clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary),
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 4.dp
            // Sized in whole pixels so ✓ is exactly two keys and a gap wide at every density.
            val density = LocalDensity.current
            val gapPx = with(density) { gap.roundToPx() }
            val keyPx = (constraints.maxWidth - gapPx * 4) / 5
            val keyWidth = with(density) { keyPx.toDp() }
            val wideKeyWidth = with(density) { (keyPx * 2 + gapPx).toDp() }
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                rows.forEach { keys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        keys.forEach { key ->
                            CompactCalculatorKey(
                                label = key,
                                operator = key in setOf("÷", "×", "−", "+", "="),
                                confirm = key == "✓",
                                enabled = key != "✓" || canFinish(calculator.cents),
                                description = if (key == "±" && onToggleSign != null) toggleSignLabel
                                    else calculatorKeyDescriptions[key],
                                modifier = Modifier.width(if (key == "✓") wideKeyWidth else keyWidth),
                                onClick = { press(key) },
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
    }
    @Suppress("UNUSED_EXPRESSION") revision
}

/** Spoken labels for keys whose symbol TalkBack reads poorly. */
private val calculatorKeyDescriptions = mapOf(
    "×" to "Multiply", "÷" to "Divide", "+" to "Add", "−" to "Subtract", "=" to "Equals",
    "." to "Decimal point", "⌫" to "Backspace", "✓" to "Done",
)

@Composable
private fun CompactCalculatorKey(
    label: String,
    operator: Boolean,
    confirm: Boolean = false,
    enabled: Boolean = true,
    description: String? = null,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
        shape = MaterialTheme.shapes.large,
        color = when {
            confirm -> MaterialTheme.colorScheme.primaryContainer
            operator -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = when {
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    confirm -> MaterialTheme.colorScheme.onPrimaryContainer
                    operator -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}
