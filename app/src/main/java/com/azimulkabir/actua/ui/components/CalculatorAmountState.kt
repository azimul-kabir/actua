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
    /**
     * Pressing − before any number makes the first number negative ("−1 − 0.01"), for a keypad whose ±
     * key does something else (the transaction amount, where ± flips expense and income).
     */
    private val leadingMinus: Boolean = false,
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
    /**
     * The number typed after × or ÷, as typed ("3", "1.5"): a plain factor, not an amount, as in
     * Actual's arithmetic amount input (#1028). Null while the operand is an amount.
     */
    private var factor: String? = null

    /**
     * ± was pressed before any digit of the next amount, so that amount starts negative. Needed because
     * a zero operand has no sign of its own.
     */
    private var negativeNext = false

    private val factorPending: Boolean get() = pending == Operator.MULTIPLY || pending == Operator.DIVIDE

    // Bumped by setCents so composables that read `cents` recompose after a programmatic change.
    private var version by mutableIntStateOf(0)

    val cents: Long
        get() = version.let { accumulatorCents?.let { left ->
            if (hasOperand) apply(left, requireNotNull(pending)) else left
        } ?: operandCents }

    val display: String
        get() {
            val operand = factor ?: signedZero(format(operandCents))
            val left = accumulatorCents ?: return operand
            val op = pending ?: return operand
            return if (hasOperand) "${format(left)} ${op.symbol} $operand" else "${format(left)} ${op.symbol}"
        }

    val expressionDisplay: String
        get() = buildList {
            addAll(expressionParts)
            if (hasOperand || expressionParts.isEmpty()) add(operandText())
        }.joinToString(" ")

    private fun operandText(): String = factor ?: signedZero(formatCompact(operandCents))

    /** A zero that ± has marked negative reads "−0" until a digit makes it a real amount. */
    private fun signedZero(text: String): String = if (negativeNext && operandCents == 0L) "−$text" else text

    fun digit(value: Int) {
        require(value in 0..9)
        if (factorPending) {
            val typed = factor.orEmpty()
            // Up to 9 whole and 4 fraction digits keep the arithmetic well inside Long.
            val whole = typed.substringBefore('.').trimStart('-')
            if (if ('.' in typed) typed.substringAfter('.').length < 4 else whole.length < 9) {
                factor = if (typed == "0" || typed == "-0") typed.dropLast(1) + value else typed + value
            }
            hasOperand = true
            return
        }
        val sign = if (operandCents < 0 || (operandCents == 0L && negativeNext)) -1 else 1
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
        if (operandCents != 0L) negativeNext = false
        hasOperand = true
    }

    fun decimalPoint() {
        if (factorPending) {
            val typed = factor.orEmpty()
            if ('.' !in typed) factor = (if (typed.isEmpty() || typed == "-") typed + "0" else typed) + "."
            hasOperand = true
            return
        }
        if (conventionalAmountEntry && decimalDigits == null && decimalPlaces > 0) decimalDigits = 0
    }

    fun backspace() {
        if (negativeNext && operandCents == 0L) {
            // Takes back the ± (and any zeros typed after it) before touching the operator.
            negativeNext = false
            hasOperand = false
            decimalDigits = null
            return
        }
        if (hasOperand && factor != null) {
            factor = requireNotNull(factor).dropLast(1).takeIf { it.isNotEmpty() && it != "-" }
            hasOperand = factor != null
        } else if (hasOperand) {
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
        factor = null
        negativeNext = false
        expressionParts.clear()
    }

    /** True when there is nothing to clear: no amount, pending operator or typed factor. */
    val isEmpty: Boolean
        get() = operandCents == 0L && accumulatorCents == null && pending == null && factor == null && !negativeNext

    /** Everything that makes up the entry, so a Clear can be undone with [restore]. */
    class Snapshot internal constructor(
        internal val operandCents: Long,
        internal val accumulatorCents: Long?,
        internal val pending: Operator?,
        internal val hasOperand: Boolean,
        internal val decimalDigits: Int?,
        internal val factor: String?,
        internal val negativeNext: Boolean,
        internal val expressionParts: List<String>,
    )

    fun snapshot(): Snapshot = Snapshot(
        operandCents, accumulatorCents, pending, hasOperand, decimalDigits, factor, negativeNext, expressionParts.toList(),
    )

    /** Puts the entry back exactly as it was when [snapshot] was taken. */
    fun restore(snapshot: Snapshot) {
        operandCents = snapshot.operandCents
        accumulatorCents = snapshot.accumulatorCents
        pending = snapshot.pending
        hasOperand = snapshot.hasOperand
        decimalDigits = snapshot.decimalDigits
        factor = snapshot.factor
        negativeNext = snapshot.negativeNext
        expressionParts.clear()
        expressionParts.addAll(snapshot.expressionParts)
        version++
    }

    /** Replaces the entry with [value], discarding any pending expression. */
    fun setCents(value: Long) {
        clear()
        operandCents = if (allowsNegative) value else kotlin.math.abs(value)
        hasOperand = operandCents != 0L
        version++
    }

    fun toggleSign() {
        if (!allowsNegative) return
        val typed = factor
        when {
            factorPending && typed != null -> factor = if (typed.startsWith("-")) typed.drop(1) else "-$typed"
            // No factor typed yet: it starts negative.
            factorPending -> factor = "-"
            // A typed amount (or a result kept by =) changes sign in place.
            operandCents != 0L -> operandCents = -operandCents
            // Nothing typed yet: the next amount starts negative, so (−1) − 0.01 can be entered.
            else -> negativeNext = !negativeNext
        }
    }

    fun operator(operator: Operator) {
        if (accumulatorCents == null && !hasOperand) {
            if (leadingMinus && allowsNegative && operator == Operator.SUBTRACT) negativeNext = !negativeNext
            return
        }
        if (hasOperand) {
            expressionParts += operandText()
            expressionParts += operator.symbol
        } else if (expressionParts.isNotEmpty()) {
            expressionParts[expressionParts.lastIndex] = operator.symbol
        }
        if (hasOperand) {
            accumulatorCents = accumulatorCents?.let { apply(it, requireNotNull(pending)) }
                ?: operandCents
            operandCents = 0
            hasOperand = false
            decimalDigits = null
            factor = null
        } else {
            factor = null
        }
        negativeNext = false
        pending = operator
    }

    fun finish(): Long {
        val result = cents
        accumulatorCents = null
        pending = null
        factor = null
        decimalDigits = null
        negativeNext = false
        operandCents = result
        hasOperand = result != 0L
        expressionParts.clear()
        return result
    }

    /** [left] combined with the current operand: an amount for + and −, a plain factor for × and ÷. */
    private fun apply(left: Long, operator: Operator): Long {
        val by = factor?.toBigDecimalOrNull() ?: BigDecimal.ONE
        return when (operator) {
            Operator.ADD -> left + operandCents
            Operator.SUBTRACT -> left - operandCents
            Operator.MULTIPLY -> BigDecimal(left).multiply(by).setScale(0, RoundingMode.HALF_UP).toLongOr(left)
            Operator.DIVIDE -> if (by.signum() == 0) left
                else BigDecimal(left).divide(by, 0, RoundingMode.HALF_UP).toLongOr(left)
        }.let { if (allowsNegative) it else kotlin.math.abs(it) }
    }

    /** A result too large for an amount leaves the running total unchanged, like ÷ 0. */
    private fun BigDecimal.toLongOr(fallback: Long): Long =
        if (toBigInteger().bitLength() < 63) longValueExact() else fallback

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
