package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TransactionSearchTermsTest {
    private val today = LocalDate.of(2026, 9, 15)

    private fun terms(search: String, format: String? = "dd/MM/yyyy") =
        TransactionSearchTerms.parse(search, format, today)

    @Test
    fun aDecimalAmountIsExactCents() {
        assertEquals(4250L..4250L, terms("42.50").amountRangeCents)
        assertEquals(4250L..4250L, terms("-42.5").amountRangeCents)
        assertEquals(4205L..4205L, terms("42,05").amountRangeCents)
        assertEquals(123450L..123450L, terms("1,234.50").amountRangeCents)
        assertEquals(123450L..123450L, terms("1.234,50").amountRangeCents)
    }

    @Test
    fun aWholeNumberSpansTheWholeUnit() {
        assertEquals(4200L..4299L, terms("42").amountRangeCents)
        assertEquals(0L..99L, terms("0").amountRangeCents)
    }

    @Test
    fun textAndOverPreciseNumbersAreNotAmounts() {
        assertNull(terms("coffee").amountRangeCents)
        assertNull(terms("42.505").amountRangeCents)
        assertNull(terms("1,234").amountRangeCents)
        assertNull(terms("").amountRangeCents)
    }

    @Test
    fun datesReadInTheBudgetsDateFormat() {
        assertEquals(listOf(20260305), terms("05/03/2026").dates)
        assertEquals(listOf(20260305), terms("5/3/2026").dates)
        assertEquals(listOf(20260503), terms("05/03/2026", "MM/dd/yyyy").dates)
        assertEquals(listOf(20260305), terms("2026-03-05", "yyyy-MM-dd").dates)
    }

    @Test
    fun shortYearAndDayMonthFormsAreRead() {
        assertEquals(listOf(20260305), terms("05/03/26").dates)
        assertEquals(listOf(20260305), terms("05/03").dates)
        assertEquals(listOf(20260503), terms("05/03", "MM/dd/yyyy").dates)
    }

    @Test
    fun invalidDatesAndOtherFormatsMatchNothing() {
        assertEquals(emptyList<Int>(), terms("31/02/2026").dates)
        assertEquals(emptyList<Int>(), terms("42.50").dates)
        assertEquals(emptyList<Int>(), terms("2026-03-05").dates)
        assertEquals(emptyList<Int>(), terms("5/3/20260").dates)
    }

    @Test
    fun anUnsetDateFormatDefaultsToActualsMonthFirstFormat() {
        assertEquals(listOf(20260503), terms("05/03/2026", null).dates)
    }
}
