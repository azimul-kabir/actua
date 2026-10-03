package com.azimulkabir.actua.data.preferences

import android.content.Context
import com.azimulkabir.actua.data.home.HomeLayout
import com.azimulkabir.actua.data.home.HomeLayoutCodec
import com.azimulkabir.actua.data.home.HomeSummaryPeriod

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

    private fun summaryStartDayKey(budgetId: String) = "$SUMMARY_START_DAY:$budgetId"

    private companion object {
        const val LAYOUT = "layout"
        const val SUMMARY_START_DAY = "summary_start_day"
    }
}
