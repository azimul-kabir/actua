package com.azimulkabir.actua.ui.components

/**
 * Reads an amount out of clipboard text for the keypad's Paste button, as integer minor units.
 *
 * The text must contain exactly one number; anything around it (a currency symbol or code, a sign, words) is
 * ignored, and the sign is dropped because the keypad's expense/income choice decides it. Digits are read
 * without floating point.
 *
 * Separators follow the usual rules: with both `.` and `,` the last one is the decimal mark and the other
 * groups thousands; a lone separator followed by exactly three digits is grouping (`1,234`) unless the
 * currency has three decimal places; otherwise a lone separator is the decimal mark. Spaces inside a number
 * are not accepted (a no-break or narrow no-break space and `'` are, as grouping marks), so "12 50" is two
 * numbers and is refused rather than guessed. Returns null when no single unambiguous amount is found, or
 * when it has more fraction digits than [decimalPlaces].
 */
internal object PastedAmount {
    private val number = Regex("""[.,]\d+|\d(?:[\d.,'  ]*\d)?""")

    fun parseCents(text: CharSequence?, decimalPlaces: Int): Long? {
        if (text.isNullOrBlank() || decimalPlaces < 0) return null
        val tokens = number.findAll(text).map { it.value }.toList()
        val token = tokens.singleOrNull() ?: return null
        val cleaned = token.filterNot { it == '\'' || it == ' ' || it == ' ' }
        val (whole, fraction) = split(cleaned, decimalPlaces) ?: return null
        if (fraction.length > decimalPlaces) return null
        val digits = whole + fraction.padEnd(decimalPlaces, '0')
        return digits.trimStart('0').ifEmpty { "0" }.toLongOrNull()
    }

    /** Splits a cleaned token into whole digits and fraction digits, or null when its separators are inconsistent. */
    private fun split(token: String, decimalPlaces: Int): Pair<String, String>? {
        val lastDot = token.lastIndexOf('.')
        val lastComma = token.lastIndexOf(',')
        if (lastDot < 0 && lastComma < 0) return token to ""
        val decimalIndex = when {
            lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)
            else -> {
                val separator = if (lastDot >= 0) '.' else ','
                val index = token.lastIndexOf(separator)
                val occurrences = token.count { it == separator }
                val digitsAfter = token.length - index - 1
                when {
                    occurrences > 1 -> -1
                    token.startsWith(separator) -> index
                    digitsAfter == 3 && decimalPlaces != 3 -> -1
                    else -> index
                }
            }
        }
        val wholePart = if (decimalIndex < 0) token else token.substring(0, decimalIndex)
        val fractionPart = if (decimalIndex < 0) "" else token.substring(decimalIndex + 1)
        if (fractionPart.any { !it.isDigit() }) return null
        val whole = groupedDigits(wholePart) ?: return null
        return whole to fractionPart
    }

    /** Digits of a whole part that may use `.` or `,` as one consistent thousands mark, or null when malformed. */
    private fun groupedDigits(part: String): String? {
        if (part.isEmpty()) return ""
        if (part.all { it.isDigit() }) return part
        val marks = part.filterNot { it.isDigit() }
        if (marks.toSet().size != 1) return null
        val groups = part.split(marks.first())
        val first = groups.first()
        if (first.isEmpty() || first.length > 3 || groups.drop(1).any { it.length != 3 }) return null
        return groups.joinToString("")
    }
}
