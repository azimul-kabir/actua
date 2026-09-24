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

object NumberDisplay {
    @Volatile var format: String = "System default"
}

fun formatMoneyCents(
    cents: Long,
    hideDecimalPlaces: Boolean,
    showPositiveSign: Boolean = false,
    respectBalanceVisibility: Boolean = true,
): String {
    if (respectBalanceVisibility && BalanceVisibility.hidden) return "••••"
    val sign = when {
        cents < 0 -> "−"
        cents > 0 && showPositiveSign -> "+"
        else -> ""
    }
    val magnitude = cents.absoluteValue
    val whole = formatWholeNumber(magnitude / 100, NumberDisplay.format)
    val decimalSeparator = when (NumberDisplay.format) {
        "1.234,56", "1 234,56" -> ","
        else -> "."
    }
    val decimals = if (hideDecimalPlaces) "" else "$decimalSeparator${(magnitude % 100).toString().padStart(2, '0')}"
    return "$sign${currencyInputPrefix()}$whole$decimals"
}

// NumberFormat construction does a locale resource lookup and isn't cheap; this path is hit
// once or more per budget/transaction row, per recomposition, so a fresh instance per call adds
// up while scrolling. Cached per-thread (NumberFormat instances aren't thread-safe to share)
// rather than per-call.
private val integerFormatCache = ThreadLocal.withInitial { mutableMapOf<Locale, NumberFormat>() }

internal fun formatWholeNumber(value: Long, format: String, locale: Locale = Locale.getDefault()): String = when (format) {
    "1,234.56" -> grouped(value, 3, ",")
    "1.234,56" -> grouped(value, 3, ".")
    "1 234,56" -> grouped(value, 3, " ")
    "1234.56" -> value.toString()
    "1,23,456.78" -> grouped(value, 3, ",", secondarySize = 2)
    else -> integerFormatCache.get().getOrPut(locale) { NumberFormat.getIntegerInstance(locale) }.format(value)
}

private fun grouped(value: Long, primarySize: Int, separator: String, secondarySize: Int = primarySize): String {
    val digits = value.toString()
    if (digits.length <= primarySize) return digits
    val tail = digits.takeLast(primarySize)
    val head = digits.dropLast(primarySize)
    val groups = mutableListOf<String>()
    var end = head.length
    while (end > 0) {
        val start = (end - secondarySize).coerceAtLeast(0)
        groups += head.substring(start, end)
        end = start
    }
    return groups.asReversed().plus(tail).joinToString(separator)
}

/** Currency prefix used by editable amount fields so they match the selected display currency. */
fun currencyInputPrefix(): String {
    val currency = CurrencyDisplay.code
    if (currency.isBlank()) return ""
    return if (CurrencyDisplay.symbolOnly) narrowCurrencySymbol(currency)
    else if (currency == "BDT") "৳"
    else runCatching { Currency.getInstance(currency).getSymbol(Locale.getDefault()) }.getOrDefault(currency)
}

private fun narrowCurrencySymbol(code: String): String = when (code) {
    "BDT" -> "৳"
    "USD", "CAD", "AUD", "NZD", "SGD", "MXN" -> "$"
    "EUR" -> "€"
    "GBP" -> "£"
    "JPY", "CNY" -> "¥"
    "INR" -> "₹"
    "AED" -> "د.إ"
    "SAR" -> "ر.س"
    "ARS" -> "Arg$"
    "BRL" -> "R$"
    "BYN" -> "Br"
    "CHF" -> "Fr."
    "CLP" -> "CLP$"
    "COP" -> "Col$"
    "CRC" -> "₡"
    "CZK" -> "Kč"
    "DKK", "SEK" -> "kr"
    "DOP" -> "RD$"
    "EGP" -> "ج.م"
    "GTQ" -> "Q"
    "HKD" -> "HK$"
    "HUF" -> "Ft"
    "IDR" -> "Rp"
    "ILS" -> "₪"
    "IRR" -> "﷼"
    "JMD" -> "J$"
    "KRW" -> "₩"
    "LKR", "PKR" -> "Rs."
    "MDL" -> "L"
    "MKD" -> "ден"
    "MYR" -> "RM"
    "PEN" -> "S/"
    "PHP" -> "₱"
    "PLN" -> "zł"
    "QAR" -> "ر.ق"
    "RON" -> "lei"
    "RSD" -> "дин"
    "RUB" -> "₽"
    "THB" -> "฿"
    "TRY" -> "₺"
    "TWD" -> "NT$"
    "UAH" -> "₴"
    "UYU" -> "\$U"
    "UZS" -> "UZS"
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
