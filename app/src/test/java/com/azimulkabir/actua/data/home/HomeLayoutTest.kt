package com.azimulkabir.actua.data.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    @Test fun default_layout_matches_the_agreed_v1_order_with_nothing_hidden() {
        val default = HomeLayout.default()
        assertEquals(HomeSection.entries.toList(), default.order)
        assertTrue(default.hidden.isEmpty())
        assertEquals(default.order, default.visibleSections)
    }

    @Test fun move_section_reorders_and_leaves_ready_to_budget_untouched() {
        val moved = HomeLayoutPlanner.moveSection(
            HomeSection.entries.toList(),
            HomeSection.RECENT_ACTIVITY,
            HomeSection.FAVORITE_CATEGORIES,
        )
        assertEquals(
            listOf(
                HomeSection.READY_TO_BUDGET,
                HomeSection.RECENT_ACTIVITY,
                HomeSection.FAVORITE_CATEGORIES,
                HomeSection.FAVORITE_ACCOUNTS,
                HomeSection.UPCOMING,
                HomeSection.THIS_MONTH,
                HomeSection.REPORTS,
            ),
            moved,
        )
    }

    @Test fun ready_to_budget_cannot_be_moved_or_targeted() {
        val order = HomeSection.entries.toList()
        assertNull(HomeLayoutPlanner.moveSection(order, HomeSection.READY_TO_BUDGET, HomeSection.REPORTS))
        assertNull(HomeLayoutPlanner.moveSection(order, HomeSection.REPORTS, HomeSection.READY_TO_BUDGET))
        assertNull(HomeLayoutPlanner.moveSectionUp(order, HomeSection.FAVORITE_CATEGORIES)) // already first reorderable
    }

    @Test fun move_up_and_down_step_one_position_at_a_time() {
        val order = HomeSection.entries.toList()
        val down = HomeLayoutPlanner.moveSectionDown(order, HomeSection.FAVORITE_CATEGORIES)
        assertEquals(HomeSection.FAVORITE_ACCOUNTS, down?.get(1))
        assertEquals(HomeSection.FAVORITE_CATEGORIES, down?.get(2))

        val up = HomeLayoutPlanner.moveSectionUp(down!!, HomeSection.FAVORITE_CATEGORIES)
        assertEquals(order, up)
    }

    @Test fun move_down_from_the_last_position_is_a_no_op() {
        assertNull(HomeLayoutPlanner.moveSectionDown(HomeSection.entries.toList(), HomeSection.RECENT_ACTIVITY))
    }

    @Test fun set_hidden_never_hides_ready_to_budget() {
        val layout = HomeLayout.default()
        val attempt = HomeLayoutPlanner.setHidden(layout, HomeSection.READY_TO_BUDGET, true)
        assertTrue(attempt.hidden.isEmpty())

        val hiddenReports = HomeLayoutPlanner.setHidden(layout, HomeSection.REPORTS, true)
        assertTrue(HomeSection.REPORTS in hiddenReports.hidden)
        assertFalse(HomeSection.REPORTS in hiddenReports.visibleSections)
        assertTrue(HomeSection.READY_TO_BUDGET in hiddenReports.visibleSections)
    }

    @Test fun sanitize_appends_newly_introduced_sections_deterministically_without_disturbing_saved_order() {
        // Simulates an existing saved layout from before a new section shipped: it only knows about
        // a subset of today's HomeSection entries.
        val savedOrder = listOf(HomeSection.READY_TO_BUDGET, HomeSection.RECENT_ACTIVITY, HomeSection.FAVORITE_CATEGORIES)
        val sanitized = HomeLayoutPlanner.sanitize(HomeLayout(savedOrder, hidden = setOf(HomeSection.FAVORITE_CATEGORIES)))

        assertEquals(HomeSection.READY_TO_BUDGET, sanitized.order.first())
        assertEquals(savedOrder.drop(1), sanitized.order.drop(1).take(2))
        // Missing sections land at the end, in their declared (default) order.
        assertEquals(
            HomeSection.entries.filterNot { it in savedOrder },
            sanitized.order.drop(savedOrder.size),
        )
        assertEquals(HomeSection.entries.size, sanitized.order.size)
        assertEquals(HomeSection.entries.toSet(), sanitized.order.toSet())
        assertEquals(setOf(HomeSection.FAVORITE_CATEGORIES), sanitized.hidden)
    }

    @Test fun sanitize_drops_unknown_sections_and_never_hides_ready_to_budget() {
        val sanitized = HomeLayoutPlanner.sanitize(
            HomeLayout(listOf(HomeSection.REPORTS, HomeSection.READY_TO_BUDGET), hidden = setOf(HomeSection.READY_TO_BUDGET)),
        )
        assertEquals(HomeSection.READY_TO_BUDGET, sanitized.order.first())
        assertTrue(sanitized.hidden.isEmpty())
        assertEquals(HomeSection.entries.toSet(), sanitized.order.toSet())
    }

    @Test fun codec_round_trips_a_customized_layout() {
        val layout = HomeLayout(
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
        val encoded = HomeLayoutCodec.encode(layout)
        assertEquals(layout, HomeLayoutCodec.decode(encoded))
    }

    @Test fun codec_falls_back_to_defaults_for_missing_or_corrupt_data() {
        assertEquals(HomeLayout.default(), HomeLayoutCodec.decode(null))
        assertEquals(HomeLayout.default(), HomeLayoutCodec.decode("not json"))
    }

    @Test fun codec_migrates_a_layout_saved_before_a_section_was_renamed_or_removed() {
        val legacy = """{"version":1,"order":["READY_TO_BUDGET","SOME_RETIRED_SECTION","REPORTS"],"hidden":["SOME_RETIRED_SECTION"]}"""
        val decoded = HomeLayoutCodec.decode(legacy)
        assertEquals(HomeSection.entries.toSet(), decoded.order.toSet())
        assertTrue(decoded.hidden.none { it.name == "SOME_RETIRED_SECTION" })
        assertEquals(HomeSection.READY_TO_BUDGET, decoded.order.first())
    }
}
