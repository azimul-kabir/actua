package com.azimulkabir.actua.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import com.azimulkabir.actua.data.home.MonthStartRule
import com.azimulkabir.actua.data.home.Holiday
import com.azimulkabir.actua.data.home.HolidayCodec
import com.azimulkabir.actua.data.preferences.HomePreferences
import com.azimulkabir.actua.data.preferences.FavoritePreferences

/** Flow state retains its original navigation-composition lifetime and saveability. */
internal class HomeBudgetPreferencesState(
    private val homePreferences: HomePreferences,
    private val favoriteBudgetId: String,
    val homeMonthStart: MutableState<MonthStartRule>,
    val homeHolidays: MutableState<List<Holiday>>,
    val favoriteCategoryIds: MutableState<Set<String>>,
    val favoriteAccountIds: MutableState<Set<String>>,
    val favoriteReportIds: MutableState<Set<String>>,
) {
    fun setMonthStart(rule: MonthStartRule) {
        val clamped = rule.copy(day = rule.day.coerceIn(1, 31))
        homePreferences.setSummaryStartDay(favoriteBudgetId, clamped.day)
        homePreferences.setStartDayAdjustment(favoriteBudgetId, clamped.adjustment)
        homePreferences.setWeekendDays(favoriteBudgetId, clamped.weekendDays)
        homeMonthStart.value = clamped
    }

    fun setHolidays(holidays: List<Holiday>) {
        val normalized = HolidayCodec.normalize(holidays)
        homePreferences.setHolidays(favoriteBudgetId, normalized)
        homeHolidays.value = normalized
        homeMonthStart.value = homeMonthStart.value.copy(holidays = normalized.mapTo(mutableSetOf()) { it.date })
    }
}

@Composable
internal fun rememberHomeBudgetPreferencesState(homePreferences: HomePreferences, favoritePreferences: FavoritePreferences, favoriteBudgetId: String): HomeBudgetPreferencesState =
    HomeBudgetPreferencesState(
        homePreferences = homePreferences,
        favoriteBudgetId = favoriteBudgetId,
        homeMonthStart = remember(favoriteBudgetId) { mutableStateOf(homePreferences.monthStartRule(favoriteBudgetId)) },
        homeHolidays = remember(favoriteBudgetId) { mutableStateOf(homePreferences.holidays(favoriteBudgetId)) },
        favoriteCategoryIds = remember(favoriteBudgetId) {
            mutableStateOf(favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.CATEGORY))
        },
        favoriteAccountIds = remember(favoriteBudgetId) {
            mutableStateOf(favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.ACCOUNT))
        },
        favoriteReportIds = remember(favoriteBudgetId) {
            mutableStateOf(favoritePreferences.ids(favoriteBudgetId, FavoritePreferences.Type.REPORT))
        },
    )
