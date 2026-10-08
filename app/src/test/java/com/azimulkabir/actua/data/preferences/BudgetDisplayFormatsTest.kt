package com.azimulkabir.actua.data.preferences

import com.azimulkabir.actua.data.preferences.EffectiveDisplayFormats.Companion.FOLLOW_BUDGET
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetDisplayFormatsTest {
    @Test fun unsetPreferencesUseActualsDefaults() {
        val formats = BudgetDisplayFormats.from(emptyMap())
        assertEquals("1,234.56", formats.numberFormat)
        assertEquals("MM/DD/YYYY", formats.dateFormat)
        assertFalse(formats.hideFraction)
        assertFalse(formats.privacyEnabled)
        assertEquals("", formats.currencyCode)
        assertFalse(formats.symbolAfterAmount)
        assertFalse(formats.spaceBetweenAmountAndSymbol)
    }

    @Test fun mapsEveryActualNumberFormat() {
        assertEquals("1,234.56", BudgetDisplayFormats.numberFormat("comma-dot"))
        assertEquals("1.234,56", BudgetDisplayFormats.numberFormat("dot-comma"))
        assertEquals("1 234,56", BudgetDisplayFormats.numberFormat("space-comma"))
        assertEquals("1’234.56", BudgetDisplayFormats.numberFormat("apostrophe-dot"))
        assertEquals("1,23,456.78", BudgetDisplayFormats.numberFormat("comma-dot-in"))
        assertEquals("1,234.56", BudgetDisplayFormats.numberFormat("unknown"))
    }

    @Test fun mapsEveryActualDateFormat() {
        assertEquals("MM/DD/YYYY", BudgetDisplayFormats.dateFormat("MM/dd/yyyy"))
        assertEquals("DD/MM/YYYY", BudgetDisplayFormats.dateFormat("dd/MM/yyyy"))
        assertEquals("YYYY-MM-DD", BudgetDisplayFormats.dateFormat("yyyy-MM-dd"))
        assertEquals("MM.DD.YYYY", BudgetDisplayFormats.dateFormat("MM.dd.yyyy"))
        assertEquals("DD.MM.YYYY", BudgetDisplayFormats.dateFormat("dd.MM.yyyy"))
        assertEquals("DD-MM-YYYY", BudgetDisplayFormats.dateFormat("dd-MM-yyyy"))
    }

    @Test fun readsSyncedStringValues() {
        val formats = BudgetDisplayFormats.from(mapOf(
            "hideFraction" to "true", "isPrivacyEnabled" to "true", "defaultCurrencyCode" to "EUR",
            "currencySymbolPosition" to "after", "currencySpaceBetweenAmountAndSymbol" to "true",
        ))
        assertTrue(formats.hideFraction)
        assertTrue(formats.privacyEnabled)
        assertEquals("EUR", formats.currencyCode)
        assertTrue(formats.symbolAfterAmount)
        assertTrue(formats.spaceBetweenAmountAndSymbol)
    }

    @Test fun sameAsBudgetUsesTheBudgetsPreferences() {
        val budget = BudgetDisplayFormats(
            numberFormat = "1.234,56", dateFormat = "DD.MM.YYYY", hideFraction = true, privacyEnabled = true,
            currencyCode = "EUR", symbolAfterAmount = true, spaceBetweenAmountAndSymbol = true,
        )
        val formats = EffectiveDisplayFormats.resolve(
            FOLLOW_BUDGET, false, FOLLOW_BUDGET, FOLLOW_BUDGET, FOLLOW_BUDGET, FOLLOW_BUDGET, budget,
        )
        assertEquals("EUR", formats.currencyCode)
        assertTrue(formats.currencySymbolOnly)
        assertTrue(formats.symbolAfterAmount)
        assertTrue(formats.spaceBetweenAmountAndSymbol)
        assertEquals("DD.MM.YYYY", formats.dateFormat)
        assertEquals("1.234,56", formats.numberFormat)
        assertTrue(formats.hideDecimals)
        assertTrue(formats.hideBalances)
    }

    @Test fun deviceChoicesOverrideTheBudget() {
        val budget = BudgetDisplayFormats(
            numberFormat = "1.234,56", dateFormat = "DD.MM.YYYY", hideFraction = true, privacyEnabled = true,
            currencyCode = "EUR", symbolAfterAmount = true, spaceBetweenAmountAndSymbol = true,
        )
        val formats = EffectiveDisplayFormats.resolve(
            "USD", false, "YYYY-MM-DD", "1,234.56", EffectiveDisplayFormats.SHOW, EffectiveDisplayFormats.SHOW, budget,
        )
        assertEquals("USD", formats.currencyCode)
        assertFalse(formats.currencySymbolOnly)
        assertFalse(formats.symbolAfterAmount)
        assertFalse(formats.spaceBetweenAmountAndSymbol)
        assertEquals("YYYY-MM-DD", formats.dateFormat)
        assertEquals("1,234.56", formats.numberFormat)
        assertFalse(formats.hideDecimals)
        assertFalse(formats.hideBalances)
    }
}
