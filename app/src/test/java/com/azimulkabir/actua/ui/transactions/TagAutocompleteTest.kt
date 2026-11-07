package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.data.budget.model.ActualTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagAutocompleteTest {
    @Test fun findsActiveTokenAtCursor() {
        assertEquals(ActiveTagToken("gro", 6, 10), activeTagToken("Lunch #gro later", 10))
    }

    @Test fun escapedHashDoesNotAutocomplete() {
        assertNull(activeTagToken("literal ##tag", 13))
    }

    @Test fun matchingTagsHidesHiddenAndRanksPrefixFirst() {
        val tags = listOf(
            ActualTag("1", "travel"),
            ActualTag("2", "work-travel"),
            ActualTag("3", "trash", hidden = true),
        )
        assertEquals(listOf("travel", "work-travel"), matchingTags(tags, "tra").map { it.tag })
    }

    @Test fun matchingTagsReturnsEveryMatchSoTheSuggestionRowCanScrollThroughThem() {
        val tags = (1..12).map { ActualTag("$it", "tag%02d".format(it)) }
        assertEquals(12, matchingTags(tags, "").size)
        assertEquals(12, matchingTags(tags, "tag").size)
    }

    @Test fun createValidationMatchesActualTagRules() {
        val tags = listOf(ActualTag("1", "Travel"))
        assertTrue(canCreateTag("travel", tags))
        assertFalse(canCreateTag("Travel", tags))
        assertFalse(canCreateTag("two words", tags))
        assertFalse(canCreateTag("bad#tag", tags))
    }

    @Test fun replacesWholeTokenAndStepsOverExistingSpace() {
        val token = activeTagToken("Pay #gro today", 8)!!
        assertEquals("Pay #groceries today" to 15, replaceActiveTag("Pay #gro today", token, "groceries"))
    }

    @Test fun addsTrailingSpaceAtEndOfNote() {
        val token = activeTagToken("Wash #car", 9)!!
        assertEquals("Wash #Carwash " to 14, replaceActiveTag("Wash #car", token, "Carwash"))
    }

    @Test fun addsSpaceBeforeFollowingTag() {
        val token = activeTagToken("#gro#b", 4)!!
        assertEquals("#groceries #b" to 11, replaceActiveTag("#gro#b", token, "groceries"))
    }

    @Test fun cursorAfterReplacementIsOutsideTheTag() {
        val (text, cursor) = replaceActiveTag("Wash #car", activeTagToken("Wash #car", 9)!!, "Carwash")
        assertNull(activeTagToken(text, cursor))
    }
}
