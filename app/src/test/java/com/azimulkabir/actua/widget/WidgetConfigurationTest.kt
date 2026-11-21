package com.azimulkabir.actua.widget

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetConfigurationTest {
    private val choices = listOf(
        WidgetChoice("1", "Groceries", "Food"),
        WidgetChoice("2", "Dining out", "Food"),
        WidgetChoice("3", "Rent", "Home"),
        WidgetChoice("4", "Électricité", "Home"),
    )

    @Test
    fun aBlankSearchKeepsEveryChoice() {
        assertEquals(choices, choices.matching(""))
        assertEquals(choices, choices.matching("   "))
    }

    @Test
    fun searchMatchesNameOrGroupIgnoringCase() {
        assertEquals(listOf("Groceries"), choices.matching("groc").map { it.name })
        assertEquals(listOf("Groceries", "Dining out"), choices.matching("FOOD").map { it.name })
        assertEquals(listOf("Rent", "Électricité"), choices.matching(" home ").map { it.name })
        assertTrue(choices.matching("zzz").isEmpty())
    }

    @Test
    fun searchOnlyAppearsForLongLists() {
        assertTrue(choices.size < WIDGET_SEARCH_THRESHOLD)
        assertTrue(WIDGET_SEARCH_THRESHOLD <= MAX_WIDGET_ROWS)
    }

    private fun features(info: String): String? =
        Regex("""android:widgetFeatures="([^"]*)"""").find(File("src/main/res/xml/$info.xml").readText())?.groupValues?.get(1)

    private fun attribute(info: String, name: String): String? =
        Regex("android:$name=\"([^\"]*)\"").find(File("src/main/res/xml/$info.xml").readText())?.groupValues?.get(1)

    @Test
    fun theCategoryAndAccountListsArePlacedFourWideAndThreeTall() {
        // Two columns crowd the name next to the amount pill (#1662); they can still be resized down.
        for (info in listOf("widget_favourite_categories_info", "widget_account_balances_info")) {
            assertEquals("$info cells wide", "4", attribute(info, "targetCellWidth"))
            assertEquals("$info cells tall", "3", attribute(info, "targetCellHeight"))
            assertEquals("$info width before Android 12", "250dp", attribute(info, "minWidth"))
            assertEquals("$info still shrinks", "40dp", attribute(info, "minResizeWidth"))
        }
    }

    @Test
    fun widgetsWithADefaultCanBePlacedWithoutTheSetupScreen() {
        for (info in listOf(
            "widget_favourite_categories_info", "widget_account_balances_info", "widget_scheduled_transactions_info",
            "widget_single_account_info", "widget_single_category_info", "widget_report_card_info",
        )) {
            val value = features(info)!!.split('|')
            assertTrue("$info should be placeable without configuring", "configuration_optional" in value)
            assertTrue("$info should still be reconfigurable", "reconfigurable" in value)
            assertTrue("$info keeps its configuration screen", File("src/main/res/xml/$info.xml").readText().contains("android:configure="))
        }
        // The rest only have their own look to set, which is optional too.
        for (info in listOf("widget_budget_snapshot_info", "widget_quick_transaction_info", "widget_spending_trend_info", "widget_income_spending_info")) {
            val value = features(info)!!.split('|')
            assertTrue("$info should be placeable without configuring", "configuration_optional" in value)
            assertTrue("$info should be reconfigurable for its look", "reconfigurable" in value)
            assertTrue("$info sets its look", File("src/main/res/xml/$info.xml").readText().contains("android:configure=\"com.azimulkabir.actua.widget.WidgetLookActivity\""))
        }
    }

    @Test
    fun aDefaultExistsForEverySetting() {
        // Without configuring, the schedules widget uses its 14-day period.
        assertEquals(14, com.azimulkabir.actua.data.schedules.ScheduleWidgetPeriod.FOURTEEN.days)
    }
}
