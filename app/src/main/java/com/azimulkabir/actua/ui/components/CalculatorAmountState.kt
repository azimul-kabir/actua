package com.azimulkabir.actua.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import java.math.BigDecimal
import java.math.RoundingMode

/** Port of iOS AmountInputField's calculator-style amount entry. */
class CalculatorAmountState(
    initialCents: Long = 0,
    private val allowsNegative: Boolean = false,
    private val conventionalAmountEntry: Boolean = false,
    /** Decimal places of the budget's currency; amounts are integers in these minor units. */
    private val decimalPlaces: Int = CurrencyDisplay.decimalPlaces,
) {
    enum class Operator(val symbol: String) { ADD("+"), SUBTRACT("−"), MULTIPLY("×"), DIVIDE("÷") }

    private var operandCents = initialCents
    private var accumulatorCents: Long? = null
    private var pending: Operator? = null
    private var hasOperand = initialCents != 0L
    private var decimalDigits: Int? = null
    private val expressionParts = mutableListOf<String>()
    private val scale = minorUnitsPerWhole(decimalPlaces)

    // Bumped by setCents so composables that read `cents` recompose after a programmatic change.
    private var version by mutableIntStateOf(0)

    val cents: Long
        get() = version.let { accumulatorCents?.let { left ->
            if (hasOperand) apply(left, operandCents, requireNotNull(pending)) else left
        } ?: operandCents }

    val display: String
        get() {
            val operand = format(operandCents)
            val left = accumulatorCents ?: return operand
            val op = pending ?: return operand
            return if (hasOperand) "${format(left)} ${op.symbol} $operand" else "${format(left)} ${op.symbol}"
        }

    val expressionDisplay: String
        get() = buildList {
            addAll(expressionParts)
            if (hasOperand || expressionParts.isEmpty()) add(formatCompact(operandCents))
        }.joinToString(" ")

    fun digit(value: Int) {
        require(value in 0..9)
        val sign = if (operandCents < 0) -1 else 1
        val magnitude = kotlin.math.abs(operandCents)
        if (conventionalAmountEntry) {
            val typed = decimalDigits
            operandCents = sign * when {
                typed == null -> {
                    val whole = magnitude / scale
                    if (whole > (Long.MAX_VALUE / scale - value) / 10) magnitude
                    else (whole * 10 + value) * scale
                }
                // The next fraction digit: keep the digits typed so far, put [value] after them.
                typed < decimalPlaces -> {
                    val place = minorUnitsPerWhole(decimalPlaces - 1 - typed)
                    magnitude / (place * 10) * (place * 10) + value * place
                }
                else -> magnitude
            }
            decimalDigits = typed?.let { minOf(decimalPlaces, it + 1) }
        } else if (magnitude <= (Long.MAX_VALUE - value) / 10) {
            operandCents = sign * (magnitude * 10 + value)
        }
        hasOperand = true
    }

    fun decimalPoint() {
        if (conventionalAmountEntry && decimalDigits == null && decimalPlaces > 0) decimalDigits = 0
    }

    fun backspace() {
        if (hasOperand) {
            if (conventionalAmountEntry) {
                val sign = if (operandCents < 0) -1 else 1
                val magnitude = kotlin.math.abs(operandCents)
                val typed = decimalDigits
                operandCents = sign * when {
                    typed == null -> magnitude / (scale * 10) * scale
                    typed == 0 -> magnitude
                    // Clear the last typed fraction digit and everything after it.
                    else -> {
                        val place = minorUnitsPerWhole(decimalPlaces - typed + 1)
                        magnitude / place * place
                    }
                }
                decimalDigits = typed?.let { if (it == 0) null else it - 1 }
            } else operandCents /= 10
            hasOperand = operandCents != 0L
        } else if (pending != null) {
            operandCents = accumulatorCents ?: 0
            accumulatorCents = null
            pending = null
            hasOperand = operandCents != 0L
            expressionParts.clear()
        }
    }

    fun clear() {
        operandCents = 0
        accumulatorCents = null
        pending = null
        hasOperand = false
        decimalDigits = null
        expressionParts.clear()
    }

    /** Replaces the entry with [value], discarding any pending expression. */
    fun setCents(value: Long) {
        clear()
        operandCents = if (allowsNegative) value else kotlin.math.abs(value)
        hasOperand = operandCents != 0L
        version++
    }

    fun toggleSign() {
        if (allowsNegative) operandCents = -operandCents
    }

    fun operator(operator: Operator) {
        if (accumulatorCents == null && !hasOperand) return
        if (hasOperand) {
            expressionParts += formatCompact(operandCents)
            expressionParts += operator.symbol
        } else if (expressionParts.isNotEmpty()) {
            expressionParts[expressionParts.lastIndex] = operator.symbol
        }
        if (hasOperand) {
            accumulatorCents = accumulatorCents?.let { apply(it, operandCents, requireNotNull(pending)) }
                ?: operandCents
            operandCents = 0
            hasOperand = false
            decimalDigits = null
        }
        pending = operator
    }

    fun finish(): Long {
        val result = cents
        accumulatorCents = null
        pending = null
        operandCents = result
        hasOperand = result != 0L
        expressionParts.clear()
        return result
    }

    private fun apply(left: Long, right: Long, operator: Operator): Long = when (operator) {
        Operator.ADD -> left + right
        Operator.SUBTRACT -> left - right
        Operator.MULTIPLY -> BigDecimal(left).multiply(BigDecimal(right))
            .divide(BigDecimal(scale), 0, RoundingMode.HALF_UP).longValueExact()
        Operator.DIVIDE -> if (right == 0L) left else BigDecimal(left).multiply(BigDecimal(scale))
            .divide(BigDecimal(right), 0, RoundingMode.HALF_UP).longValueExact()
    }.let { if (allowsNegative) it else kotlin.math.abs(it) }

    private fun format(value: Long): String {
        val magnitude = kotlin.math.abs(value)
        return buildString {
            if (value < 0) append('−')
            append(magnitude / scale)
            if (decimalPlaces > 0) {
                append('.')
                append((magnitude % scale).toString().padStart(decimalPlaces, '0'))
            }
        }
    }

    private fun formatCompact(value: Long): String {
        val formatted = format(value)
        val zeroFraction = "." + "0".repeat(decimalPlaces)
        return if (decimalPlaces > 0 && formatted.endsWith(zeroFraction)) formatted.dropLast(zeroFraction.length) else formatted
    }
}
