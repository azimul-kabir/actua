package com.azimulkabir.actua.data.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutTest {
    /** The section order from before the greeting existed. */
    private val withoutGreeting = HomeSection.entries.filterNot { it == HomeSection.GREETING }

    @Test fun default_layout_matches_the_agreed_v1_order_with_nothing_hidden() {
        val default = HomeLayout.default()
        assertEquals(HomeSection.entries.toList(), default.order)
        assertTrue(default.hidden.isEmpty())
        assertEquals(default.order, default.visibleSections)
    }

    @Test fun move_section_reorders_and_leaves_ready_to_budget_untouched() {
        val moved = HomeLayoutPlanner.moveSection(
            withoutGreeting,
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
        assertNull(HomeLayoutPlanner.moveSectionUp(order, HomeSection.GREETING)) // already first reorderable
    }

    @Test fun move_up_and_down_step_one_position_at_a_time() {
        val order = withoutGreeting
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
        assertEquals(savedOrder.drop(1), sanitized.order.drop(2).take(2))
        // Missing sections land at the end, in their declared (default) order.
        assertEquals(
            HomeSection.entries.filterNot { it in savedOrder || it == HomeSection.GREETING },
            sanitized.order.drop(savedOrder.size + 1),
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
                HomeSection.GREETING,
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

    @Test fun a_layout_saved_before_the_greeting_gets_it_at_the_top_of_the_reorderable_sections() {
        val saved = HomeLayoutCodec.encode(HomeLayout(withoutGreeting.reversed(), emptySet()))
            .replace("\"GREETING\",", "").replace(",\"GREETING\"", "")
        val decoded = HomeLayoutCodec.decode(saved)
        assertEquals(HomeSection.READY_TO_BUDGET, decoded.order[0])
        assertEquals(HomeSection.GREETING, decoded.order[1])
        // The rest keeps the user's saved arrangement.
        assertEquals(withoutGreeting.drop(1).reversed().filterNot { it == HomeSection.READY_TO_BUDGET }, decoded.order.drop(2))
    }

    @Test fun the_greeting_is_offered_to_supporters_only() {
        assertTrue(HomeSection.GREETING in HomeLayoutPlanner.offered(supporter = true))
        assertFalse(HomeSection.GREETING in HomeLayoutPlanner.offered(supporter = false))
        assertEquals(HomeSection.entries.size - 1, HomeLayoutPlanner.offered(supporter = false).size)
        val restricted = HomeLayoutPlanner.restrict(HomeLayout.default(), HomeLayoutPlanner.offered(supporter = false))
        assertFalse(HomeSection.GREETING in restricted.order)
    }

    @Test fun editing_the_layout_without_the_greeting_keeps_its_place_and_visibility() {
        val offered = HomeLayoutPlanner.offered(supporter = false)
        val previous = HomeLayout(HomeSection.entries.toList(), hidden = setOf(HomeSection.GREETING))
        val edited = HomeLayoutPlanner.restrict(previous, offered).let {
            it.copy(order = HomeLayoutPlanner.moveSection(it.order, HomeSection.REPORTS, HomeSection.FAVORITE_CATEGORIES)!!)
        }
        val restored = HomeLayoutPlanner.restore(previous, edited, offered)
        assertEquals(HomeSection.GREETING, restored.order[1])
        assertEquals(HomeSection.REPORTS, restored.order[2])
        assertEquals(setOf(HomeSection.GREETING), restored.hidden)
    }
}
