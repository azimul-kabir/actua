package com.azimulkabir.actua.data.receipts

/** Turns recognized lines into rows of text, the way the receipt reads. */
object ReceiptLayout {
    /**
     * Lines whose vertical centres fall on the same row are joined left to right, so "TOTAL" and its amount
     * recognized as two blocks become one row "TOTAL 12.50". Rows run top to bottom; blank lines are dropped.
     */
    fun rows(lines: List<RecognizedLine>): List<String> {
        val sorted = lines.filter { it.text.isNotBlank() }.sortedBy { (it.top + it.bottom) / 2 }
        val rows = mutableListOf<MutableList<RecognizedLine>>()
        for (line in sorted) {
            val centre = (line.top + line.bottom) / 2
            val current = rows.lastOrNull()
            if (current != null && centre <= current.maxOf { it.bottom } && centre >= current.minOf { it.top }) {
                current += line
            } else {
                rows += mutableListOf(line)
            }
        }
        return rows.map { row -> row.sortedBy { it.left }.joinToString(" ") { it.text.trim() } }
    }
}
