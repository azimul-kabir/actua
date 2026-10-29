package com.azimulkabir.actua.data.export

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionCsvTest {
    private fun csv(vararg rows: CsvTransaction): String =
        StringBuilder().also { TransactionCsv.write(rows.toList(), it) }.toString()

    private fun lines(text: String) = text.split("\r\n").dropLast(1)

    private fun txn(
        payee: String = "Corner Shop",
        notes: String = "",
        category: String = "Food",
        group: String = "Usual Expenses",
        cents: Long = -1250,
        cleared: Boolean = false,
        reconciled: Boolean = false,
        splits: List<CsvSplitLine> = emptyList(),
    ) = CsvTransaction("Checking", 20260315, payee, notes, group, category, cents, cleared, reconciled, splits)

    @Test
    fun headerMatchesActualsAccountExport() {
        assertEquals(
            "Account,Date,Payee,Notes,Category_Group,Category,Amount,Split_Amount,Cleared",
            lines(csv()).single(),
        )
    }

    @Test
    fun plainTransactionIsOneRowWithIsoDateAndExactAmount() {
        val rows = lines(csv(txn()))
        assertEquals("Checking,2026-03-15,Corner Shop,,Usual Expenses,Food,-12.50,0.00,Not cleared", rows[1])
    }

    @Test
    fun amountsAreExactForEveryCentValue() {
        assertEquals("0.00", TransactionCsv.amount(0))
        assertEquals("0.05", TransactionCsv.amount(5))
        assertEquals("-0.05", TransactionCsv.amount(-5))
        assertEquals("1234567890.12", TransactionCsv.amount(123_456_789_012))
        assertEquals("-92233720368547758.08", TransactionCsv.amount(Long.MIN_VALUE))
        // 0.1 + 0.2 style drift cannot happen: nothing goes through floating point.
        assertEquals("0.30", TransactionCsv.amount(10 + 20))
    }

    @Test
    fun statusReflectsReconciledBeforeCleared() {
        assertEquals("Reconciled", lines(csv(txn(cleared = true, reconciled = true)))[1].substringAfterLast(','))
        assertEquals("Cleared", lines(csv(txn(cleared = true)))[1].substringAfterLast(','))
        assertEquals("Not cleared", lines(csv(txn()))[1].substringAfterLast(','))
    }

    @Test
    fun splitParentMovesItsAmountToSplitAmountAndIsFollowedByItsLines() {
        val rows = lines(
            csv(
                txn(
                    payee = "Market", notes = "weekly", category = "", group = "", cents = -3000, cleared = true,
                    splits = listOf(
                        CsvSplitLine("Market", "veg", "Usual Expenses", "Food", -2000),
                        CsvSplitLine("Market", "", "Usual Expenses", "Household", -1000),
                    ),
                ),
            ),
        )
        assertEquals(4, rows.size)
        assertEquals("Checking,2026-03-15,Market,(SPLIT INTO 2) weekly,,,0.00,-30.00,Cleared", rows[1])
        assertEquals("Checking,2026-03-15,Market,(SPLIT 1 OF 2) veg,Usual Expenses,Food,-20.00,0.00,Cleared", rows[2])
        assertEquals("Checking,2026-03-15,Market,(SPLIT 2 OF 2) ,Usual Expenses,Household,-10.00,0.00,Cleared", rows[3])
    }

    @Test
    fun splitRowsSumToTheTransactionAmount() {
        val cents = listOf(-2000L, -1000L)
        val rows = lines(
            csv(txn(cents = -3000, splits = cents.map { CsvSplitLine("", "", "", "", it) })),
        ).drop(2)
        val sum = rows.sumOf { it.split(',')[6].replace(".", "").toLong() }
        assertEquals(-3000, sum)
    }

    @Test
    fun cellsWithCommasQuotesAndNewlinesAreQuoted() {
        val row = csv(txn(payee = "Smith, J \"Jo\"", notes = "line1\nline2")).split("\r\n")[1]
        assertEquals(
            "Checking,2026-03-15,\"Smith, J \"\"Jo\"\"\",\"line1\nline2\",Usual Expenses,Food,-12.50,0.00,Not cleared",
            row,
        )
    }

    @Test
    fun textThatSpreadsheetsReadAsFormulasIsDefused() {
        val row = lines(csv(txn(payee = "=HYPERLINK(\"x\")", notes = "-5 refund", category = "@home")))[1]
        assertEquals(
            "Checking,2026-03-15,\"'=HYPERLINK(\"\"x\"\")\",'-5 refund,Usual Expenses,'@home,-12.50,0.00,Not cleared",
            row,
        )
    }

    @Test
    fun negativeAmountsKeepTheirSignDespiteTheFormulaGuard() {
        assertEquals("-12.50", lines(csv(txn()))[1].split(',')[6])
    }

    @Test
    fun returnsTheNumberOfTransactionsWithoutCountingSplitLines() {
        val out = StringBuilder()
        val written = TransactionCsv.write(
            listOf(txn(), txn(splits = listOf(CsvSplitLine("", "", "", "", -1), CsvSplitLine("", "", "", "", -2)))),
            out,
        )
        assertEquals(2, written)
    }

    @Test
    fun databaseRowsMapNamesGroupsAndSplitPortions() {
        val row = ActualTransaction(
            id = "t1", accountId = "a1", date = 20260102, amountCents = -500, payeeId = "p1", payeeName = "Cafe",
            categoryId = "c1", categoryName = "Coffee", notes = "n", cleared = true, reconciled = false,
            transferId = null, isParent = false, parentId = null, tombstone = false, sortOrder = null,
            importedPayee = null, scheduleId = null, transferAccountId = null,
        )
        val mapped = row.toCsvTransaction(mapOf("a1" to "Checking"), mapOf("c1" to "Fun"))

        assertEquals("Checking", mapped.account)
        assertEquals("Fun", mapped.categoryGroup)
        assertEquals("Coffee", mapped.category)
        assertEquals(-500L, mapped.amountCents)

        val parent = row.copy(
            isParent = true, categoryId = null, categoryName = null,
            splitPortions = listOf(
                ActualTransaction.SplitPortion("s1", "Coffee", -300, "x", "Cafe", categoryId = "c1"),
                ActualTransaction.SplitPortion("s2", null, -200, null, null),
            ),
        ).toCsvTransaction(mapOf("a1" to "Checking"), mapOf("c1" to "Fun"))
        assertEquals("", parent.category)
        assertEquals(listOf("Fun", ""), parent.splits.map { it.categoryGroup })
        assertEquals(listOf(-300L, -200L), parent.splits.map { it.amountCents })
    }
}
