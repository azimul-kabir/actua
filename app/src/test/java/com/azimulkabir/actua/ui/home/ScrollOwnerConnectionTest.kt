package com.azimulkabir.actua.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollOwnerConnectionTest {
    private val drag = Offset(0f, 20f)

    private fun connection(back: Boolean, forward: Boolean) = ScrollOwnerConnection({ back }, { forward })

    private fun ScrollOwnerConnection.leftover(delta: Offset): Offset {
        onPreScroll(delta, NestedScrollSource.UserInput)
        return onPostScroll(Offset.Zero, delta, NestedScrollSource.UserInput)
    }

    @Test fun the_card_keeps_a_gesture_it_can_scroll_and_swallows_what_it_cannot_use() {
        val card = connection(back = true, forward = true)
        assertEquals(drag, card.leftover(drag))
        assertEquals(Offset(0f, -30f), card.leftover(Offset(0f, -30f)))
        val fling = Velocity(0f, 900f)
        assertEquals(fling, runBlocking { card.onPostFling(Velocity.Zero, fling) })
    }

    @Test fun the_page_takes_a_drag_down_at_the_top_and_a_drag_up_at_the_bottom() {
        val atTop = connection(back = false, forward = true)
        assertEquals(Offset.Zero, atTop.leftover(drag))
        assertEquals(Velocity.Zero, runBlocking { atTop.onPostFling(Velocity.Zero, Velocity(0f, 900f)) })

        val atBottom = connection(back = true, forward = false)
        assertEquals(Offset.Zero, atBottom.leftover(Offset(0f, -20f)))
    }

    @Test fun the_owner_is_chosen_again_for_the_next_gesture() {
        var back = true
        val card = ScrollOwnerConnection({ back }, { true })
        assertEquals(drag, card.leftover(drag))
        runBlocking { card.onPostFling(Velocity.Zero, Velocity.Zero) }
        back = false
        assertEquals(Offset.Zero, card.leftover(drag))
    }
}
