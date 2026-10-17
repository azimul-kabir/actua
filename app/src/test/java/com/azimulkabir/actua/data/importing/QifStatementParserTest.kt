package com.azimulkabir.actua.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QifStatementParserTest {

    @Test
    fun `parses standard QIF bank statement with caret records`() {
        val qif = """
            !Type:Bank
            D03/15/2024
            T-42.50
            PGrocery Store
            MGroceries for the week
            N1042
            ^
            D03/16/2024
            T250.00
            PDirect Deposit
            ^
        """.trimIndent()

        val result = QifStatementParser.parse(qif.toByteArray())
        assertEquals(0, result.problems.size)
        assertEquals(2, result.candidates.size)

        val first = result.candidates[0]
        assertEquals(20240315, first.date)
        assertEquals(-4250L, first.amountCents)
        assertEquals("Grocery Store", first.payee)
        assertEquals("Groceries for the week", first.notes)
        assertEquals("1042", first.reference)

        val second = result.candidates[1]
        assertEquals(20240316, second.date)
        assertEquals(25000L, second.amountCents)
        assertEquals("Direct Deposit", second.payee)
        assertEquals("", second.notes)
    }

    @Test
    fun `handles multiple date formats including Quicken apostrophe format`() {
        assertEquals(20240315, QifStatementParser.parseQifDate("03/15/2024"))
        assertEquals(20240315, QifStatementParser.parseQifDate("3/15/24"))
        assertEquals(20240315, QifStatementParser.parseQifDate("3/15'24"))
        assertEquals(20240315, QifStatementParser.parseQifDate("03/15'2024"))
        assertEquals(20240315, QifStatementParser.parseQifDate("2024-03-15"))
        assertEquals(20240325, QifStatementParser.parseQifDate("25/03/2024"))
    }

    @Test
    fun `handles negative amounts with parentheses and comma formatting`() {
        assertEquals(-125000L, QifStatementParser.parseQifAmount("(1,250.00)"))
        assertEquals(-3050L, QifStatementParser.parseQifAmount("-30.50"))
        assertEquals(10025L, QifStatementParser.parseQifAmount("100.25"))
    }

    @Test
    fun `falls back to memo when payee is empty`() {
        val qif = """
            !Type:Cash
            D2024-04-01
            T-5.00
            MCoffee shop
            ^
        """.trimIndent()

        val result = QifStatementParser.parse(qif.toByteArray())
        assertEquals(1, result.candidates.size)
        assertEquals("Coffee shop", result.candidates.single().payee)
    }

    @Test
    fun `rejects invalid non-QIF file`() {
        val notQif = "This is a random text file with no header"
        val result = QifStatementParser.parse(notQif.toByteArray())
        assertEquals(0, result.candidates.size)
        assertTrue(result.problems.isNotEmpty())
    }
}
