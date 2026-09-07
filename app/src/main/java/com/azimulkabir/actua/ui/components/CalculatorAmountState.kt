package com.azimulkabir.actua.ui.components

import java.math.BigDecimal
import java.math.RoundingMode

/** Port of iOS AmountInputField's calculator-style amount entry. */
class CalculatorAmountState(
    initialCents: Long = 0,
    private val allowsNegative: Boolean = false,
    private val conventionalAmountEntry: Boolean = false,
) {
    enum class Operator(val symbol: String) { ADD("+"), SUBTRACT("−"), MULTIPLY("×"), DIVIDE("÷") }

    private var operandCents = initialCents
    private var accumulatorCents: Long? = null
    private var pending: Operator? = null
    private var hasOperand = initialCents != 0L
    private var decimalDigits: Int? = null

    val cents: Long
        get() = accumulatorCents?.let { left ->
            if (hasOperand) apply(left, operandCents, requireNotNull(pending)) else left
        } ?: operandCents

    val display: String
        get() {
            val operand = format(operandCents)
            val left = accumulatorCents ?: return operand
            val op = pending ?: return operand
            return if (hasOperand) "${format(left)} ${op.symbol} $operand" else "${format(left)} ${op.symbol}"
        }

    fun digit(value: Int) {
        require(value in 0..9)
        val sign = if (operandCents < 0) -1 else 1
        val magnitude = kotlin.math.abs(operandCents)
        if (conventionalAmountEntry) {
            operandCents = sign * when (decimalDigits) {
                null -> {
                    val whole = magnitude / 100
                    if (whole > (Long.MAX_VALUE / 100 - value) / 10) magnitude
                    else (whole * 10 + value) * 100
                }
                0 -> magnitude / 100 * 100 + value * 10
                1 -> magnitude / 100 * 100 + (magnitude % 100 / 10) * 10 + value
                else -> magnitude
            }
            decimalDigits = decimalDigits?.let { minOf(2, it + 1) }
        } else if (magnitude <= (Long.MAX_VALUE - value) / 10) {
            operandCents = sign * (magnitude * 10 + value)
        }
        hasOperand = true
    }

    fun decimalPoint() {
        if (conventionalAmountEntry && decimalDigits == null) decimalDigits = 0
    }

    fun backspace() {
        if (hasOperand) {
            if (conventionalAmountEntry) {
                val sign = if (operandCents < 0) -1 else 1
                val magnitude = kotlin.math.abs(operandCents)
                operandCents = sign * when (decimalDigits) {
                    2 -> magnitude / 100 * 100 + (magnitude % 100 / 10) * 10
                    1 -> magnitude / 100 * 100
                    0 -> magnitude
                    else -> magnitude / 1000 * 100
                }
                decimalDigits = when (decimalDigits) { 2 -> 1; 1 -> 0; 0 -> null; else -> null }
            } else operandCents /= 10
            hasOperand = operandCents != 0L
        } else if (pending != null) {
            operandCents = accumulatorCents ?: 0
            accumulatorCents = null
            pending = null
            hasOperand = operandCents != 0L
        }
    }

    fun clear() {
        operandCents = 0
        accumulatorCents = null
        pending = null
        hasOperand = false
        decimalDigits = null
    }

    fun toggleSign() {
        if (allowsNegative) operandCents = -operandCents
    }

    fun operator(operator: Operator) {
        if (accumulatorCents == null && !hasOperand) return
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
        return result
    }

    private fun apply(left: Long, right: Long, operator: Operator): Long = when (operator) {
        Operator.ADD -> left + right
        Operator.SUBTRACT -> left - right
        Operator.MULTIPLY -> BigDecimal(left).multiply(BigDecimal(right))
            .divide(BigDecimal(100), 0, RoundingMode.HALF_UP).longValueExact()
        Operator.DIVIDE -> if (right == 0L) left else BigDecimal(left).multiply(BigDecimal(100))
            .divide(BigDecimal(right), 0, RoundingMode.HALF_UP).longValueExact()
    }.let { if (allowsNegative) it else kotlin.math.abs(it) }

    private fun format(value: Long): String {
        val magnitude = kotlin.math.abs(value)
        return buildString {
            if (value < 0) append('−')
            append(magnitude / 100)
            append('.')
            append((magnitude % 100).toString().padStart(2, '0'))
        }
    }
}
