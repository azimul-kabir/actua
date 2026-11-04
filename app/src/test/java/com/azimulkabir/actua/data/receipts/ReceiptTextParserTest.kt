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
        val receipt = parse("TOTAL ১২.৫০", "০৪/১১/২০২৬")

        assertEquals(1250L, receipt.totalCents)
        assertEquals(LocalDate.of(2026, 11, 4), receipt.date)
    }

    @Test fun datesInSeveralFormatsAreRead() {
        assertEquals(LocalDate.of(2026, 11, 2), parse("2026/11/02").date)
        assertEquals(LocalDate.of(2026, 11, 2), parse("02.11.2026").date)
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
}
