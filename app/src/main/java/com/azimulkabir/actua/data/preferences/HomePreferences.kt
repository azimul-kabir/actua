package com.azimulkabir.actua.data.preferences

import android.content.Context
import com.azimulkabir.actua.data.home.HomeLayout
import com.azimulkabir.actua.data.home.HomeLayoutCodec
import com.azimulkabir.actua.data.home.Holiday
import com.azimulkabir.actua.data.home.HolidayCodec
import com.azimulkabir.actua.data.home.HomeSummaryPeriod
import com.azimulkabir.actua.data.home.MonthStartRule
import com.azimulkabir.actua.data.home.StartDayAdjustment
import java.time.DayOfWeek

/** Home dashboard section order and visibility; device-local UI preference, not budget data. */
class HomePreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("home_preferences", Context.MODE_PRIVATE)

    fun layout(): HomeLayout = HomeLayoutCodec.decode(preferences.getString(LAYOUT, null))

    fun save(layout: HomeLayout) {
        preferences.edit().putString(LAYOUT, HomeLayoutCodec.encode(layout)).apply()
    }

    fun restoreDefaults(): HomeLayout = HomeLayout.default().also { save(it) }

    /** Start day (1–31) of the Home summary period for [budgetId]; 1 is the calendar month. */
    fun summaryStartDay(budgetId: String): Int =
        preferences.getInt(summaryStartDayKey(budgetId), HomeSummaryPeriod.CALENDAR_MONTH_START_DAY).coerceIn(1, 31)

    fun setSummaryStartDay(budgetId: String, day: Int) {
        preferences.edit().putInt(summaryStartDayKey(budgetId), day.coerceIn(1, 31)).apply()
    }

    /** What to do when the start day lands on a weekend day for [budgetId]; none by default. */
    fun startDayAdjustment(budgetId: String): StartDayAdjustment =
        preferences.getString(startDayAdjustmentKey(budgetId), null)
            ?.let { name -> StartDayAdjustment.values().firstOrNull { it.name == name } }
            ?: StartDayAdjustment.NONE

    fun setStartDayAdjustment(budgetId: String, adjustment: StartDayAdjustment) {
        preferences.edit().putString(startDayAdjustmentKey(budgetId), adjustment.name).apply()
    }

    /** The weekdays treated as the weekend for [budgetId]; Saturday and Sunday unless changed. */
    fun weekendDays(budgetId: String): Set<DayOfWeek> =
        preferences.getString(weekendDaysKey(budgetId), null)
            ?.split(',')
            ?.mapNotNull { it.toIntOrNull()?.takeIf { value -> value in 1..7 }?.let(DayOfWeek::of) }
            ?.toSet()
            ?: MonthStartRule.DEFAULT_WEEKEND_DAYS

    fun setWeekendDays(budgetId: String, days: Set<DayOfWeek>) {
        preferences.edit().putString(weekendDaysKey(budgetId), days.map { it.value }.sorted().joinToString(",")).apply()
    }

    /** The holidays listed for [budgetId], sorted by date; empty unless the user added some. */
    fun holidays(budgetId: String): List<Holiday> = HolidayCodec.decode(preferences.getString(holidaysKey(budgetId), null))

    fun setHolidays(budgetId: String, holidays: List<Holiday>) {
        preferences.edit().putString(holidaysKey(budgetId), HolidayCodec.encode(holidays)).apply()
    }

    /** The full start rule for [budgetId], as the summaries and report presets consume it. */
    fun monthStartRule(budgetId: String) = MonthStartRule(
        summaryStartDay(budgetId), startDayAdjustment(budgetId), weekendDays(budgetId),
        holidays(budgetId).mapTo(mutableSetOf()) { it.date },
    )

    private fun summaryStartDayKey(budgetId: String) = "$SUMMARY_START_DAY:$budgetId"
    private fun startDayAdjustmentKey(budgetId: String) = "$START_DAY_ADJUSTMENT:$budgetId"
    private fun weekendDaysKey(budgetId: String) = "$WEEKEND_DAYS:$budgetId"
    private fun holidaysKey(budgetId: String) = "$HOLIDAYS:$budgetId"

    private companion object {
        const val LAYOUT = "layout"
        const val SUMMARY_START_DAY = "summary_start_day"
        const val START_DAY_ADJUSTMENT = "start_day_adjustment"
        const val WEEKEND_DAYS = "weekend_days"
        const val HOLIDAYS = "holidays"
    }
}
