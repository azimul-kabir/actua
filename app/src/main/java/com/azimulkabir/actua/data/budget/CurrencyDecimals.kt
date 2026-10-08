package com.azimulkabir.actua.data.budget

import java.util.Locale

/**
 * Decimal places of a budget's synced `defaultCurrencyCode`, from Actual's `shared/currencies.ts`:
 * IRR, JPY and KRW have none, every other currency (and no currency) has 2. Actual stores amounts
 * as integers in these minor units, so a JPY budget stores ¥1,000 as 1000, not 100000.
 */
object CurrencyDecimals {
    private val zeroDecimalCurrencies = setOf("IRR", "JPY", "KRW")

    fun of(currencyCode: String?): Int =
        if (currencyCode?.trim()?.uppercase(Locale.ROOT) in zeroDecimalCurrencies) 0 else 2
}
