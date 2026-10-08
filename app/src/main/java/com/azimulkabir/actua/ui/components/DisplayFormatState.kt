package com.azimulkabir.actua.ui.components

import com.azimulkabir.actua.data.preferences.EffectiveDisplayFormats

/**
 * Points the app-wide amount and date formatters at [formats]. Masking ([BalanceVisibility]) is left
 * to the app's UI, so widgets and notifications can format without unmasking it.
 */
fun applyDisplayFormats(formats: EffectiveDisplayFormats) {
    CurrencyDisplay.code = formats.currencyCode
    CurrencyDisplay.symbolOnly = formats.currencySymbolOnly
    CurrencyDisplay.symbolAfterAmount = formats.symbolAfterAmount
    CurrencyDisplay.spaceBetweenAmountAndSymbol = formats.spaceBetweenAmountAndSymbol
    DateDisplay.format = formats.dateFormat
    NumberDisplay.format = formats.numberFormat
}
