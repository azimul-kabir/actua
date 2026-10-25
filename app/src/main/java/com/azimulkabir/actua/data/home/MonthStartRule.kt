package com.azimulkabir.actua.data.home

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** What to do when the month start day falls on a weekend day. */
enum class StartDayAdjustment { NONE, PREVIOUS_WORKING_DAY, NEXT_WORKING_DAY }

/**
 * When a pay period starts: a day of the month (1..31, clamped to short months) that can move to the
 * previous or next working day when it lands on one of [weekendDays]. Device-local and display-only;
 * it never changes budget months or stored data. The default rule (day 1, no adjustment) is the
 * calendar month.
 */
data class MonthStartRule(
    val day: Int = CALENDAR_MONTH_START_DAY,
    val adjustment: StartDayAdjustment = StartDayAdjustment.NONE,
    val weekendDays: Set<DayOfWeek> = DEFAULT_WEEKEND_DAYS,
) {
    /** True when the rule is plain calendar months, so month-based data keeps its upstream meaning. */
    val isCalendarMonth: Boolean get() = day == CALENDAR_MONTH_START_DAY && adjustment == StartDayAdjustment.NONE

    /** The first day of the pay period anchored to [month], after any working-day adjustment. */
    fun startFor(month: YearMonth): LocalDate {
        val base = month.atDay(day.coerceIn(1, 31).coerceAtMost(month.lengthOfMonth()))
        // With every weekday a weekend there is no working day to move to, so the day stays put.
        if (adjustment == StartDayAdjustment.NONE || weekendDays.size >= DayOfWeek.values().size) return base
        val step = if (adjustment == StartDayAdjustment.PREVIOUS_WORKING_DAY) -1L else 1L
        var date = base
        while (date.dayOfWeek in weekendDays) date = date.plusDays(step)
        return date
    }

    companion object {
        const val CALENDAR_MONTH_START_DAY = 1
        val DEFAULT_WEEKEND_DAYS: Set<DayOfWeek> = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        val CALENDAR_MONTH = MonthStartRule()
    }
}
