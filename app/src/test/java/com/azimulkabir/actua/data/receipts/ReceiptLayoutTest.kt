package com.azimulkabir.actua.data.receipts

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptLayoutTest {
    private fun line(text: String, left: Int, top: Int, bottom: Int) = RecognizedLine(text, left, top, left + 100, bottom)

    @Test fun aLabelAndItsAmountOnTheSameRowAreJoinedLeftToRight() {
        val rows = ReceiptLayout.rows(
            listOf(line("12.50", 400, 100, 130), line("TOTAL", 10, 102, 128)),
        )

        assertEquals(listOf("TOTAL 12.50"), rows)
    }

    @Test fun rowsRunTopToBottomWhateverTheDetectionOrder() {
        val rows = ReceiptLayout.rows(
            listOf(line("TOTAL", 10, 300, 330), line("SHOP", 10, 10, 40), line("Milk", 10, 100, 130)),
        )

        assertEquals(listOf("SHOP", "Milk", "TOTAL"), rows)
    }

    @Test fun blankLinesAreDropped() {
        assertEquals(listOf("SHOP"), ReceiptLayout.rows(listOf(line("  ", 0, 0, 20), line("SHOP", 0, 100, 120))))
    }

    @Test fun noLinesGiveNoRows() {
        assertEquals(emptyList<String>(), ReceiptLayout.rows(emptyList()))
    }
}
