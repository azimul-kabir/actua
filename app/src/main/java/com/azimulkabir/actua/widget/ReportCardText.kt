package com.azimulkabir.actua.widget

import com.azimulkabir.actua.data.reports.ReportCardHeadline
import java.util.Locale

/**
 * A report card's headline as the widget shows it. [isMoney] says the value is an amount of money, which is
 * the only kind hidden with balances; [comparison] is the amount it is compared with, when the card has one.
 */
internal data class ReportCardText(val value: String, val comparison: String? = null, val isMoney: Boolean = false)

/** The words around a report card's number, which come from string resources in the widget. */
internal interface ReportCardLabels {
    fun days(days: Long): String
    fun years(years: String): String
    val notReached: String
    val unavailable: String
}

/**
 * Formats [headline] the way Reports shows it. Only money goes through [money], so only money follows the
 * budget's currency, hidden decimals and balance hiding; a percentage, days and years are plain numbers.
 */
internal fun reportCardText(
    headline: ReportCardHeadline,
    labels: ReportCardLabels,
    locale: Locale,
    money: (Long) -> String,
): ReportCardText = when (headline) {
    is ReportCardHeadline.Money -> ReportCardText(money(headline.cents), headline.comparisonCents?.let(money), isMoney = true)
    is ReportCardHeadline.Percent -> ReportCardText(String.format(locale, "%.2f%%", headline.value))
    is ReportCardHeadline.Days -> ReportCardText(labels.days(headline.days))
    is ReportCardHeadline.MonthsToCrossover -> ReportCardText(
        headline.months?.let { labels.years(String.format(locale, "%.1f", it / 12.0)) } ?: labels.notReached,
    )
    ReportCardHeadline.Unavailable -> ReportCardText(labels.unavailable)
}
