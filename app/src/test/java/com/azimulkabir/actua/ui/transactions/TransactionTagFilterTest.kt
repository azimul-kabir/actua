package com.azimulkabir.actua.ui.transactions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTagFilterTest {
    @Test fun `matches exact tag case sensitively like Actual's hasTags filter`() {
        assertTrue(notesContainTag("Lunch #Travel today", "Travel"))
        assertTrue(notesContainTag("#travel", "#travel"))
        assertFalse(notesContainTag("Lunch #Travel today", "travel"))
        assertFalse(notesContainTag("#travel", "#TRAVEL"))
    }

    @Test fun `a hash after another hash never opens a tag`() {
        assertFalse(notesContainTag("Use ###travel here", "travel"))
        assertTrue(notesContainTag("##x #travel", "travel"))
    }

    @Test fun `does not match longer tag`() {
        assertFalse(notesContainTag("Booked #travel2026", "travel"))
        assertFalse(notesContainTag("Booked #traveller", "travel"))
    }

    @Test fun `escaped hash is not a tag`() {
        assertFalse(notesContainTag("Use ##travel as literal text", "travel"))
    }

    @Test fun `supports punctuation as part of Actual tag token`() {
        assertTrue(notesContainTag("Paid #work-trip", "work-trip"))
        assertFalse(notesContainTag("Paid #work-trip", "work"))
    }

    @Test fun `blank and missing notes do not match`() {
        assertFalse(notesContainTag(null, "travel"))
        assertFalse(notesContainTag("#travel", ""))
    }
}
