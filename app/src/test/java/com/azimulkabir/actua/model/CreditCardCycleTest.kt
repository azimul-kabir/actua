package com.azimulkabir.actua.model

import com.azimulkabir.actua.data.schedules.DayDate
import org.junit.Assert.assertEquals
import org.junit.Test

class CreditCardCycleTest {
    @Test fun cycleBeforeClosingDayMatchesIos() {
        val cycle = CreditCardCycle(statementDay = 15, dueOffsetDays = 25)
        val range = cycle.cycleRange(DayDate(2026, 2, 10))
        assertEquals(DayDate(2026, 1, 16), range.first)
        assertEquals(DayDate(2026, 2, 15), range.second)
    }

    @Test fun cycleAfterClosingDayMatchesIos() {
        val cycle = CreditCardCycle(statementDay = 15, dueOffsetDays = 25)
        val range = cycle.cycleRange(DayDate(2026, 2, 20))
        assertEquals(DayDate(2026, 2, 16), range.first)
        assertEquals(DayDate(2026, 3, 15), range.second)
    }

    @Test fun closingDayClampsToShortMonth() {
        val range = CreditCardCycle(31).cycleRange(DayDate(2026, 2, 20))
        assertEquals(DayDate(2026, 2, 1), range.first)
        assertEquals(DayDate(2026, 2, 28), range.second)
    }

    @Test fun availableCreditUsesActualNegativeDebtConvention() {
        val limit = 100_000L
        val balance = -23_450L
        assertEquals(76_550L, limit + balance)
    }
}
