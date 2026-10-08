package com.azimulkabir.actua.data.preferences

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DisplayPreferencesTest {
    @Before fun clearPreferences() {
        ApplicationProvider.getApplicationContext<Context>()
            .getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test fun legacyMoreStartPageMigratesToManage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
            .edit().putString("start_page", "More").commit()

        val preferences = DisplayPreferences(context)
        assertEquals("Manage", preferences.startPage)
        assertEquals(
            "Manage",
            context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
                .getString("start_page", null),
        )

        preferences.startPage = "More"
        assertEquals("Manage", preferences.startPage)
    }

    @Test fun formattingDefaultsAndSelectionsPersist() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Same as budget", DisplayPreferences(context).dateFormat)
        assertEquals("Same as budget", DisplayPreferences(context).numberFormat)
        assertEquals("Same as budget", DisplayPreferences(context).currencyCode)
        assertEquals("Same as budget", DisplayPreferences(context).decimalPlacesMode)
        assertEquals("Same as budget", DisplayPreferences(context).privacyMode)

        DisplayPreferences(context).dateFormat = "DD/MM/YYYY"
        DisplayPreferences(context).numberFormat = "1,23,456.78"

        val restored = DisplayPreferences(context)
        assertEquals("DD/MM/YYYY", restored.dateFormat)
        assertEquals("1,23,456.78", restored.numberFormat)
    }

    @Test fun legacyDecimalAndBalanceTogglesKeepTheirChoice() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE).edit()
            .putBoolean("hide_decimal_places", true).putBoolean("hide_balances", false).commit()

        val preferences = DisplayPreferences(context)
        assertEquals("Hide", preferences.decimalPlacesMode)
        assertEquals("Show", preferences.privacyMode)

        preferences.decimalPlacesMode = "Same as budget"
        assertEquals("Same as budget", DisplayPreferences(context).decimalPlacesMode)
    }

    @Test fun explicitNoCurrencyIsKept() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DisplayPreferences(context).currencyCode = ""
        assertEquals("", DisplayPreferences(context).currencyCode)
    }

    @Test fun reportSummaryDefaultsOffAndPersists() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(false, DisplayPreferences(context).showReportSummary)

        DisplayPreferences(context).showReportSummary = true

        assertEquals(true, DisplayPreferences(context).showReportSummary)
    }

    @Test fun budgetAndEntryDefaultsMatchAppDefaults() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = DisplayPreferences(context)

        assertEquals("Table", preferences.budgetView)
        assertEquals(true, preferences.showSpentColumn)
        assertEquals(true, preferences.showGroupTotals)
        assertEquals(false, preferences.conventionalAmountEntry)
    }

    @Test fun categoryStatusDotsDefaultOnAndPersistWhenDisabled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals(true, DisplayPreferences(context).showCategoryStatusDots)

        DisplayPreferences(context).showCategoryStatusDots = false

        assertEquals(false, DisplayPreferences(context).showCategoryStatusDots)
    }

    @Test fun legacyReportsStartPageMigratesToHome() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
            .edit().putString("start_page", "Reports").commit()

        val preferences = DisplayPreferences(context)
        assertEquals("Home", preferences.startPage)
        assertEquals(
            "Home",
            context.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
                .getString("start_page", null),
        )

        preferences.startPage = "Reports"
        assertEquals("Home", preferences.startPage)
    }
}
