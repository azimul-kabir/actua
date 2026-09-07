package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalculatorAmountSheet(
    title: String,
    initialCents: Long,
    conventionalAmountEntry: Boolean = false,
    onDismiss: () -> Unit,
    onApply: (Long) -> Unit,
) {
    val calculator = remember(initialCents, conventionalAmountEntry) {
        CalculatorAmountState(initialCents, conventionalAmountEntry = conventionalAmountEntry)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
    ) {
        CompactCalculatorPad(
            calculator = calculator,
            conventionalAmountEntry = conventionalAmountEntry,
            onClose = onDismiss,
            onDone = { onApply(calculator.finish()) },
        )
    }
}

@Composable
fun CompactCalculatorPad(
    calculator: CalculatorAmountState,
    conventionalAmountEntry: Boolean = false,
    allowSign: Boolean = false,
    doneLabel: String = "Done",
    horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
    onClose: (() -> Unit)? = null,
    onDone: () -> Unit,
) {
    var revision by remember { mutableIntStateOf(0) }
    fun press(key: String) {
        when (key) {
            "C" -> calculator.clear()
            "±" -> calculator.toggleSign()
            "⌫" -> calculator.backspace()
            "." -> calculator.decimalPoint()
            "+" -> calculator.operator(CalculatorAmountState.Operator.ADD)
            "−" -> calculator.operator(CalculatorAmountState.Operator.SUBTRACT)
            "×" -> calculator.operator(CalculatorAmountState.Operator.MULTIPLY)
            "÷" -> calculator.operator(CalculatorAmountState.Operator.DIVIDE)
            else -> calculator.digit(key.toInt())
        }
        revision++
    }
    val utilityKey = if (allowSign) "±" else if (conventionalAmountEntry) "." else "C"
    val rows = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf(utilityKey, "0", "⌫", "+"),
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
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
        rows.forEach { keys ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                keys.forEach { key ->
                    CompactCalculatorKey(
                        label = key,
                        operator = key in setOf("÷", "×", "−", "+"),
                        modifier = Modifier.weight(1f),
                        onClick = { press(key) },
                    )
                }
            }
        }
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().height(44.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(doneLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
    }
    @Suppress("UNUSED_EXPRESSION") revision
}

@Composable
private fun CompactCalculatorKey(
    label: String,
    operator: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(14.dp),
        color = if (operator) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium,
                color = if (operator) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
