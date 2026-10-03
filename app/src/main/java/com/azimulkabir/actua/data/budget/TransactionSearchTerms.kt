package com.azimulkabir.actua.data.budget

import java.time.LocalDate

/**
 * The amount and date readings of a transaction search string, after Actual's `transactionsSearch`
 * (`desktop-client/src/queries/index.ts`): a search that parses as a number matches rows whose absolute
 * amount equals it, and one that parses as a date in the budget's `dateFormat` matches that day.
 * Amounts stay integer cents; nothing here goes through floating point.
 */
data class TransactionSearchTerms(
    /** Inclusive absolute-amount range in cents, or null when the search is not a number. */
    val amountRangeCents: LongRange?,
    /** YYYYMMDD day integers the search reads as, or empty when it is not a date. */
    val dates: List<Int>,
) {
    companion object {
        const val DEFAULT_DATE_FORMAT = "MM/dd/yyyy"
        private val numberPattern = Regex("""\d+(?:[.,]\d{1,2})?""")

        fun parse(search: String, dateFormat: String?, today: LocalDate): TransactionSearchTerms {
            val text = search.trim()
            return TransactionSearchTerms(parseAmountRange(text), parseDates(text, dateFormat ?: DEFAULT_DATE_FORMAT, today))
        }

        /** `42.50` is exactly 4250 cents; a whole number such as `42` spans the whole unit, 4200..4299. */
        internal fun parseAmountRange(text: String): LongRange? {
            val unsigned = text.removePrefix("-").removePrefix("+").replace(" ", "")
            val normalized = normalizeSeparators(unsigned)
            if (!numberPattern.matches(normalized)) return null
            val separator = normalized.indexOfFirst { it == '.' || it == ',' }
            if (separator < 0) {
                val whole = normalized.toLongOrNull()?.times(100) ?: return null
                return whole..whole + 99
            }
            val fraction = normalized.substring(separator + 1).padEnd(2, '0')
            val cents = (normalized.substring(0, separator).toLongOrNull() ?: return null) * 100 + fraction.toLong()
            return cents..cents
        }

        private fun normalizeSeparators(text: String): String {
            val lastDot = text.lastIndexOf('.')
            val lastComma = text.lastIndexOf(',')
            return when {
                lastDot >= 0 && lastComma >= 0 ->
                    // Both present: the later one is the decimal mark, the other groups thousands.
                    if (lastDot > lastComma) text.replace(",", "") else text.replace(".", "").replace(',', '.')
                else -> text
            }
        }

        /** Reads [text] as the full format, its short-year form, and its day-month form (current year). */
        internal fun parseDates(text: String, dateFormat: String, today: LocalDate): List<Int> {
            val separator = dateFormat.firstOrNull { !it.isLetter() } ?: return emptyList()
            val order = dateFormat.split(separator).mapNotNull { part ->
                when (part.firstOrNull()?.lowercaseChar()) {
                    'd' -> 'd'
                    'm' -> 'm'
                    'y' -> 'y'
                    else -> null
                }
            }
            if (order.size != 3 || order.toSet().size != 3) return emptyList()
            val parts = text.split(separator)
            if (parts.any { it.isEmpty() || !it.all(Char::isDigit) || it.length > 4 }) return emptyList()
            val values = mutableMapOf<Char, Int>()
            when (parts.size) {
                3 -> order.forEachIndexed { index, token -> values[token] = parts[index].toInt() }
                2 -> {
                    val dayMonth = order.filter { it != 'y' }
                    dayMonth.forEachIndexed { index, token -> values[token] = parts[index].toInt() }
                    values['y'] = today.year
                }
                else -> return emptyList()
            }
            val yearText = if (parts.size == 3) parts[order.indexOf('y')] else null
            val year = when {
                yearText == null -> values.getValue('y')
                yearText.length == 4 -> values.getValue('y')
                yearText.length == 2 -> 2000 + values.getValue('y')
                else -> return emptyList()
            }
            val date = runCatching { LocalDate.of(year, values.getValue('m'), values.getValue('d')) }.getOrNull()
                ?: return emptyList()
            return listOf(date.year * 10000 + date.monthValue * 100 + date.dayOfMonth)
        }
    }
}
