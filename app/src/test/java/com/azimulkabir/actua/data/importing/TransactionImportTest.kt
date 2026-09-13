package com.azimulkabir.actua.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionImportTest {
    @Test fun `parses quoted amount and common headers`() {
        val result = CsvTransactionCandidateSource.parse(
            "Date,Description,Amount,Reference\n2026-09-01,Salary,2500.00,abc\n09/02/2026,\"Shop, Inc\",\"(12.34)\",def"
        )
        assertEquals(emptyList<ImportProblem>(), result.problems)
        assertEquals(250000, result.candidates[0].amountCents)
        assertEquals(-1234, result.candidates[1].amountCents)
        assertEquals("Shop, Inc", result.candidates[1].payee)
    }

    @Test fun `combines debit and credit columns`() {
        val result = CsvTransactionCandidateSource.parse(
            "Posting Date,Narration,Debit,Credit\n2026-09-01,Coffee,4.25,\n2026-09-02,Refund,,10"
        )
        assertEquals(listOf(-425L, 1000L), result.candidates.map { it.amountCents })
    }

    @Test fun `malformed rows become problems and never candidates`() {
        val result = CsvTransactionCandidateSource.parse(
            "Date,Description,Amount\nnot-a-date,Coffee,-4.25\n2026-09-02,Valid,-5.00"
        )
        assertEquals(1, result.candidates.size)
        assertEquals(2, result.problems.single().sourceRow)
    }

    @Test fun `requires recognizable columns`() {
        val result = CsvTransactionCandidateSource.parse("When,What,Value\n2026-01-01,A,2")
        assertTrue(result.candidates.isEmpty())
        assertTrue(result.problems.single().message.contains("Missing required columns"))
    }

    @Test fun `duplicate keys normalize payee whitespace and case`() {
        assertEquals(
            ImportDuplicateDetector.key(20260901, -425, "Coffee Shop"),
            ImportDuplicateDetector.key(20260901, -425, " coffee   SHOP "),
        )
    }

    @Test fun `duplicate keys remain account-date-amount-payee stable`() {
        val parsed = CsvTransactionCandidateSource.parse(
            "Date,Description,Amount\n2026-09-01,Coffee,-4.25\n2026-09-01, coffee ,-4.25"
        ).candidates
        assertEquals(
            ImportDuplicateDetector.key(parsed[0].date, parsed[0].amountCents, parsed[0].payee),
            ImportDuplicateDetector.key(parsed[1].date, parsed[1].amountCents, parsed[1].payee),
        )
    }

    @Test fun `supports semicolon statements with configurable mapping and date format`() {
        val table = CsvTransactionCandidateSource.inspect(
            "Booked;Details;Out;Ref\n13-09-2026;Groceries;12.50;bank-1"
        )
        val mapping = ImportColumnMapping(
            listOf(ImportColumnRole.DATE, ImportColumnRole.PAYEE, ImportColumnRole.AMOUNT,
                ImportColumnRole.REFERENCE),
            datePattern = "dd-MM-yyyy",
            expensesArePositive = true,
        )
        val result = CsvTransactionCandidateSource.parse(table, mapping)
        assertEquals(emptyList<ImportProblem>(), result.problems)
        assertEquals(20260913, result.candidates.single().date)
        assertEquals(-1250L, result.candidates.single().amountCents)
        assertEquals("bank-1", result.candidates.single().reference)
    }

    @Test fun `parses tab separated rows and excel serial dates`() {
        val table = CsvTransactionCandidateSource.inspect("Date\tMerchant\tAmount\n46322\tCafe\t-3.25")
        val result = CsvTransactionCandidateSource.parse(table)
        assertEquals(emptyList<ImportProblem>(), result.problems)
        assertEquals(20261027, result.candidates.single().date)
        assertEquals(-325L, result.candidates.single().amountCents)
    }

    @Test fun `positive expense setting does not invert separate debit credit columns`() {
        val table = CsvTransactionCandidateSource.inspect("Date,Payee,Debit,Credit\n2026-09-13,Cafe,5,")
        val suggested = CsvTransactionCandidateSource.suggestedMapping(table.headers)
        val result = CsvTransactionCandidateSource.parse(table, suggested.copy(expensesArePositive = true))
        assertEquals(-500L, result.candidates.single().amountCents)
    }
}
