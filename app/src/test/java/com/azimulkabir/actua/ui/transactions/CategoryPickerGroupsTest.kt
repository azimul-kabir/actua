package com.azimulkabir.actua.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

/** #921: categories are browsed by group in budget order, as in Actual, not A–Z. */
class CategoryPickerGroupsTest {
    private val sections = listOf(
        "Everyday" to listOf("🟦 Groceries", "🟥 Dining"),
        "Bills" to listOf("Rent", "Power"),
        "Income" to listOf("Salary"),
    )

    @Test
    fun sectionsKeepTheirOrderAndEmojiNamesStayInTheirGroup() {
        assertEquals(
            sections,
            browsePickerGroups(listOf("Power", "Rent", "Salary", "🟥 Dining", "🟦 Groceries"), sections),
        )
    }

    @Test
    fun optionsShownElsewhereLeaveTheirGroupAndEmptyGroupsAreDropped() {
        // e.g. suggested options are removed before grouping
        assertEquals(
            listOf("Everyday" to listOf("🟥 Dining"), "Bills" to listOf("Rent", "Power")),
            browsePickerGroups(listOf("🟥 Dining", "Rent", "Power"), sections),
        )
    }

    @Test
    fun withoutSectionsOptionsAreBucketedAToZ() {
        assertEquals(
            listOf("C" to listOf("Cafe"), "S" to listOf("shop", "Store"), "#" to listOf("🟦 Groceries")),
            browsePickerGroups(listOf("Store", "🟦 Groceries", "Cafe", "shop"), emptyList()),
        )
    }

    @Test
    fun optionsNoSectionHoldsFollowInLetterBuckets() {
        assertEquals(
            listOf("Bills" to listOf("Rent"), "Z" to listOf("Zoo")),
            browsePickerGroups(listOf("Zoo", "Rent"), listOf("Bills" to listOf("Rent"))),
        )
    }
}
