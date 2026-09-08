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

    @Test fun fixedDueDayAfterStatementFallsInSameMonth() {
        val cycle = CreditCardCycle(
            statementDay = 5,
            paymentDue = CreditCardCycle.PaymentDue.DayOfMonth(25),
        )

        assertEquals(DayDate(2026, 1, 25), cycle.dueDate(DayDate(2026, 1, 5)))
        assertEquals(DayDate(2026, 1, 25), cycle.upcomingDueDate(DayDate(2026, 1, 10)))
    }

    @Test fun fixedDueDayAtOrBeforeStatementFallsInNextMonth() {
        val cycle = CreditCardCycle(
            statementDay = 15,
            paymentDue = CreditCardCycle.PaymentDue.DayOfMonth(15),
        )

        assertEquals(DayDate(2026, 2, 15), cycle.dueDate(DayDate(2026, 1, 15)))
        assertEquals(DayDate(2026, 2, 15), cycle.upcomingDueDate(DayDate(2026, 1, 20)))
    }

    @Test fun fixedDueDayClampsToShortMonth() {
        val cycle = CreditCardCycle(
            statementDay = 31,
            paymentDue = CreditCardCycle.PaymentDue.DayOfMonth(30),
        )

        assertEquals(DayDate(2026, 2, 28), cycle.dueDate(DayDate(2026, 1, 31)))
    }

    @Test fun legacyOffsetConfigRemainsTheDefaultPaymentRule() {
        val config = CreditCardConfig(statementDay = 15, dueOffsetDays = 25)

        assertEquals(CreditCardCycle.PaymentDue.DaysAfter(25), config.paymentDue)
        assertEquals(DayDate(2026, 3, 12), CreditCardCycle(config.statementDay, config.paymentDue)
            .dueDate(DayDate(2026, 2, 15)))
    }

    @Test fun dueDayTakesPriorityOverStoredFallbackOffset() {
        val config = CreditCardConfig(statementDay = 15, dueOffsetDays = 25, dueDay = 1)

        assertEquals(CreditCardCycle.PaymentDue.DayOfMonth(1), config.paymentDue)
        assertEquals(DayDate(2026, 3, 1), CreditCardCycle(config.statementDay, config.paymentDue)
            .dueDate(DayDate(2026, 2, 15)))
    }

    @Test fun fixedDueDayUsesLeapDayWhenAvailable() {
        val cycle = CreditCardCycle(
            statementDay = 31,
            paymentDue = CreditCardCycle.PaymentDue.DayOfMonth(29),
        )

        assertEquals(DayDate(2028, 2, 29), cycle.dueDate(DayDate(2028, 1, 31)))
        assertEquals(DayDate(2027, 2, 28), cycle.dueDate(DayDate(2027, 1, 31)))
    }

    @Test fun widestOffsetFindsEarliestStillPendingStatement() {
        val cycle = CreditCardCycle(statementDay = 15, dueOffsetDays = CreditCardCycle.MAX_DUE_OFFSET_DAYS)

        assertEquals(DayDate(2026, 3, 16), cycle.upcomingDueDate(DayDate(2026, 2, 20)))
    }

    @Test fun availableCreditUsesActualNegativeDebtConvention() {
        val limit = 100_000L
        val balance = -23_450L
        assertEquals(76_550L, limit + balance)
    }
}
