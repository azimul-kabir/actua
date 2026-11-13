package com.azimulkabir.actua.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Decides, once per gesture, whether a scrolling card inside the page owns the gesture or the page does.
 *
 * If the card can scroll in the direction the finger first moves, it owns the whole gesture: whatever is
 * left when it hits its first or last row (and the fling after it) is swallowed, so the page never starts
 * moving halfway through. If it can't (a drag down at its top, a drag up at its bottom), the page owns the
 * whole gesture, so a card never traps the page under the finger. The choice resets when the gesture ends.
 */
internal class ScrollOwnerConnection(
    private val canScrollBackward: () -> Boolean,
    private val canScrollForward: () -> Boolean,
) : NestedScrollConnection {
    private enum class Owner { CARD, PAGE }

    private var owner: Owner? = null

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (owner == null && available.y != 0f) {
            // A finger moving down (available.y > 0) pulls the content towards its first row.
            val cardCanMove = if (available.y > 0f) canScrollBackward() else canScrollForward()
            owner = if (cardCanMove) Owner.CARD else Owner.PAGE
        }
        return Offset.Zero
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (owner == Owner.CARD) available else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val swallow = owner == Owner.CARD
        owner = null
        return if (swallow) available else Velocity.Zero
    }
}
