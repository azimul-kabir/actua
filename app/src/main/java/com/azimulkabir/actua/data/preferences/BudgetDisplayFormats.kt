package com.azimulkabir.actua.data.preferences

/**
 * Actual's synced Format and privacy preferences (`types/prefs.ts` `SyncedPrefs`), read from the
 * budget's `preferences` table and mapped to Actua's display option values. Actua only reads them.
 */
data class BudgetDisplayFormats(
    /** One of Actua's number format options, from `numberFormat` (Actual's default `comma-dot`). */
    val numberFormat: String = "1,234.56",
    /** One of Actua's date format options, from `dateFormat` (Actual's default `MM/dd/yyyy`). */
    val dateFormat: String = "MM/DD/YYYY",
    val hideFraction: Boolean = false,
    val privacyEnabled: Boolean = false,
    /** `defaultCurrencyCode`, or "" for no currency. */
    val currencyCode: String = "",
    /** `currencySymbolPosition` is `after`. */
    val symbolAfterAmount: Boolean = false,
    /** `currencySpaceBetweenAmountAndSymbol` is `true`. */
    val spaceBetweenAmountAndSymbol: Boolean = false,
) {
    companion object {
        val KEYS = listOf(
            "numberFormat", "dateFormat", "hideFraction", "isPrivacyEnabled", "defaultCurrencyCode",
            "currencySymbolPosition", "currencySpaceBetweenAmountAndSymbol",
        )

        /** Upstream readers: `parseNumberFormat`, `dateFormat || 'MM/dd/yyyy'`, `String(v) === 'true'`. */
        fun from(prefs: Map<String, String?>) = BudgetDisplayFormats(
            numberFormat = numberFormat(prefs["numberFormat"]),
            dateFormat = dateFormat(prefs["dateFormat"]),
            hideFraction = prefs["hideFraction"] == "true",
            privacyEnabled = prefs["isPrivacyEnabled"] == "true",
            currencyCode = prefs["defaultCurrencyCode"].orEmpty().trim(),
            symbolAfterAmount = prefs["currencySymbolPosition"] == "after",
            spaceBetweenAmountAndSymbol = prefs["currencySpaceBetweenAmountAndSymbol"] == "true",
        )

        /** Actual's `numberFormats` values; anything else is `comma-dot`, as `parseNumberFormat` does. */
        fun numberFormat(value: String?): String = when (value) {
            "dot-comma" -> "1.234,56"
            "space-comma" -> "1 234,56"
            "apostrophe-dot" -> "1’234.56"
            "comma-dot-in" -> "1,23,456.78"
            else -> "1,234.56"
        }

        /** Actual's Format settings `dateFormats`; unset is `MM/dd/yyyy`. */
        fun dateFormat(value: String?): String = when (value) {
            "dd/MM/yyyy" -> "DD/MM/YYYY"
            "yyyy-MM-dd" -> "YYYY-MM-DD"
            "MM.dd.yyyy" -> "MM.DD.YYYY"
            "dd.MM.yyyy" -> "DD.MM.YYYY"
            "dd-MM-yyyy" -> "DD-MM-YYYY"
            else -> "MM/DD/YYYY"
        }
    }
}

/** The formats amounts and dates are shown with: a device override where one is set, else the budget's. */
data class EffectiveDisplayFormats(
    val currencyCode: String,
    val currencySymbolOnly: Boolean,
    val symbolAfterAmount: Boolean,
    val spaceBetweenAmountAndSymbol: Boolean,
    val dateFormat: String,
    val numberFormat: String,
    val hideDecimals: Boolean,
    val hideBalances: Boolean,
) {
    companion object {
        /** The device value that defers to the budget's synced preference. */
        const val FOLLOW_BUDGET = "Same as budget"
        const val SHOW = "Show"
        const val HIDE = "Hide"

        fun resolve(
            currencyCode: String,
            currencySymbolOnly: Boolean,
            dateFormat: String,
            numberFormat: String,
            decimalPlacesMode: String,
            privacyMode: String,
            budget: BudgetDisplayFormats,
        ): EffectiveDisplayFormats {
            val followCurrency = currencyCode == FOLLOW_BUDGET
            return EffectiveDisplayFormats(
                currencyCode = if (followCurrency) budget.currencyCode else currencyCode,
                // Actual shows its own short symbol (`currencies.ts`), e.g. $ for USD.
                currencySymbolOnly = if (followCurrency) true else currencySymbolOnly,
                symbolAfterAmount = followCurrency && budget.symbolAfterAmount,
                spaceBetweenAmountAndSymbol = followCurrency && budget.spaceBetweenAmountAndSymbol,
                dateFormat = if (dateFormat == FOLLOW_BUDGET) budget.dateFormat else dateFormat,
                numberFormat = if (numberFormat == FOLLOW_BUDGET) budget.numberFormat else numberFormat,
                hideDecimals = when (decimalPlacesMode) {
                    HIDE -> true
                    SHOW -> false
                    else -> budget.hideFraction
                },
                hideBalances = when (privacyMode) {
                    HIDE -> true
                    SHOW -> false
                    else -> budget.privacyEnabled
                },
            )
        }
    }
}
