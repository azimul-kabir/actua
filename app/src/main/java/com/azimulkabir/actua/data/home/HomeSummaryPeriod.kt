package com.azimulkabir.actua.data.home

import java.time.LocalDate
import java.time.YearMonth

/**
 * The span the Home Income / Spent / Net summary covers: from a start day of the month to the day
 * before the next start day. A start day beyond a short month's last day is clamped to it, so day 31
 * starts on Feb 28/29, Apr 30 and so on. Display-only; it never changes budget months or stored data.
 */
data class HomeSummaryPeriod(val start: LocalDate, val endInclusive: LocalDate) {
    /** True when [yyyymmdd] (Actual's day integer) falls inside the period. */
    operator fun contains(yyyymmdd: Int): Boolean = yyyymmdd in start.toDayInt()..endInclusive.toDayInt()

    companion object {
        const val CALENDAR_MONTH_START_DAY = 1

        /** The period containing [today] for a start day of 1..31 (out-of-range values are coerced). */
        fun containing(today: LocalDate, startDay: Int): HomeSummaryPeriod {
            val thisStart = clampedStart(YearMonth.from(today), startDay)
            val start = if (today >= thisStart) thisStart else clampedStart(YearMonth.from(today).minusMonths(1), startDay)
            val nextStart = clampedStart(YearMonth.from(start).plusMonths(1), startDay)
            return HomeSummaryPeriod(start, nextStart.minusDays(1))
        }

        private fun clampedStart(month: YearMonth, startDay: Int): LocalDate =
            month.atDay(startDay.coerceIn(1, 31).coerceAtMost(month.lengthOfMonth()))

        private fun LocalDate.toDayInt() = year * 10000 + monthValue * 100 + dayOfMonth
    }
}
