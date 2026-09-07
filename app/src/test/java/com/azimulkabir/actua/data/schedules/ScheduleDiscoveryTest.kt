package com.azimulkabir.actua.data.schedules

import org.junit.Assert.*
import org.junit.Test

class ScheduleDiscoveryTest {
    private fun candidate(date: Int, amount: Long, payee: String = "p1") =
        ScheduleDiscovery.Candidate("$date-$payee", requireNotNull(DayDate.fromYyyymmdd(date)), amount, payee, "acct-1")
    private fun config() = RecurConfig(RecurConfig.Frequency.MONTHLY, 1, DayDate(2026, 6, 15))

    @Test fun thresholdAndRankMatchIos() {
        assertEquals(7_500L, ScheduleDiscovery.approxThreshold(-100_000))
        assertEquals(0.5, ScheduleDiscovery.rank(DayDate(2026, 8, 15), DayDate(2026, 8, 16)), 0.0)
    }

    @Test fun exactMonthlyRepeatIsDetected() {
        val match = ScheduleDiscovery.match(listOf(
            DayDate(2026, 6, 15) to listOf(candidate(20260615, -125_000)),
            DayDate(2026, 7, 15) to listOf(candidate(20260715, -125_000)),
            DayDate(2026, 8, 15) to listOf(candidate(20260815, -125_000))), config(), "acct-1").single()
        assertTrue(match.exactDate); assertTrue(match.exactAmount); assertEquals(3.0, match.rank, 0.0)
    }

    @Test fun driftAndMissingOccurrenceFollowIos() {
        val drift = ScheduleDiscovery.match(listOf(
            DayDate(2026, 6, 15) to listOf(candidate(20260616, -100_000)),
            DayDate(2026, 7, 15) to listOf(candidate(20260715, -103_000)),
            DayDate(2026, 8, 15) to listOf(candidate(20260815, -100_000))), config(), "acct-1").single()
        assertFalse(drift.exactDate); assertFalse(drift.exactAmount); assertEquals(2.5, drift.rank, 0.0)
        assertTrue(ScheduleDiscovery.match(listOf(
            DayDate(2026, 6, 15) to listOf(candidate(20260615, -100_000)),
            DayDate(2026, 7, 15) to emptyList(),
            DayDate(2026, 8, 15) to listOf(candidate(20260815, -100_000))), config(), "acct-1").isEmpty())
    }

    @Test fun candidateIndexUsesTwoDayWindow() {
        assertEquals(2, ScheduleDiscovery.CandidateIndex(listOf(candidate(20260813, -1), candidate(20260815, -2), candidate(20260818, -3)))
            .near(DayDate(2026, 8, 15), 2).size)
    }
}
