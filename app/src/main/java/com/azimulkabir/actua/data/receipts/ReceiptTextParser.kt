package com.azimulkabir.actua.data.receipts

import com.azimulkabir.actua.ui.components.PastedAmount
import java.time.LocalDate
import java.util.Locale

/** One line item read from a receipt: what it says and its price in minor units. */
data class ReceiptItem(val name: String, val cents: Long)

/**
 * What could be read from a receipt. Each field is null (or empty) when it was not found or was unclear.
 * [taxCents] is the sales tax or VAT amount; [items] are the priced lines above the subtotal, the least
 * reliable part of a scan.
 */
data class ParsedReceipt(
    val payee: String?,
    val date: LocalDate?,
    val totalCents: Long?,
    val taxCents: Long? = null,
    val items: List<ReceiptItem> = emptyList(),
) {
    val isEmpty: Boolean get() = payee == null && date == null && totalCents == null && taxCents == null && items.isEmpty()
}

/**
 * Finds the payee, date and total in the text of a receipt. The result is only a suggestion: a field that is
 * unclear is left null instead of guessed. Digits of any script are read (Latin and Bengali among them), the
 * total is an integer number of minor units with no floating point, and no receipt text is kept or logged.
 */
object ReceiptTextParser {
    fun parse(
        rows: List<String>,
        decimalPlaces: Int,
        today: LocalDate,
        knownPayees: Collection<String> = emptyList(),
        /** Whether a date like 04/05/2026 is day first; null when unknown, which leaves such dates unread. */
        dayFirst: Boolean? = null,
    ): ParsedReceipt {
        val clean = rows.map { normalizeDigits(it).trim() }.filter { it.isNotEmpty() }
        val total = findTotal(clean, decimalPlaces)
        return ParsedReceipt(
            payee = findPayee(clean, knownPayees),
            date = findDate(clean, today, dayFirst),
            totalCents = total,
            taxCents = findTax(clean, decimalPlaces, total),
            items = findItems(clean, decimalPlaces),
        )
    }

    /** Every Unicode decimal digit becomes 0-9, so Bengali "১২.৫০" reads as "12.50". */
    internal fun normalizeDigits(text: String): String = buildString(text.length) {
        for (ch in text) append(if (Character.isDigit(ch)) Character.forDigit(Character.digit(ch, 10), 10) else ch)
    }

    // --- Total ---------------------------------------------------------------------------------------------

    private val strongTotal = listOf("grand total", "amount due", "balance due", "total due", "total amount")
    private val totalWords = strongTotal + listOf("total", "gesamt", "summe", "importe", "montant", "মোট")
    private val notATotal = Regex("""sub[\s-]?total|zwischensumme|total\s+(tax|vat|savings?|saved|discount|items?|qty|quantity|points)|(tax|vat)\s+total""")
    private val amountToken = Regex("""[.,]\d+|\d(?:[\d.,']*\d)?""")

    private fun findTotal(rows: List<String>, decimalPlaces: Int): Long? {
        var strong: Long? = null
        var plain: Long? = null
        for ((index, row) in rows.withIndex()) {
            val lower = row.lowercase(Locale.ROOT)
            if (totalWords.none { it in lower } || notATotal.containsMatchIn(lower)) continue
            val amount = amountsIn(row, decimalPlaces).lastOrNull()
                // The label and its amount can be read as separate rows.
                ?: rows.getOrNull(index + 1)?.takeIf(::isOnlyAmount)?.let { amountsIn(it, decimalPlaces).lastOrNull() }
                ?: continue
            if (amount <= 0) continue
            if (strongTotal.any { it in lower }) strong = amount else plain = amount
        }
        return strong ?: plain
    }

    private fun isOnlyAmount(row: String): Boolean =
        row.replace(Regex("""[^\p{L}\p{N}.,']"""), "").let { it.isNotEmpty() && it.none(Char::isLetter) }

    /** Amounts in [row]; without decimal places in the text only a currency with none accepts a bare integer. */
    private fun amountsIn(row: String, decimalPlaces: Int): List<Long> =
        amountToken.findAll(row).map { it.value }.mapNotNull { token ->
            if (decimalPlaces > 0 && token.none { it == '.' || it == ',' }) null
            else PastedAmount.parseCents(token, decimalPlaces)
        }.toList()

    // --- Sales tax and items ---------------------------------------------------------------------------------

    private val taxWord = Regex("""\b(tax|vat|gst|hst|pst|mwst|iva)\b""")
    private val notTaxRow = Regex("""tax\s+(invoice|id|no|number|reg)""")
    private val totalTaxRow = Regex("""total\s+(tax|vat)|(tax|vat)\s+total""")
    private val percent = Regex("""\d+(?:[.,]\d+)?\s*%""")

    /**
     * The tax on the receipt: a "total tax" row when there is one, otherwise the tax rows added up (some
     * receipts list one per rate). A percentage such as "8.5%" is never read as an amount. Null when no tax
     * row has an amount, or the amount is not smaller than the total.
     */
    private fun findTax(rows: List<String>, decimalPlaces: Int, totalCents: Long?): Long? {
        var overall: Long? = null
        var sum = 0L
        var any = false
        for (row in rows) {
            val lower = row.lowercase(Locale.ROOT)
            if (!taxWord.containsMatchIn(lower) || notTaxRow.containsMatchIn(lower)) continue
            val isTotalTax = totalTaxRow.containsMatchIn(lower)
            // "Subtotal incl. tax" and "total due" rows are not tax rows.
            if (!isTotalTax && "total" in lower) continue
            val amount = amountsIn(percent.replace(row, " "), decimalPlaces).lastOrNull() ?: continue
            if (isTotalTax) overall = amount else { sum += amount; any = true }
        }
        val tax = overall ?: if (any) sum else null
        return tax?.takeIf { it > 0 && (totalCents == null || it < totalCents) }
    }

    private val itemRow = Regex("""^(.+?)\s+\p{Sc}?\s*(\d[\d.,']*[.,]\d{1,3})\s*[A-Za-z]{0,2}$""")
    private val notAnItem = Regex(
        """total|tax|vat|gst|cash|change|card|visa|master|tender|balance|discount|tip|rounding|due|paid|amex|debit|credit|payment|saving|tel|phone|www|http|@""",
    )

    /**
     * Priced lines above the first subtotal or total row, as "name  amount". Rows that look like totals,
     * tax, payment or contact details are skipped. Without a subtotal or total row nothing is returned,
     * because the receipt's structure is then too unclear to trust.
     */
    private fun findItems(rows: List<String>, decimalPlaces: Int): List<ReceiptItem> {
        val end = rows.indexOfFirst { row ->
            val lower = row.lowercase(Locale.ROOT)
            totalWords.any { it in lower } || "subtotal" in lower || Regex("""sub[\s-]?total""").containsMatchIn(lower)
        }
        if (end <= 0) return emptyList()
        return rows.take(end).mapNotNull { row ->
            val match = itemRow.find(row) ?: return@mapNotNull null
            val name = match.groupValues[1].trim()
            if (name.count(Char::isLetter) < 2 || notAnItem.containsMatchIn(name.lowercase(Locale.ROOT))) return@mapNotNull null
            val cents = PastedAmount.parseCents(match.groupValues[2], decimalPlaces) ?: return@mapNotNull null
            if (cents <= 0) null else ReceiptItem(name.replace(Regex("""\s+"""), " ").take(60), cents)
        }
    }

    // --- Date ----------------------------------------------------------------------------------------------

    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val monthNames = months.joinToString("|")
    private val isoDate = Regex("""\b(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})\b""")
    private val numericDate = Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})\b""")
    private val dayMonthName = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?[\s-]+($monthNames)[a-z]*\.?,?[\s-]+(\d{4})\b""", RegexOption.IGNORE_CASE)
    private val monthNameDay = Regex("""\b($monthNames)[a-z]*\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})\b""", RegexOption.IGNORE_CASE)

    private fun findDate(rows: List<String>, today: LocalDate, dayFirst: Boolean?): LocalDate? {
        for (row in rows) {
            val found = sequenceOf(
                isoDate.find(row)?.let { m -> date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) },
                numericDate.find(row)?.let { m -> numeric(m.groupValues[1].toInt(), m.groupValues[2].toInt(), year(m.groupValues[3]), dayFirst) },
                dayMonthName.find(row)?.let { m -> date(m.groupValues[3].toInt(), monthIndex(m.groupValues[2]), m.groupValues[1].toInt()) },
                monthNameDay.find(row)?.let { m -> date(m.groupValues[3].toInt(), monthIndex(m.groupValues[1]), m.groupValues[2].toInt()) },
            ).filterNotNull().firstOrNull { it <= today && it.year >= 2000 }
            if (found != null) return found
        }
        return null
    }

    private fun year(text: String): Int = text.toInt().let { if (text.length == 2) 2000 + it else it }

    private fun monthIndex(name: String): Int = months.indexOf(name.lowercase(Locale.ROOT).take(3)) + 1

    private fun date(year: Int, month: Int, day: Int): LocalDate? = runCatching { LocalDate.of(year, month, day) }.getOrNull()

    /** 04/05/2026 is read only when it is unambiguous: one part is above 12, the parts match, or [dayFirst] is known. */
    private fun numeric(a: Int, b: Int, year: Int, dayFirst: Boolean?): LocalDate? = when {
        a > 12 && b <= 12 -> date(year, b, a)
        b > 12 && a <= 12 -> date(year, a, b)
        a == b -> date(year, a, b)
        a <= 12 && b <= 12 -> dayFirst?.let { if (it) date(year, b, a) else date(year, a, b) }
        else -> null
    }

    // --- Payee ---------------------------------------------------------------------------------------------

    private val notAPayee = listOf("receipt", "invoice", "tel", "phone", "www.", "http", "@", "date", "order", "table", "cashier", "thank")

    private fun findPayee(rows: List<String>, knownPayees: Collection<String>): String? {
        val head = rows.take(10)
        // An existing payee named on the receipt comes first; the longest name wins ("Joe's Cafe" over "Joe").
        val known = knownPayees.asSequence()
            .map { it to compact(it) }
            .filter { (_, compactName) -> compactName.length >= 3 }
            .filter { (_, compactName) -> head.any { compactName in compact(it) } }
            .maxByOrNull { (_, compactName) -> compactName.length }
        if (known != null) return known.first
        return rows.take(6).firstOrNull { row ->
            val lower = row.lowercase(Locale.ROOT)
            val letters = row.count(Char::isLetter)
            letters >= 3 && letters >= row.count(Char::isDigit) && notAPayee.none { it in lower }
        }?.replace(Regex("""\s+"""), " ")?.take(60)
    }

    /** Letters and digits only, lower case, so "Joe's  CAFE" and "joes cafe" compare equal. */
    private fun compact(text: String): String =
        text.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}
