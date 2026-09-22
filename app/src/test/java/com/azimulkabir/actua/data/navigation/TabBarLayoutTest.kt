package com.azimulkabir.actua.data.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabBarLayoutTest {
    @Test fun default_layout_visible_tabs_match_todays_fixed_bottom_bar() {
        val default = TabBarLayout.default()
        assertEquals(
            listOf(TabItem.HOME, TabItem.BUDGET, TabItem.TRANSACTIONS, TabItem.ACCOUNTS, TabItem.MANAGE),
            default.visibleTabs,
        )
        assertEquals(setOf(TabItem.REPORTS, TabItem.ADD), default.hidden)
        assertEquals(TabItem.entries.toSet(), default.order.toSet())
    }

    @Test fun move_tab_reorders_and_leaves_manage_untouched() {
        // default() order is [HOME, BUDGET, TRANSACTIONS, ACCOUNTS, MANAGE, REPORTS, ADD].
        val moved = TabBarLayoutPlanner.moveTab(
            TabBarLayout.default().order,
            TabItem.ACCOUNTS,
            TabItem.BUDGET,
        )
        assertEquals(
            listOf(TabItem.HOME, TabItem.ACCOUNTS, TabItem.BUDGET, TabItem.TRANSACTIONS, TabItem.MANAGE, TabItem.REPORTS, TabItem.ADD),
            moved,
        )
    }

    @Test fun manage_cannot_be_moved_or_targeted() {
        val order = TabBarLayout.default().order
        assertNull(TabBarLayoutPlanner.moveTab(order, TabItem.MANAGE, TabItem.BUDGET))
        assertNull(TabBarLayoutPlanner.moveTab(order, TabItem.BUDGET, TabItem.MANAGE))
        assertNull(TabBarLayoutPlanner.moveTabUp(order, TabItem.HOME)) // already first reorderable
    }

    @Test fun move_up_and_down_step_one_position_at_a_time() {
        val order = TabBarLayout.default().order
        val down = TabBarLayoutPlanner.moveTabDown(order, TabItem.BUDGET)
        assertEquals(TabItem.TRANSACTIONS, down?.get(1))
        assertEquals(TabItem.BUDGET, down?.get(2))

        val up = TabBarLayoutPlanner.moveTabUp(down!!, TabItem.BUDGET)
        assertEquals(order, up)
    }

    @Test fun move_down_from_the_last_position_is_a_no_op() {
        // ADD is the last reorderable entry in default()'s order (Manage excluded).
        assertNull(TabBarLayoutPlanner.moveTabDown(TabBarLayout.default().order, TabItem.ADD))
    }

    @Test fun move_tab_with_unknown_or_equal_ids_is_a_no_op() {
        val order = TabBarLayout.default().order
        assertNull(TabBarLayoutPlanner.moveTab(order, TabItem.BUDGET, TabItem.BUDGET))
        assertNull(TabBarLayoutPlanner.moveTab(listOf(TabItem.HOME, TabItem.MANAGE), TabItem.BUDGET, TabItem.HOME)) // not in the given order
    }

    @Test fun set_hidden_never_hides_manage() {
        val layout = TabBarLayout.default()
        val attempt = TabBarLayoutPlanner.setHidden(layout, TabItem.MANAGE, true)
        assertEquals(layout, attempt)

        val hiddenTransactions = TabBarLayoutPlanner.setHidden(layout, TabItem.TRANSACTIONS, true)
        assertTrue(TabItem.TRANSACTIONS in hiddenTransactions.hidden)
        assertFalse(TabItem.TRANSACTIONS in hiddenTransactions.visibleTabs)
        assertTrue(TabItem.MANAGE in hiddenTransactions.visibleTabs)
    }

    @Test fun set_hidden_refuses_to_drop_below_the_minimum_visible_tabs() {
        // default() has 5 visible tabs; hide two down to the 3-tab minimum, then a third must refuse.
        var layout = TabBarLayoutPlanner.setHidden(TabBarLayout.default(), TabItem.TRANSACTIONS, true)
        layout = TabBarLayoutPlanner.setHidden(layout, TabItem.ACCOUNTS, true)
        assertEquals(3, layout.visibleTabs.size)

        val refused = TabBarLayoutPlanner.setHidden(layout, TabItem.BUDGET, true)
        assertEquals(layout, refused)
        assertEquals(3, refused.visibleTabs.size)
    }

    @Test fun set_hidden_refuses_to_rise_above_the_maximum_visible_tabs() {
        // default() already has 5 visible tabs (the maximum); showing a hidden 6th tab must refuse.
        val layout = TabBarLayout.default()
        assertEquals(5, layout.visibleTabs.size)

        val refused = TabBarLayoutPlanner.setHidden(layout, TabItem.REPORTS, false)
        assertEquals(layout, refused)
        assertEquals(5, refused.visibleTabs.size)
    }

    @Test fun sanitize_appends_newly_introduced_tabs_deterministically_without_disturbing_saved_order() {
        // Simulates an existing saved layout from before a new tab shipped: it only knows about a
        // subset of today's TabItem entries.
        val savedOrder = listOf(TabItem.MANAGE, TabItem.BUDGET, TabItem.ACCOUNTS)
        val sanitized = TabBarLayoutPlanner.sanitize(TabBarLayout(savedOrder, hidden = emptySet()))

        assertEquals(TabItem.MANAGE, sanitized.order.first())
        assertEquals(savedOrder.drop(1), sanitized.order.drop(1).take(2))
        // Missing tabs land at the end, in their declared (default) order.
        assertEquals(
            TabItem.entries.filterNot { it in savedOrder },
            sanitized.order.drop(savedOrder.size),
        )
        assertEquals(TabItem.entries.size, sanitized.order.size)
        assertEquals(TabItem.entries.toSet(), sanitized.order.toSet())
    }

    @Test fun sanitize_drops_unknown_tabs_and_never_hides_manage() {
        val sanitized = TabBarLayoutPlanner.sanitize(
            TabBarLayout(listOf(TabItem.BUDGET, TabItem.MANAGE), hidden = setOf(TabItem.MANAGE)),
        )
        assertTrue(TabItem.MANAGE !in sanitized.hidden)
        assertEquals(TabItem.entries.toSet(), sanitized.order.toSet())
    }

    @Test fun sanitize_of_default_is_a_no_op() {
        // TabBarLayout.default() is already valid/complete, so sanitizing it must not relocate
        // Manage or otherwise change anything - it should round-trip through save/read unchanged.
        val sanitized = TabBarLayoutPlanner.sanitize(TabBarLayout.default())
        assertEquals(TabBarLayout.default(), sanitized)
        assertEquals(TabItem.MANAGE, sanitized.visibleTabs.last())
    }

    @Test fun sanitize_unhides_tabs_to_restore_the_minimum_visible_count() {
        // Every tab but Manage is hidden: far below the 3-tab minimum.
        val allButManageHidden = TabItem.entries.filterNot { it == TabItem.MANAGE }.toSet()
        val sanitized = TabBarLayoutPlanner.sanitize(TabBarLayout(TabItem.entries.toList(), allButManageHidden))

        assertTrue(sanitized.visibleTabs.size >= 3)
        assertTrue(TabItem.MANAGE in sanitized.visibleTabs)
    }

    @Test fun sanitize_hides_extra_tabs_to_respect_the_maximum_visible_count() {
        // All 7 TabItem entries visible at once exceeds the 5-tab maximum.
        val sanitized = TabBarLayoutPlanner.sanitize(TabBarLayout(TabItem.entries.toList(), emptySet()))

        assertEquals(5, sanitized.visibleTabs.size)
        assertTrue(TabItem.MANAGE in sanitized.visibleTabs)
    }

    @Test fun codec_round_trips_a_customized_layout() {
        // A full, already-sanitized order (all TabItem entries, 5 of 7 visible) so encode()'s
        // internal sanitize() pass is a no-op and the round trip is exact.
        val layout = TabBarLayout(
            order = listOf(
                TabItem.MANAGE,
                TabItem.BUDGET,
                TabItem.ACCOUNTS,
                TabItem.TRANSACTIONS,
                TabItem.REPORTS,
                TabItem.HOME,
                TabItem.ADD,
            ),
            hidden = setOf(TabItem.TRANSACTIONS, TabItem.ADD),
        )
        val encoded = TabBarLayoutCodec.encode(layout)
        assertEquals(layout, TabBarLayoutCodec.decode(encoded))
    }

    @Test fun codec_falls_back_to_defaults_for_missing_or_corrupt_data() {
        assertEquals(TabBarLayout.default(), TabBarLayoutCodec.decode(null))
        assertEquals(TabBarLayout.default(), TabBarLayoutCodec.decode("not json"))
    }

    @Test fun codec_migrates_a_layout_saved_before_a_tab_was_renamed_or_removed() {
        val legacy = """{"version":1,"order":["MANAGE","SOME_RETIRED_TAB","BUDGET"],"hidden":["SOME_RETIRED_TAB"]}"""
        val decoded = TabBarLayoutCodec.decode(legacy)
        assertEquals(TabItem.entries.toSet(), decoded.order.toSet())
        assertTrue(decoded.hidden.none { it.name == "SOME_RETIRED_TAB" })
        assertEquals(TabItem.MANAGE, decoded.order.first())
    }
}
