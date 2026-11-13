package com.azimulkabir.actua.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GreetingLogicTest {
    private val all = listOf(
        GreetingFigure.CategoriesOnBudget(9, 12),
        GreetingFigure.Uncategorized(4),
        GreetingFigure.BillsDueThisWeek(2),
        GreetingFigure.AddedThisWeek(11),
    )

    @Test fun the_greeting_follows_the_hour_of_the_day() {
        fun at(hour: Int) = GreetingPartOfDay.forHour(hour)
        assertEquals(GreetingPartOfDay.Evening, at(0))
        assertEquals(GreetingPartOfDay.Evening, at(4))
        assertEquals(GreetingPartOfDay.Morning, at(5))
        assertEquals(GreetingPartOfDay.Morning, at(11))
        assertEquals(GreetingPartOfDay.Afternoon, at(12))
        assertEquals(GreetingPartOfDay.Afternoon, at(17))
        assertEquals(GreetingPartOfDay.Evening, at(18))
        assertEquals(GreetingPartOfDay.Evening, at(23))
    }

    @Test fun a_nickname_is_trimmed_and_capped_at_24_characters() {
        assertEquals("Sam", GreetingNickname.clean("  Sam  "))
        assertEquals("", GreetingNickname.clean("   "))
        assertEquals(24, GreetingNickname.clean("x".repeat(40)).length)
    }

    @Test fun the_strip_shows_once_per_calendar_day() {
        val day = LocalDate.of(2026, 11, 13)
        assertTrue(GreetingStripPolicy.shouldShow(null, day))
        assertFalse(GreetingStripPolicy.shouldShow(day.toString(), day))
        assertTrue(GreetingStripPolicy.shouldShow(day.toString(), day.plusDays(1)))
        // The date rolls over while the app stays open: the next check sees a new day.
        assertTrue(GreetingStripPolicy.shouldShow(day.toString(), LocalDate.of(2026, 11, 14)))
    }

    @Test fun the_daily_order_is_stable_within_a_day_and_changes_between_days() {
        val day = LocalDate.of(2026, 11, 13)
        val first = GreetingStripPolicy.dailyOrder(all, day, "budget-1")
        assertEquals(first, GreetingStripPolicy.dailyOrder(all, day, "budget-1"))
        assertEquals(all.toSet(), first.toSet())
        val orders = (0L..9L).map { GreetingStripPolicy.dailyOrder(all, day.plusDays(it), "budget-1") }.toSet()
        assertTrue("expected the order to vary across days", orders.size > 1)
    }

    @Test fun omitted_figures_are_never_picked() {
        val available = all - GreetingFigure.BillsDueThisWeek(2) - GreetingFigure.AddedThisWeek(11)
        (0L..20L).forEach {
            val picked = GreetingStripPolicy.dailyOrder(available, LocalDate.of(2026, 1, 1).plusDays(it), null)
            assertEquals(available.toSet(), picked.toSet())
        }
    }
}
