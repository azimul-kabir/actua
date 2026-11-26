package com.azimulkabir.actua.ui.budget

import org.junit.Assert.assertEquals
import org.junit.Test

class UniqueLazyKeysTest {
    @Test
    fun repeatedKeysGetAnOccurrenceSuffix() {
        assertEquals(listOf("a", "b", "a#2", "a#3"), uniqueLazyKeys(listOf("a", "b", "a", "a")))
    }

    @Test
    fun uniqueKeysAreUnchanged() {
        assertEquals(listOf("x", "y"), uniqueLazyKeys(listOf("x", "y")))
    }
}
