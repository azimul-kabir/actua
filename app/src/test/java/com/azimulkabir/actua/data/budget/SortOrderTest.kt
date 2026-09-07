package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Test

class SortOrderTest {
    @Test fun appendsAndInsertsAtTopLikeIos() {
        val rows = listOf(SortOrder.Position("a", 16_384.0), SortOrder.Position("b", 32_768.0))
        assertEquals(49_152.0, SortOrder.shove(rows, null).sortOrder, 0.0)
        assertEquals(8_192.0, SortOrder.shove(rows, "a").sortOrder, 0.0)
    }

    @Test fun crampedRowsAreShovedByActualIncrement() {
        val result = SortOrder.shove(listOf(SortOrder.Position("a", 1.0), SortOrder.Position("b", 2.0)), "a")
        assertEquals(0.5, result.sortOrder, 0.0)
        assertEquals(listOf(16_385.0, 32_769.0), result.moved.map { it.sortOrder })
    }
}
