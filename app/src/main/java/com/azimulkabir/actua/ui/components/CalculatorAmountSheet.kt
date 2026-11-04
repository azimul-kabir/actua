package com.azimulkabir.actua.ui.components

import android.content.ClipDescription
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.semantics.contentDescription
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
    /** When set, the ± key calls this (e.g. expense ↔ income) instead of negating the amount. */
    onToggleSign: (() -> Unit)? = null,
    toggleSignLabel: String? = null,
) {
    val calculator = remember(conventionalAmountEntry) {
        CalculatorAmountState(initialCents, conventionalAmountEntry = conventionalAmountEntry)
    }
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
                PasteAmountButton(
                    onPasted = { cents ->
                        calculator.setCents(cents)
                        onApply(calculator.cents)
                        onExpressionChange(calculator.expressionDisplay)
                    },
                )
                CompactCalculatorPad(
                    calculator = calculator,
                    conventionalAmountEntry = conventionalAmountEntry,
                    showDisplay = false,
                    onValueChange = onApply,
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
 * A Paste button on the keypad's top edge, shown only while the clipboard holds text. Whether it does is read
 * from the clip's description and kept current with a clipboard listener, which does not read the content, so
 * Android 12+ shows no "pasted from your clipboard" notice until the button is tapped. Tapping it reads the
 * clipboard, replaces the current entry with the amount found, and, when there is no single amount, leaves
 * the entry unchanged and says so.
 */
@Composable
private fun PasteAmountButton(onPasted: (Long) -> Unit) {
    val context = LocalContext.current
    val label = stringResource(R.string.amount_paste)
    val description = stringResource(R.string.amount_paste_description)
    val notFound = stringResource(R.string.amount_paste_not_found)
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    var hasClipboardText by remember { mutableStateOf(clipboard.holdsText()) }
    DisposableEffect(clipboard) {
        val listener = ClipboardManager.OnPrimaryClipChangedListener { hasClipboardText = clipboard.holdsText() }
        hasClipboardText = clipboard.holdsText()
        clipboard?.addPrimaryClipChangedListener(listener)
        onDispose { clipboard?.removePrimaryClipChangedListener(listener) }
    }
    if (!hasClipboardText) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 4.dp),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = {
                val text = clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
                val cents = PastedAmount.parseCents(text, CurrencyDisplay.decimalPlaces)
                if (cents != null) onPasted(cents) else Toast.makeText(context, notFound, Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier.height(36.dp).semantics { contentDescription = description },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        ) {
            Icon(Icons.Outlined.ContentPaste, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label)
        }
    }
}

/** True when the clipboard has a text clip. Reads only the clip's description, never its content. */
private fun ClipboardManager?.holdsText(): Boolean {
    val manager = this ?: return false
    if (!manager.hasPrimaryClip()) return false
    val description = manager.primaryClipDescription ?: return false
    return description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
        description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
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
