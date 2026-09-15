package com.azimulkabir.actua.data.budget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TagRenameTest {
    @Test fun renamesOnlyExactManagedTagTokens() {
        assertEquals("Lunch #office #work2", renameTagInNotes("Lunch #work #work2", "work", "office"))
    }

    @Test fun preservesEscapedHashesAndMultipleTags() {
        assertEquals("##work #office text #office", renameTagInNotes("##work #work text #work", "work", "office"))
    }

    @Test fun matchingIsCaseSensitiveLikeActual() {
        assertEquals("#Work #office", renameTagInNotes("#Work #work", "work", "office"))
    }

    @Test fun validatesActualTagNames() {
        assertEquals("school", validateTagName(" school "))
        assertThrows(IllegalArgumentException::class.java) { validateTagName("school fee") }
        assertThrows(IllegalArgumentException::class.java) { validateTagName("#school") }
    }
}
