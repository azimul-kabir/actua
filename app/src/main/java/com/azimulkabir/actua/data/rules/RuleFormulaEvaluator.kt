package com.azimulkabir.actua.data.rules

import kotlin.math.pow

internal object RuleFormulaEvaluator {
    fun evaluate(
        formula: String,
        variables: Map<String, Any?>,
        balanceOf: (String) -> Long,
    ): Any? {
        require(formula.startsWith("=")) { "Formula must start with =" }
        require(formula.length <= 8192) { "Formula is too long" }
        val result = Parser(formula.drop(1), variables, balanceOf).parse()
        return if (result is Number) kotlin.math.floor(result.toDouble() * 100.0 + 0.5) else result
    }

    private class Parser(
        private val source: String,
        variables: Map<String, Any?>,
        private val balanceOf: (String) -> Long,
    ) {
        private val variables = variables.mapKeys { it.key.uppercase() }
        private var index = 0
        private var nesting = 0

        fun parse(): Any? {
            val value = comparison()
            whitespace()
            require(index == source.length) { "Unexpected formula input" }
            return value
        }

        private fun comparison(): Any? {
            var left = concatenate()
            while (true) {
                val operator = take("=", "<>", ">=", "<=", ">", "<") ?: return left
                val right = concatenate()
                val comparison = compare(left, right)
                left = when (operator) {
                    "=" -> comparison == 0
                    "<>" -> comparison != 0
                    ">" -> comparison > 0
                    ">=" -> comparison >= 0
                    "<" -> comparison < 0
                    else -> comparison <= 0
                }
            }
        }

        private fun concatenate(): Any? {
            var left = additive()
            while (take("&") != null) left = string(left) + string(additive())
            return left
        }

        private fun additive(): Any? {
            var left = multiplicative()
            while (true) {
                left = when {
                    take("+") != null -> number(left) + number(multiplicative())
                    take("-") != null -> number(left) - number(multiplicative())
                    else -> return left
                }
            }
        }

        private fun multiplicative(): Any? {
            var left = power()
            while (true) {
                left = when {
                    take("*") != null -> number(left) * number(power())
                    take("/") != null -> number(left) / number(power())
                    else -> return left
                }
            }
        }

        private fun power(): Any? {
            val left = unary()
            val value = if (take("^") != null) number(left).pow(number(nested { power() })) else left
            return if (take("%") != null) number(value) / 100.0 else value
        }

        private fun unary(): Any? = when {
            take("+") != null -> number(nested { unary() })
            take("-") != null -> -number(nested { unary() })
            else -> primary()
        }

        private fun primary(): Any? {
            whitespace()
            require(index < source.length) { "Expected formula value" }
            if (take("(") != null) return nested { comparison() }.also {
                require(take(")") != null) { "Expected closing parenthesis" }
            }
            if (source[index] == '"') return quoted()
            if (source[index].isDigit() || source[index] == '.') return numericLiteral()
            val identifier = identifier()
            if (identifier.equals("TRUE", true)) return true
            if (identifier.equals("FALSE", true)) return false
            if (take("(") != null) {
                val arguments = mutableListOf<Any?>()
                if (take(")") == null) {
                    do { arguments += nested { comparison() } } while (take(",") != null)
                    require(take(")") != null) { "Expected closing parenthesis" }
                }
                return function(identifier.uppercase(), arguments)
            }
            require(variables.containsKey(identifier.uppercase())) { "Unknown formula field: $identifier" }
            return variables[identifier.uppercase()]
        }

        private fun function(name: String, args: List<Any?>): Any? {
            fun arg(index: Int) = args.getOrNull(index)
            fun values() = args.mapNotNull { (it as? Iterable<*>)?.toList() ?: listOf(it) }
                .flatten().filterNotNull()
            return when (name) {
                "BALANCE_OF" -> balanceOf(string(requireNotNull(arg(0)))).toDouble()
                "TODAY" -> variables["TODAY"]
                "YEAR", "MONTH", "DAY" -> {
                    val date = java.time.LocalDate.parse(string(requireNotNull(arg(0))))
                    when (name) {
                        "YEAR" -> date.year.toDouble()
                        "MONTH" -> date.monthValue.toDouble()
                        else -> date.dayOfMonth.toDouble()
                    }
                }
                "DATE" -> java.time.LocalDate.of(
                    number(requireNotNull(arg(0))).toInt(),
                    number(requireNotNull(arg(1))).toInt(),
                    number(requireNotNull(arg(2))).toInt(),
                ).toString()
                "ABS" -> kotlin.math.abs(number(requireNotNull(arg(0))))
                "ROUND" -> number(requireNotNull(arg(0))).round(number(arg(1) ?: 0.0).toInt())
                "ROUNDUP" -> directedRound(number(requireNotNull(arg(0))), number(arg(1) ?: 0.0).toInt(), true)
                "ROUNDDOWN", "TRUNC" -> directedRound(number(requireNotNull(arg(0))), number(arg(1) ?: 0.0).toInt(), false)
                "INTEGER_TO_AMOUNT" -> number(requireNotNull(arg(0))) / 100.0.pow(number(arg(1) ?: 2.0))
                "FIXED" -> number(requireNotNull(arg(0))).round(number(arg(1) ?: 0.0).toInt())
                    .toBigDecimal().setScale(number(arg(1) ?: 0.0).toInt().coerceIn(0, 12))
                    .toPlainString()
                "INT" -> kotlin.math.floor(number(requireNotNull(arg(0))))
                "MIN" -> values().minOfOrNull(::number) ?: 0.0
                "MAX" -> values().maxOfOrNull(::number) ?: 0.0
                "SUM" -> values().sumOf(::number)
                "AVERAGE" -> values().takeIf { it.isNotEmpty() }?.map(::number)?.average() ?: 0.0
                "COUNT" -> values().count { it is Number }.toDouble()
                "IF" -> if (truth(requireNotNull(arg(0)))) arg(1) else arg(2)
                "IFERROR" -> arg(0) ?: arg(1)
                "AND" -> args.all(::truth)
                "OR" -> args.any(::truth)
                "NOT" -> !truth(requireNotNull(arg(0)))
                "MOD" -> number(requireNotNull(arg(0))) % number(requireNotNull(arg(1)))
                "POWER" -> number(requireNotNull(arg(0))).pow(number(requireNotNull(arg(1))))
                "SQRT" -> kotlin.math.sqrt(number(requireNotNull(arg(0))))
                "CONCATENATE" -> args.joinToString("") { string(it) }
                "LEN" -> string(requireNotNull(arg(0))).length.toDouble()
                "LOWER" -> string(requireNotNull(arg(0))).lowercase()
                "UPPER" -> string(requireNotNull(arg(0))).uppercase()
                "LEFT" -> string(requireNotNull(arg(0))).take(number(arg(1) ?: 1.0).toInt().coerceAtLeast(0))
                "RIGHT" -> string(requireNotNull(arg(0))).takeLast(number(arg(1) ?: 1.0).toInt().coerceAtLeast(0))
                else -> error("Unsupported formula function: $name")
            }
        }

        private fun quoted(): String {
            index++
            val result = StringBuilder()
            while (index < source.length) {
                val char = source[index++]
                if (char == '"') {
                    if (index < source.length && source[index] == '"') {
                        index++
                        result.append('"')
                    } else return result.toString()
                } else result.append(char)
            }
            error("Unclosed formula string")
        }

        private fun numericLiteral(): Double {
            val start = index
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
            if (index < source.length && source[index] in "eE") {
                index++
                if (index < source.length && source[index] in "+-") index++
                while (index < source.length && source[index].isDigit()) index++
            }
            return source.substring(start, index).toDouble()
        }

        private fun identifier(): String {
            whitespace()
            val start = index
            while (index < source.length && (source[index].isLetterOrDigit() || source[index] == '_' || source[index] == '.')) index++
            require(start != index) { "Expected formula field" }
            return source.substring(start, index)
        }

        private fun take(vararg candidates: String): String? {
            whitespace()
            val candidate = candidates.firstOrNull { source.startsWith(it, index) } ?: return null
            index += candidate.length
            return candidate
        }

        private fun whitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun <T> nested(block: () -> T): T {
            require(++nesting <= 64) { "Formula is nested too deeply" }
            return try { block() } finally { nesting-- }
        }

        private fun compare(a: Any?, b: Any?): Int =
            if (a is Number && b is Number) a.toDouble().compareTo(b.toDouble())
            else string(a).compareTo(string(b), ignoreCase = true)

        private fun number(value: Any?): Double = when (value) {
            is Number -> value.toDouble()
            is Boolean -> if (value) 1.0 else 0.0
            null, "" -> 0.0
            else -> value.toString().toDouble()
        }

        private fun string(value: Any?): String = value?.toString().orEmpty()
        private fun truth(value: Any?): Boolean = when (value) {
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            null -> false
            else -> value.toString().equals("true", true)
        }

        private fun Double.round(digits: Int): Double {
            val scale = 10.0.pow(digits.coerceIn(-12, 12))
            val scaled = this * scale
            return (if (scaled < 0) -kotlin.math.floor(-scaled + 0.5) else kotlin.math.floor(scaled + 0.5)) / scale
        }

        private fun directedRound(value: Double, digits: Int, up: Boolean): Double {
            val scale = 10.0.pow(digits.coerceIn(-12, 12))
            val scaled = value * scale
            val rounded = when {
                up && scaled < 0 -> kotlin.math.floor(scaled)
                up -> kotlin.math.ceil(scaled)
                scaled < 0 -> kotlin.math.ceil(scaled)
                else -> kotlin.math.floor(scaled)
            }
            return rounded / scale
        }

    }
}
