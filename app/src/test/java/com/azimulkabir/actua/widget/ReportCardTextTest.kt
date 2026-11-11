package com.azimulkabir.actua.widget

import com.azimulkabir.actua.data.reports.DashboardPageRow
import com.azimulkabir.actua.data.reports.DashboardWidgetRow
import com.azimulkabir.actua.data.reports.ReportCardHeadline
import com.azimulkabir.actua.data.reports.ReportCards
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.formatMoneyCents
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportCardTextTest {
    private val labels = object : ReportCardLabels {
        override fun days(days: Long) = "$days days"
        override fun years(years: String) = "$years years"
        override val notReached = "Not reached"
        override val unavailable = "No value yet"
    }

    private val savedDecimals = CurrencyDisplay.decimalPlaces

    @After
    fun restoreCurrency() {
        CurrencyDisplay.decimalPlaces = savedDecimals
    }

    private fun money(hideDecimals: Boolean) = { cents: Long -> formatMoneyCents(cents, hideDecimals, respectBalanceVisibility = false) }

    private fun text(headline: ReportCardHeadline, hideDecimals: Boolean = false, locale: Locale = Locale.US) =
        reportCardText(headline, labels, locale, money(hideDecimals))

    @Test
    fun moneyIsFormattedAsTheBudgetsMoneyAndFollowsHiddenDecimals() {
        val shown = text(ReportCardHeadline.Money(1_234_56))
        assertEquals(formatMoneyCents(1_234_56, false, respectBalanceVisibility = false), shown.value)
        assertTrue(shown.isMoney)
        assertNull(shown.comparison)

        val whole = text(ReportCardHeadline.Money(1_234_56), hideDecimals = true)
        assertEquals(formatMoneyCents(1_234_56, true, respectBalanceVisibility = false), whole.value)
        assertFalse("hidden decimals change how money is shown", whole.value == shown.value)
    }

    @Test
    fun moneyFollowsTheBudgetsCurrencyDecimals() {
        CurrencyDisplay.decimalPlaces = 2
        val twoPlaces = text(ReportCardHeadline.Money(5_000)).value
        CurrencyDisplay.decimalPlaces = 0
        val noPlaces = text(ReportCardHeadline.Money(5_000)).value
        assertFalse("a currency without minor units shows the stored integer whole", twoPlaces == noPlaces)
        assertEquals(formatMoneyCents(5_000, false, respectBalanceVisibility = false), noPlaces)
    }

    @Test
    fun aComparisonIsMoneyToo() {
        val shown = text(ReportCardHeadline.Money(42_000, comparisonCents = 38_500), hideDecimals = true)
        assertEquals(formatMoneyCents(38_500, true, respectBalanceVisibility = false), shown.comparison)
    }

    @Test
    fun hiddenBalancesHideMoneyOnly() {
        val hide = { _: Long -> "••••" }
        val money = reportCardText(ReportCardHeadline.Money(1_000, comparisonCents = 2_000), labels, Locale.US, hide)
        assertEquals("••••", money.value)
        assertEquals("••••", money.comparison)
        assertEquals("27 days", reportCardText(ReportCardHeadline.Days(27), labels, Locale.US, hide).value)
        assertEquals("12.5 years", reportCardText(ReportCardHeadline.MonthsToCrossover(150), labels, Locale.US, hide).value)
        assertEquals("12.34%", reportCardText(ReportCardHeadline.Percent(12.34), labels, Locale.US, hide).value)
    }

    @Test
    fun daysAndYearsIgnoreCurrencyAndHiddenDecimals() {
        CurrencyDisplay.decimalPlaces = 0
        for (hideDecimals in listOf(false, true)) {
            val days = text(ReportCardHeadline.Days(27), hideDecimals)
            assertEquals("27 days", days.value)
            assertFalse(days.isMoney)
            val years = text(ReportCardHeadline.MonthsToCrossover(150), hideDecimals)
            assertEquals("12.5 years", years.value)
            assertFalse(years.isMoney)
        }
    }

    @Test
    fun crossoverIsShownInYearsAsInReports() {
        assertEquals("0.0 years", text(ReportCardHeadline.MonthsToCrossover(0)).value)
        assertEquals("1.0 years", text(ReportCardHeadline.MonthsToCrossover(12)).value)
        assertEquals("12,5 years", text(ReportCardHeadline.MonthsToCrossover(150), locale = Locale.forLanguageTag("es")).value)
        assertEquals("Not reached", text(ReportCardHeadline.MonthsToCrossover(null)).value)
    }

    @Test
    fun aPercentageHasTwoDecimalsWhateverTheMoneySettings() {
        assertEquals("12.34%", text(ReportCardHeadline.Percent(12.34), hideDecimals = true).value)
        assertEquals("-5.00%", text(ReportCardHeadline.Percent(-5.0)).value)
        assertEquals("12,34%", text(ReportCardHeadline.Percent(12.34), locale = Locale.forLanguageTag("pt-BR")).value)
        assertFalse(text(ReportCardHeadline.Percent(12.34)).isMoney)
    }

    @Test
    fun aCardWithNoValueSaysSo() {
        val shown = text(ReportCardHeadline.Unavailable)
        assertEquals("No value yet", shown.value)
        assertFalse(shown.isMoney)
    }

    private val page = listOf(DashboardPageRow("main", "Main"))

    private fun choice(rows: List<DashboardWidgetRow>, selectedId: String?) =
        resolveSingle(ReportCards.offered(page) { rows }, selectedId) { it.id }

    private val rows = listOf(
        DashboardWidgetRow("notes", "markdown-card", null),
        DashboardWidgetRow("worth", "net-worth-card", null),
        DashboardWidgetRow("summary", "summary-card", null),
    )

    @Test
    fun skippingSetupShowsTheFirstOfferedCard() {
        assertEquals("worth", (choice(rows, null) as SingleChoice.Found).item.id)
    }

    @Test
    fun theChosenCardIsShown() {
        assertEquals("summary", (choice(rows, "summary") as SingleChoice.Found).item.id)
    }

    @Test
    fun aRemovedCardIsNotFoundRatherThanReplacedByAnother() {
        assertEquals(SingleChoice.NotFound, choice(rows.filterNot { it.id == "summary" }, "summary"))
    }

    @Test
    fun aCardChangedToAnUnsupportedKindIsNotFound() {
        val changed = rows.map { if (it.id == "summary") it.copy(type = "cash-flow-card") else it }
        assertEquals(SingleChoice.NotFound, choice(changed, "summary"))
    }

    @Test
    fun aDashboardWithoutSingleValueCardsHasNothingToDefaultTo() {
        assertEquals(SingleChoice.Empty, choice(listOf(DashboardWidgetRow("notes", "markdown-card", null)), null))
    }
}
