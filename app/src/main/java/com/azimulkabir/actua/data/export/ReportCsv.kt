package com.azimulkabir.actua.data.export

/**
 * Writes a custom report's data table as CSV: the header row, then the rows exactly as the table
 * shows them, so the file matches what the report displays. Cells are quoted per RFC 4180; a text
 * cell a spreadsheet would read as a formula gets a leading apostrophe (as in [TransactionCsv]), while
 * plain numbers such as `-12.50` keep their sign. Callers pass amounts already rendered as exact
 * two-decimal numbers (see [TransactionCsv.amount]).
 */
object ReportCsv {
    private val NUMBER = Regex("-?\\d+(\\.\\d+)?")

    /** Writes the header and every row to [out]; returns the number of data rows written. */
    fun write(headers: List<String>, rows: List<List<String>>, out: Appendable): Int {
        writeLine(headers, out, guardFormulas = false)
        rows.forEach { writeLine(it, out, guardFormulas = true) }
        return rows.size
    }

    private fun writeLine(cells: List<String>, out: Appendable, guardFormulas: Boolean) {
        cells.forEachIndexed { index, cell ->
            if (index > 0) out.append(',')
            val text = if (guardFormulas && !NUMBER.matches(cell)) TransactionCsv.guardFormula(cell) else cell
            out.append(TransactionCsv.quote(text))
        }
        out.append(TransactionCsv.LINE_END)
    }
}
