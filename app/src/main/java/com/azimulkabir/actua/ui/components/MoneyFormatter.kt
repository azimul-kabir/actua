package com.azimulkabir.actua.ui.components

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import kotlin.math.absoluteValue

object BalanceVisibility {
    @Volatile var hidden: Boolean = false
}

object CurrencyDisplay {
    @Volatile var code: String = "BDT"
    @Volatile var symbolOnly: Boolean = false
}

fun formatMoneyCents(
    cents: Long,
    hideDecimalPlaces: Boolean,
    showPositiveSign: Boolean = false,
): String {
    if (BalanceVisibility.hidden) return "••••"
    val sign = when {
        cents < 0 -> "−"
        cents > 0 && showPositiveSign -> "+"
        else -> ""
    }
    val magnitude = cents.absoluteValue
    val whole = NumberFormat.getIntegerInstance(Locale.forLanguageTag("en-BD")).format(magnitude / 100)
    val decimals = if (hideDecimalPlaces) "" else ".${(magnitude % 100).toString().padStart(2, '0')}"
    val currency = CurrencyDisplay.code
    if (currency.isBlank()) return "$sign$whole$decimals"
    val symbol = if (CurrencyDisplay.symbolOnly) narrowCurrencySymbol(currency)
    else if (currency == "BDT") "৳"
    else runCatching { Currency.getInstance(currency).getSymbol(Locale.getDefault()) }.getOrDefault(currency)
    return "$sign$symbol$whole$decimals"
}

private fun narrowCurrencySymbol(code: String): String = when (code) {
    "BDT" -> "৳"
    "USD", "CAD", "AUD", "NZD", "SGD" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "JPY", "CNY" -> "¥"
    "INR" -> "₹"
    "AED" -> "د.إ"
    "SAR" -> "ر.س"
    else -> runCatching { Currency.getInstance(code).symbol }.getOrDefault(code)
}

fun centsToInput(cents: Long): String {
    val magnitude = cents.absoluteValue
    val decimal = (magnitude % 100).toString().padStart(2, '0')
    return "${magnitude / 100}.$decimal"
}

fun parseInputCents(value: String): Long? = runCatching {
    value.trim().toBigDecimal().movePointRight(2).longValueExact()
}.getOrNull()
