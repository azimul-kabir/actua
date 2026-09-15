package com.azimulkabir.actua.ui.transactions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionTagFilterTest {
    @Test fun `matches exact tag case insensitively`() {
        assertTrue(notesContainTag("Lunch #Travel today", "travel"))
        assertTrue(notesContainTag("#travel", "#TRAVEL"))
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
