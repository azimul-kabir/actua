package com.azimulkabir.actua.data.home

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * The days that are not working days for the user: the weekend days plus any listed holidays.
 * Device-local and display-only; Actual keeps its own Saturday/Sunday handling.
 */
data class NonWorkingDays(
    val weekend: Set<DayOfWeek> = MonthStartRule.DEFAULT_WEEKEND_DAYS,
    val holidays: Set<LocalDate> = emptySet(),
) {
    /** True when this is Actual's own view (Saturday and Sunday, no holidays), so nothing needs moving. */
    val isDefault: Boolean get() = weekend == MonthStartRule.DEFAULT_WEEKEND_DAYS && holidays.isEmpty()

    fun isNonWorking(date: LocalDate): Boolean = date.dayOfWeek in weekend || date in holidays

    /**
     * Steps [date] by [step] days (-1 for earlier, 1 for later) until it is a working day. With every
     * weekday a weekend there is no working day to reach, and a run of non-working days longer than a
     * year is given up on, so [date] stays put in both cases.
     */
    fun workingDayFrom(date: LocalDate, step: Long): LocalDate {
        if (weekend.size >= DayOfWeek.values().size) return date
        var result = date
        var moved = 0
        while (isNonWorking(result) && moved++ < MAX_SHIFT_DAYS) result = result.plusDays(step)
        return if (isNonWorking(result)) date else result
    }

    private companion object {
        const val MAX_SHIFT_DAYS = 366
    }
}
