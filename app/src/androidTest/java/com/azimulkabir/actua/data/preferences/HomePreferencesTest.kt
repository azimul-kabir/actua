package com.azimulkabir.actua.data.preferences

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.home.Holiday
import com.azimulkabir.actua.data.home.HomeLayout
import com.azimulkabir.actua.data.home.HomeSection
import com.azimulkabir.actua.data.home.MonthStartRule
import com.azimulkabir.actua.data.home.StartDayAdjustment
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePreferencesTest {
    @Test
    fun savedLayoutSurvivesRestartAndRestoreDefaultsResetsIt() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("home_preferences", Context.MODE_PRIVATE).edit().clear().commit()

        val fresh = HomePreferences(context)
        assertEquals(HomeLayout.default(), fresh.layout())

        val customized = HomeLayout(
            order = listOf(
                HomeSection.READY_TO_BUDGET,
                HomeSection.RECENT_ACTIVITY,
                HomeSection.FAVORITE_CATEGORIES,
                HomeSection.FAVORITE_ACCOUNTS,
                HomeSection.UPCOMING,
                HomeSection.THIS_MONTH,
                HomeSection.REPORTS,
            ),
            hidden = setOf(HomeSection.REPORTS),
        )
        fresh.save(customized)

        // Simulate a restart by reading the layout back through a new instance over the same store.
        val reloaded = HomePreferences(context)
        assertEquals(customized, reloaded.layout())

        assertEquals(HomeLayout.default(), reloaded.restoreDefaults())
        assertEquals(HomeLayout.default(), reloaded.layout())
    }

    @Test
    fun summaryStartDayDefaultsToTheCalendarMonthPersistsAndIsPerBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("home_preferences", Context.MODE_PRIVATE).edit().clear().commit()

        val fresh = HomePreferences(context)
        assertEquals(1, fresh.summaryStartDay("budget-a"))

        fresh.setSummaryStartDay("budget-a", 27)
        assertEquals(27, HomePreferences(context).summaryStartDay("budget-a"))
        assertEquals(1, HomePreferences(context).summaryStartDay("budget-b"))

        fresh.setSummaryStartDay("budget-a", 99)
        assertEquals(31, fresh.summaryStartDay("budget-a"))
        fresh.setSummaryStartDay("budget-a", 0)
        assertEquals(1, fresh.summaryStartDay("budget-a"))
    }

    @Test
    fun adjustmentAndWeekendDaysDefaultPersistAndArePerBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("home_preferences", Context.MODE_PRIVATE).edit().clear().commit()

        val fresh = HomePreferences(context)
        assertEquals(StartDayAdjustment.NONE, fresh.startDayAdjustment("budget-a"))
        assertEquals(MonthStartRule.DEFAULT_WEEKEND_DAYS, fresh.weekendDays("budget-a"))
        assertEquals(MonthStartRule.CALENDAR_MONTH, fresh.monthStartRule("budget-a"))

        fresh.setSummaryStartDay("budget-a", 25)
        fresh.setStartDayAdjustment("budget-a", StartDayAdjustment.PREVIOUS_WORKING_DAY)
        fresh.setWeekendDays("budget-a", setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY))
        assertEquals(
            MonthStartRule(25, StartDayAdjustment.PREVIOUS_WORKING_DAY, setOf(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)),
            HomePreferences(context).monthStartRule("budget-a"),
        )
        assertEquals(MonthStartRule.CALENDAR_MONTH, HomePreferences(context).monthStartRule("budget-b"))

        // An empty weekend (every day is a working day) is a real choice and survives a restart.
        fresh.setWeekendDays("budget-a", emptySet())
        assertEquals(emptySet<DayOfWeek>(), HomePreferences(context).weekendDays("budget-a"))
    }

    @Test
    fun holidaysDefaultToNoneRoundTripSortedAndArePerBudget() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("home_preferences", Context.MODE_PRIVATE).edit().clear().commit()

        val fresh = HomePreferences(context)
        assertEquals(emptyList<Holiday>(), fresh.holidays("budget-a"))

        fresh.setHolidays("budget-a", listOf(Holiday(LocalDate.of(2026, 6, 1), "Eid"), Holiday(LocalDate.of(2026, 2, 21))))
        assertEquals(
            listOf(Holiday(LocalDate.of(2026, 2, 21), ""), Holiday(LocalDate.of(2026, 6, 1), "Eid")),
            HomePreferences(context).holidays("budget-a"),
        )
        assertEquals(emptyList<Holiday>(), HomePreferences(context).holidays("budget-b"))
        assertEquals(
            setOf(LocalDate.of(2026, 2, 21), LocalDate.of(2026, 6, 1)),
            HomePreferences(context).monthStartRule("budget-a").holidays,
        )
        assertEquals(emptySet<LocalDate>(), HomePreferences(context).monthStartRule("budget-b").holidays)

        fresh.setHolidays("budget-a", emptyList())
        assertEquals(emptyList<Holiday>(), HomePreferences(context).holidays("budget-a"))
    }
}
