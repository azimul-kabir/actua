package com.azimulkabir.actua.data.export

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import java.math.BigDecimal
import java.util.Locale

/** One category line of a split transaction, as exported. */
data class CsvSplitLine(
    val payee: String,
    val notes: String,
    val categoryGroup: String,
    val category: String,
    val amountCents: Long,
)

/**
 * A live transaction ready for export. [splits] is non-empty exactly when the transaction is a
 * split parent; the parent's own amount is then exported in `Split_Amount`, as Actual does.
 */
data class CsvTransaction(
    val account: String,
    /** Actual's YYYYMMDD day integer. */
    val date: Int,
    val payee: String,
    val notes: String,
    val categoryGroup: String,
    val category: String,
    val amountCents: Long,
    val cleared: Boolean,
    val reconciled: Boolean,
    val splits: List<CsvSplitLine> = emptyList(),
)

/**
 * Writes transactions in the column layout of Actual's account "Export" (`exportQueryToCSV` in
 * loot-core `server/transactions/export/export-to-csv.ts`): one row per transaction, a split
 * parent followed by one row per split line, with `Amount` and `Split_Amount` kept apart so the
 * file still sums to the account balance.
 *
 * Amounts are integer cents rendered as exact two-decimal numbers (negative is money out), never
 * through floating point. Text is quoted per RFC 4180, and text cells that a spreadsheet would
 * read as a formula get a leading apostrophe, as in Actual.
 */
object TransactionCsv {
    val HEADER = listOf(
        "Account", "Date", "Payee", "Notes", "Category_Group", "Category", "Amount", "Split_Amount", "Cleared",
    )

    internal const val LINE_END = "\r\n"
    private val FORMULA_TRIGGERS = charArrayOf('=', '+', '-', '@', '\t', '\r')

    /** Writes the header and every row to [out]; returns the number of transactions written. */
    fun write(transactions: Iterable<CsvTransaction>, out: Appendable): Int {
        writeLine(HEADER, out, guardFormulas = false)
        var count = 0
        for (transaction in transactions) {
            count++
            val status = status(transaction)
            if (transaction.splits.isEmpty()) {
                writeLine(
                    listOf(
                        transaction.account, isoDate(transaction.date), transaction.payee, transaction.notes,
                        transaction.categoryGroup, transaction.category,
                        amount(transaction.amountCents), amount(0), status,
                    ),
                    out,
                )
                continue
            }
            val total = transaction.splits.size
            writeLine(
                listOf(
                    transaction.account, isoDate(transaction.date), transaction.payee,
                    "(SPLIT INTO $total) ${transaction.notes}", "", "",
                    amount(0), amount(transaction.amountCents), status,
                ),
                out,
            )
            transaction.splits.forEachIndexed { index, line ->
                writeLine(
                    listOf(
                        transaction.account, isoDate(transaction.date), line.payee,
                        "(SPLIT ${index + 1} OF $total) ${line.notes}", line.categoryGroup, line.category,
                        amount(line.amountCents), amount(0), status,
                    ),
                    out,
                )
            }
        }
        return count
    }

    fun amount(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

    fun isoDate(day: Int): String =
        String.format(Locale.ROOT, "%04d-%02d-%02d", day / 10000, day / 100 % 100, day % 100)

    private fun status(transaction: CsvTransaction) = when {
        transaction.reconciled -> "Reconciled"
        transaction.cleared -> "Cleared"
        else -> "Not cleared"
    }

    private val numericColumns = setOf(6, 7)

    private fun writeLine(cells: List<String>, out: Appendable, guardFormulas: Boolean = true) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) out.append(',')
            // Amount columns are numbers ("-12.50"), not text, so they must keep their sign.
            val text = if (guardFormulas && index !in numericColumns) guardFormula(cell) else cell
            out.append(quote(text))
        }
        out.append(LINE_END)
    }

    internal fun guardFormula(value: String) =
        if (value.isNotEmpty() && value[0] in FORMULA_TRIGGERS) "'$value" else value

    internal fun quote(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
}

/**
 * Maps a live database row (with its [ActualTransaction.splitPortions]) to an export row. Names
 * come from the caller so duplicate account or category names stay distinguishable by group.
 */
fun ActualTransaction.toCsvTransaction(
    accountNames: Map<String, String>,
    categoryGroupNames: Map<String, String>,
): CsvTransaction = CsvTransaction(
    account = accountNames[accountId].orEmpty(),
    date = date,
    payee = payeeName.orEmpty(),
    notes = notes.orEmpty(),
    categoryGroup = categoryId?.let(categoryGroupNames::get).orEmpty(),
    category = categoryName.orEmpty(),
    amountCents = amountCents,
    cleared = cleared,
    reconciled = reconciled,
    splits = splitPortions.map { part ->
        CsvSplitLine(
            payee = part.payeeName.orEmpty(),
            notes = part.notes.orEmpty(),
            categoryGroup = part.categoryId?.let(categoryGroupNames::get).orEmpty(),
            category = part.categoryName.orEmpty(),
            amountCents = part.amountCents,
        )
    },
)
