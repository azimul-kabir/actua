package com.azimulkabir.actua.data.budget

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Actual's `addMovementNotes` (loot-core `budget/actions.ts`): moving or covering money appends
 * `- Reassigned <amount> from <from> → <to> on <Month dd>` to the month's `budget-<YYYY-MM>` note.
 */
object BudgetMovementNote {
    const val TO_BUDGET = "To Budget"
    const val OVERBUDGETED = "Overbudgeted"

    fun noteId(month: String) = "budget-$month"

    fun decimalPlaces(currencyCode: String?): Int = CurrencyDecimals.of(currencyCode)

    /**
     * Upstream `integerToCurrencyWithDecimal(amount, currencyCode)` on the server: the integer amount
     * divided by 100 (every currency stores two implied decimals), formatted en-US with grouping and
     * the currency's own fraction digits, so none for JPY, KRW and IRR (the server never changes its
     * default number format).
     */
    fun amountText(amountCents: Long, currencyCode: String?): String {
        val value = BigDecimal.valueOf(amountCents).movePointLeft(2)
        val pattern = if (decimalPlaces(currencyCode) == 0) "#,##0" else "#,##0.00"
        return DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).apply { roundingMode = RoundingMode.HALF_UP }.format(value)
    }

    /** One note line, without the leading "- ". [today] is formatted `MMMM dd`, in English. */
    fun line(amountCents: Long, from: String, to: String, currencyCode: String?, today: LocalDate): String {
        val day = today.format(DateTimeFormatter.ofPattern("MMMM dd", Locale.ENGLISH))
        return "Reassigned ${amountText(amountCents, currencyCode)} from $from → $to on $day"
    }

    /** Upstream `${addNewLine(existing)}- ${line}`. */
    fun append(existing: String?, line: String): String =
        (if (existing.isNullOrEmpty()) "" else "$existing\n") + "- " + line
}
