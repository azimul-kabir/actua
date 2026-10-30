package com.azimulkabir.actua.data.export

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportCsvTest {
    private fun csv(headers: List<String>, rows: List<List<String>>): String =
        StringBuilder().also { ReportCsv.write(headers, rows, it) }.toString()

    @Test
    fun writesHeaderThenRowsWithCrlfAndReturnsTheRowCount() {
        val out = StringBuilder()
        val count = ReportCsv.write(
            listOf("Period", "Income", "Expense", "Net"),
            listOf(listOf("2026-05", "500.00", "200.00", "300.00"), listOf("2026-06", "0.00", "10.05", "-10.05")),
            out,
        )
        assertEquals(2, count)
        assertEquals(
            "Period,Income,Expense,Net\r\n2026-05,500.00,200.00,300.00\r\n2026-06,0.00,10.05,-10.05\r\n",
            out.toString(),
        )
    }

    @Test
    fun amountsKeepTheirSignAndAreNeverFormulaGuarded() {
        assertEquals("Name,Amount\r\nRent,-1250.00\r\n", csv(listOf("Name", "Amount"), listOf(listOf("Rent", "-1250.00"))))
    }

    @Test
    fun textCellsThatLookLikeFormulasGetALeadingApostrophe() {
        val text = csv(listOf("Name"), listOf(listOf("=SUM(A1)"), listOf("+1 deal"), listOf("-note"), listOf("@home")))
        assertEquals("Name\r\n'=SUM(A1)\r\n'+1 deal\r\n'-note\r\n'@home\r\n", text)
    }

    @Test
    fun quotesCommasQuotesAndNewlinesPerRfc4180() {
        val text = csv(listOf("Name", "Amount"), listOf(listOf("Dining, \"out\"\nlate", "5.00")))
        assertEquals("Name,Amount\r\n\"Dining, \"\"out\"\"\nlate\",5.00\r\n", text)
    }

    @Test
    fun anEmptyTableStillWritesTheHeader() {
        assertEquals("Name,Amount\r\n", csv(listOf("Name", "Amount"), emptyList()))
    }
}
