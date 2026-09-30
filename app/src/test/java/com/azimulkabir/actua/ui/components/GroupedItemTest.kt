package com.azimulkabir.actua.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GroupedItemTest {
    @Test fun positionOfASingleItemIsOnly() {
        assertEquals(GroupPosition.Only, GroupPosition.of(0, 1))
    }

    @Test fun positionsOfAGroupAreFirstMiddleLast() {
        assertEquals(
            listOf(GroupPosition.First, GroupPosition.Middle, GroupPosition.Middle, GroupPosition.Last),
            (0 until 4).map { GroupPosition.of(it, 4) },
        )
        assertEquals(listOf(GroupPosition.First, GroupPosition.Last), (0 until 2).map { GroupPosition.of(it, 2) })
    }

    @Test fun positionOutsideTheGroupIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { GroupPosition.of(2, 2) }
        assertThrows(IllegalArgumentException::class.java) { GroupPosition.of(0, 0) }
    }

    @Test fun onlyTheOutsideCornersOfAGroupAreRounded() {
        val base = RoundedCornerShape(16.dp)
        val corner = base.topStart

        val first = groupedItemShape(GroupPosition.First, base)
        assertEquals(listOf(corner, corner, ZeroCornerSize, ZeroCornerSize), first.corners())

        val middle = groupedItemShape(GroupPosition.Middle, base)
        assertEquals(List(4) { ZeroCornerSize }, middle.corners())

        val last = groupedItemShape(GroupPosition.Last, base)
        assertEquals(listOf(ZeroCornerSize, ZeroCornerSize, corner, corner), last.corners())

        val only = groupedItemShape(GroupPosition.Only, base)
        assertEquals(List(4) { corner }, only.corners())
    }

    private fun androidx.compose.foundation.shape.CornerBasedShape.corners() =
        listOf(topStart, topEnd, bottomEnd, bottomStart)
}
