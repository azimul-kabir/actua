package com.azimulkabir.actua.data.importing

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImportedPayeeNameTest {
    private fun candidate(payee: String, keepPayeeCase: Boolean = false) =
        ImportCandidate(1, 20261015, payee, "", -1_069L, keepPayeeCase = keepPayeeCase)

    @Test
    fun `file rows are title-cased as in Actual`() {
        assertEquals("Starbucks 1234", candidate("  STARBUCKS 1234 ").importedPayeeName())
    }

    @Test
    fun `captured rows keep the payee's case`() {
        assertEquals("STARBUCKS 1234", candidate("  STARBUCKS 1234 ", keepPayeeCase = true).importedPayeeName())
    }

    @Test
    fun `blank payees have no imported payee`() {
        assertNull(candidate("  ").importedPayeeName())
        assertNull(candidate("  ", keepPayeeCase = true).importedPayeeName())
    }

    @Test
    fun `message and Tasker rows keep the payee's case`() {
        val message = FinancialMessageParser.parse("Your card ****9876 was debited BDT 450.25 at COFFEE HOUSE on 13/09/2026. Trx ID ABC12345",
            "Bank", LocalDate.of(2026, 10, 15)).candidates.single()
        assertEquals("COFFEE HOUSE", message.importedPayeeName())
        val tasker = parseAutomationExtras(mapOf("amount" to "-10.69", "payee" to "STARBUCKS 1234"), LocalDate.of(2026, 10, 15))
            .candidates.single()
        assertEquals("STARBUCKS 1234", tasker.importedPayeeName())
    }

    @Test
    fun `statement CSV rows are still title-cased`() {
        val result = CsvTransactionCandidateSource.parse("Date,Payee,Amount\n2026-10-14,STARBUCKS 1234,-10.69")
        assertEquals("Starbucks 1234", result.candidates.single().importedPayeeName())
    }
}
