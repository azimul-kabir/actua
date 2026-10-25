package com.azimulkabir.actua.data.home

import java.time.LocalDate
import java.time.YearMonth

/**
 * The span the Home Income / Spent / Net summary covers: from a [MonthStartRule]'s start day of the
 * month to the day before the next start. A start day beyond a short month's last day is clamped to
 * it, so day 31 starts on Feb 28/29, Apr 30 and so on, and a start on a weekend day can move to the
 * previous or next working day. Display-only; it never changes budget months or stored data.
 */
data class HomeSummaryPeriod(val start: LocalDate, val endInclusive: LocalDate) {
    /** True when [yyyymmdd] (Actual's day integer) falls inside the period. */
    operator fun contains(yyyymmdd: Int): Boolean = yyyymmdd in start.toDayInt()..endInclusive.toDayInt()

    /**
     * The period [months] pay periods away from this one (negative for earlier ones). Moves by the
     * period's anchor month rather than by date arithmetic, because an adjusted start can sit in the
     * month before the one it belongs to.
     */
    fun shiftedByMonths(rule: MonthStartRule, months: Long): HomeSummaryPeriod {
        val startMonth = YearMonth.from(start)
        val anchor = listOf(1L, 0L, -1L).map(startMonth::plusMonths).firstOrNull { rule.startFor(it) == start } ?: startMonth
        return anchoredTo(rule, anchor.plusMonths(months))
    }

    companion object {
        const val CALENDAR_MONTH_START_DAY = MonthStartRule.CALENDAR_MONTH_START_DAY

        /** The period containing [today] for a start day of 1..31 (out-of-range values are coerced). */
        fun containing(today: LocalDate, startDay: Int): HomeSummaryPeriod =
            containing(today, MonthStartRule(startDay))

        /** The period containing [today] for [rule]. */
        fun containing(today: LocalDate, rule: MonthStartRule): HomeSummaryPeriod {
            val month = YearMonth.from(today)
            // A working-day adjustment can pull a start back into the previous month or push it into the
            // next, so look from the next month's anchor back until one starts on or before today.
            val anchor = listOf(1L, 0L, -1L, -2L).map(month::plusMonths).first { rule.startFor(it) <= today }
            return anchoredTo(rule, anchor)
        }

        private fun anchoredTo(rule: MonthStartRule, anchor: YearMonth) =
            HomeSummaryPeriod(rule.startFor(anchor), rule.startFor(anchor.plusMonths(1)).minusDays(1))

        private fun LocalDate.toDayInt() = year * 10000 + monthValue * 100 + dayOfMonth
    }
}
