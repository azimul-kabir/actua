package com.azimulkabir.actua.data.receipts

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic receipt text only; no real receipts. */
class ReceiptTextParserTest {
    private val today = LocalDate.of(2026, 11, 4)

    private fun parse(
        vararg rows: String,
        decimalPlaces: Int = 2,
        payees: List<String> = emptyList(),
        dayFirst: Boolean? = null,
    ) = ReceiptTextParser.parse(rows.toList(), decimalPlaces, today, payees, dayFirst)

    @Test fun readsPayeeDateAndTotalFromATypicalReceipt() {
        val receipt = parse(
            "GREEN LEAF MARKET", "12 Example Street", "Tel 555 0100", "2026-11-02 14:31",
            "Milk 2.50", "Bread 3.00", "SUBTOTAL 5.50", "TAX 0.44", "TOTAL 5.94", "CASH 10.00",
        )

        assertEquals("GREEN LEAF MARKET", receipt.payee)
        assertEquals(LocalDate.of(2026, 11, 2), receipt.date)
        assertEquals(594L, receipt.totalCents)
    }

    @Test fun theSubtotalIsNotTheTotal() {
        assertEquals(594L, parse("SUBTOTAL 5.50", "TOTAL 5.94").totalCents)
        assertNull(parse("SUBTOTAL 5.50", "TAX 0.44").totalCents)
    }

    @Test fun totalTaxAndTotalItemsAreNotTheTotal() {
        assertEquals(1250L, parse("TOTAL 12.50", "TOTAL TAX 1.10", "TOTAL ITEMS 3").totalCents)
    }

    @Test fun anAmountDueBeatsAPlainTotalLine() {
        assertEquals(900L, parse("TOTAL 10.00", "AMOUNT DUE 9.00").totalCents)
    }

    @Test fun theAmountOnTheNextRowBelongsToALoneTotalLabel() {
        assertEquals(1250L, parse("TOTAL", "$12.50").totalCents)
    }

    @Test fun groupedThousandsAndEuropeanSeparatorsAreRead() {
        assertEquals(123_456L, parse("TOTAL 1,234.56").totalCents)
        assertEquals(123_456L, parse("Gesamt 1.234,56").totalCents)
    }

    @Test fun aBareCountOnTheTotalLineIsNotAnAmount() {
        assertEquals(1250L, parse("TOTAL 3 ITEMS 12.50").totalCents)
        assertNull(parse("TOTAL 3").totalCents)
    }

    @Test fun aCurrencyWithoutDecimalsTakesWholeNumbers() {
        assertEquals(1500L, parse("TOTAL 1500", decimalPlaces = 0).totalCents)
    }

    @Test fun anUnclearTotalIsLeftEmptyInsteadOfGuessed() {
        assertNull(parse("Milk 2.50", "Bread 3.00").totalCents)
    }

    @Test fun bengaliDigitsAreRead() {
        val receipt = parse("TOTAL ১২.৫০", "১৩/১০/২০২৬")

        assertEquals(1250L, receipt.totalCents)
        assertEquals(LocalDate.of(2026, 10, 13), receipt.date)
    }

    @Test fun datesInSeveralFormatsAreRead() {
        assertEquals(LocalDate.of(2026, 11, 2), parse("2026/11/02").date)
        assertEquals(LocalDate.of(2026, 10, 25), parse("25.10.2026").date)
        assertEquals(LocalDate.of(2026, 11, 2), parse("02 Nov 2026").date)
        assertEquals(LocalDate.of(2026, 11, 2), parse("November 2, 2026").date)
        assertEquals(LocalDate.of(2026, 11, 2), parse("2nd Nov 2026").date)
    }

    @Test fun anAmbiguousDateIsReadOnlyWhenTheOrderIsKnown() {
        assertNull(parse("04/05/2026").date)
        assertEquals(LocalDate.of(2026, 5, 4), parse("04/05/2026", dayFirst = true).date)
        assertEquals(LocalDate.of(2026, 4, 5), parse("04/05/2026", dayFirst = false).date)
    }

    @Test fun anUnambiguousNumericDateNeedsNoOrder() {
        assertEquals(LocalDate.of(2026, 10, 25), parse("25/10/2026").date)
        assertEquals(LocalDate.of(2026, 10, 25), parse("10/25/2026").date)
        assertEquals(LocalDate.of(2026, 10, 25), parse("25-10-26").date)
    }

    @Test fun aFutureOrImplausibleDateIsIgnored() {
        assertNull(parse("2026-12-25").date)
        assertNull(parse("1999-01-01").date)
        assertNull(parse("2026-13-45").date)
    }

    @Test fun anExistingPayeeNamedOnTheReceiptComesFirst() {
        val receipt = parse(
            "Receipt", "GREEN LEAF MARKET", "TOTAL 5.00",
            payees = listOf("Green Leaf", "Green Leaf Market", "Other Shop"),
        )

        assertEquals("Green Leaf Market", receipt.payee)
    }

    @Test fun payeeMatchingIgnoresCaseAndPunctuation() {
        assertEquals("Joe's Cafe", parse("JOES  CAFE", payees = listOf("Joe's Cafe")).payee)
    }

    @Test fun shortPayeeNamesAreNotMatched() {
        assertEquals("Corner Shop", parse("Corner Shop", payees = listOf("Co")).payee)
    }

    @Test fun withoutAMatchThePayeeIsTheFirstNameLikeLine() {
        val receipt = parse("Receipt No 4411", "Tel 555 0100", "Corner Shop", "TOTAL 5.00")

        assertEquals("Corner Shop", receipt.payee)
    }

    @Test fun textWithNothingUsefulGivesAnEmptyResult() {
        assertTrue(parse("12 34", "55 0100", "...").isEmpty)
        assertTrue(parse().isEmpty)
    }

    // --- Sales tax and line items (synthetic text) -------------------------------------------------------

    private val groceryReceipt = arrayOf(
        "GREEN LEAF MARKET", "12 Example Street", "Tel 555 0100", "2026-11-02 14:31",
        "Milk 2.50", "Bread 3.00", "SUBTOTAL 5.50", "TAX 0.44", "TOTAL 5.94", "CASH 10.00", "CHANGE 4.06",
    )

    @Test fun taxAndItemsAreReadFromATypicalReceipt() {
        val receipt = parse(*groceryReceipt)

        assertEquals(594L, receipt.totalCents)
        assertEquals(44L, receipt.taxCents)
        assertEquals(listOf(ReceiptItem("Milk", 250), ReceiptItem("Bread", 300)), receipt.items)
    }

    @Test fun aReceiptWithOnlyATotalHasNoTaxAndNoItems() {
        val receipt = parse("CORNER SHOP", "TOTAL 5.94")

        assertNull(receipt.taxCents)
        assertTrue(receipt.items.isEmpty())
    }

    @Test fun aPercentageIsNeverReadAsTheTaxAmount() {
        assertEquals(47L, parse("Sales Tax 8.5% 0.47", "TOTAL 6.47").taxCents)
        assertEquals(47L, parse("VAT 20% 0.47", "TOTAL 6.47").taxCents)
    }

    @Test fun taxRowsOfSeveralRatesAreAddedUp() {
        assertEquals(44L, parse("TAX A 0.30", "TAX B 0.14", "TOTAL 5.94").taxCents)
    }

    @Test fun aTotalTaxRowWinsOverTheRatesAboveIt() {
        assertEquals(44L, parse("TAX A 0.30", "TAX B 0.14", "TOTAL TAX 0.44", "TOTAL 5.94").taxCents)
    }

    @Test fun taxThatIsNotSmallerThanTheTotalIsIgnored() {
        assertNull(parse("TAX 1.00", "TOTAL 1.00").taxCents)
    }

    @Test fun taxInvoiceHeadingsAndSubtotalsIncludingTaxAreNotTaxRows() {
        assertNull(parse("TAX INVOICE", "Tax ID 12345678", "SUBTOTAL INCL TAX 5.50", "TOTAL 5.94").taxCents)
    }

    @Test fun paymentAndContactRowsAreNotItems() {
        val receipt = parse("Tel 555 0100", "12 Example Street", "Milk 2.50", "CASH 10.00", "VISA 10.00", "SUBTOTAL 2.50", "TOTAL 2.50")

        assertEquals(listOf(ReceiptItem("Milk", 250)), receipt.items)
    }

    @Test fun withoutASubtotalOrTotalRowNoItemsAreTrusted() {
        assertTrue(parse("Milk 2.50", "Bread 3.00").items.isEmpty())
    }

    @Test fun itemsAcceptACurrencySymbolAndATaxCodeLetter() {
        val receipt = parse("Milk $2.50 A", "Bread 3.00 B", "TOTAL 5.50")

        assertEquals(listOf(ReceiptItem("Milk", 250), ReceiptItem("Bread", 300)), receipt.items)
    }

    @Test fun itemsAndTaxReadBengaliDigits() {
        val receipt = parse("Rice ১২.৫০", "Oil ৩.০০", "SUBTOTAL ১৫.৫০", "TAX ১.০০", "TOTAL ১৬.৫০")

        assertEquals(listOf(ReceiptItem("Rice", 1250), ReceiptItem("Oil", 300)), receipt.items)
        assertEquals(100L, receipt.taxCents)
    }

    @Test fun anUnrecognizedLineIsSkippedNotGuessed() {
        val receipt = parse("Milk 2.50", "x7#%", "Bread", "SUBTOTAL 2.50", "TOTAL 2.50")

        assertEquals(listOf(ReceiptItem("Milk", 250)), receipt.items)
    }
}
