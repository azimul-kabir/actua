package com.azimulkabir.actua.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.loans.LoanPlanner
import com.azimulkabir.actua.model.LoanStatus
import com.azimulkabir.actua.model.parsePositiveCents
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaFormTextField
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.reports.charts.LineChart
import com.azimulkabir.actua.ui.reports.charts.LineChartSeries
import com.azimulkabir.actua.ui.theme.Spacing
import java.math.BigDecimal

private fun centsText(cents: Long): String = BigDecimal(cents).movePointLeft(CurrencyDisplay.decimalPlaces).toPlainString()

/** "1 year, 3 months", "5 years" or "7 months" for a month count. */
@Composable
internal fun loanDurationText(months: Int): String {
    val (years, rest) = LoanPlanner.years(months)
    val yearsText = pluralStringResource(R.plurals.fs_interval_years, years, years)
    val monthsText = pluralStringResource(R.plurals.fs_interval_months, rest, rest)
    return when {
        years == 0 -> monthsText
        rest == 0 -> yearsText
        else -> stringResource(R.string.loans_duration_join, yearsText, monthsText)
    }
}

/**
 * Payoff simulator for a loan: a different monthly payment, a payoff target in months, or a one-off
 * extra this month, shown against the required minimum. Nothing is saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoanPlannerSheet(loan: LoanStatus, hideDecimals: Boolean, onDismiss: () -> Unit) {
    val config = loan.config
    val start = remember(loan.accountId, loan.balanceCents) { loan.projectionStart() }
    val decimals = CurrencyDisplay.decimalPlaces
    var payment by remember { mutableStateOf(centsText(config.minimumPaymentCents)) }
    var targetMonths by remember { mutableStateOf("") }
    var extra by remember { mutableStateOf("") }
    // A blank or invalid field falls back to the minimum payment / no extra, as in Actuali.
    val paymentCents = parsePositiveCents(payment, decimals) ?: config.minimumPaymentCents
    val extraCents = parsePositiveCents(extra, decimals) ?: 0L
    val plan = remember(loan.balanceCents, config, paymentCents, extraCents, start) {
        LoanPlanner.plan(config, loan.balanceCents, paymentCents, extraCents, start)
    }
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val filter: (String) -> String = { value -> value.filter { it.isDigit() || it == '.' || it == ',' } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(stringResource(R.string.loan_planner_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            ActuaFormCard {
                ActuaFormRow(
                    icon = Icons.Outlined.Payments,
                    label = stringResource(R.string.loan_planner_minimum),
                    value = formatMoneyCents(config.minimumPaymentCents, hideDecimals),
                )
                ActuaCardDivider()
                ActuaFormTextField(Icons.Outlined.Payments, stringResource(R.string.loans_monthly_payment), payment,
                    { payment = filter(it); targetMonths = "" }, keyboardOptions = decimal)
                ActuaCardDivider()
                ActuaFormTextField(Icons.Outlined.Schedule, stringResource(R.string.loan_planner_target_months), targetMonths,
                    { value ->
                        targetMonths = value.filter(Char::isDigit).take(3)
                        targetMonths.toIntOrNull()?.let { months ->
                            LoanPlanner.paymentForMonths(config, loan.balanceCents, months, start)?.let {
                                payment = centsText(it); extra = ""
                            }
                        }
                    }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                ActuaCardDivider()
                ActuaFormTextField(Icons.Outlined.Savings, stringResource(R.string.loan_planner_extra), extra,
                    { extra = filter(it); targetMonths = "" }, keyboardOptions = decimal)
            }
            Text(stringResource(R.string.loan_planner_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = Spacing.xs))
            val chosen = plan.chosen
            val minimum = plan.minimum
            if (chosen == null || minimum == null) {
                Text(stringResource(R.string.loans_payment_too_small), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                LoanBurndownChart(plan, chosen, minimum, start, hideDecimals)
                ActuaFormCard {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlannerAmount(stringResource(R.string.loan_planner_principal), plan.owedCents, hideDecimals)
                        PlannerAmount(stringResource(R.string.loan_planner_interest), chosen.totalInterest, hideDecimals)
                        PlannerAmount(stringResource(R.string.loan_planner_total), plan.totalRemainingCents ?: 0, hideDecimals)
                        HorizontalDivider()
                        Row(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.loan_planner_time_remaining), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Text(loanDurationText(chosen.paymentCount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        plan.savings?.let { savings ->
                            Text(
                                stringResource(R.string.loan_planner_savings,
                                    formatMoneyCents(savings.interestCents, hideDecimals), loanDurationText(savings.months)),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(Spacing.md))
        }
    }
}

@Composable
private fun PlannerAmount(label: String, cents: Long, hideDecimals: Boolean) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(formatMoneyCents(cents, hideDecimals), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Balance over time for the chosen plan, with the minimum-payment plan dashed alongside it. */
@Composable
private fun LoanBurndownChart(
    plan: LoanPlanner.Plan, chosen: com.azimulkabir.actua.data.loans.LoanAmortization.Schedule,
    minimum: com.azimulkabir.actua.data.loans.LoanAmortization.Schedule,
    start: com.azimulkabir.actua.data.schedules.DayDate, hideDecimals: Boolean,
) {
    val chosenPoints = LoanPlanner.balancePoints(chosen, plan.owedCents)
    val minimumPoints = LoanPlanner.balancePoints(minimum, plan.owedCents)
    val count = maxOf(chosenPoints.size, minimumPoints.size)
    // A paid-off plan stays at zero for the rest of the chart.
    fun padded(points: List<Long>) = points + List(count - points.size) { 0L }
    val series = buildList {
        if (!plan.isAtMinimum) add(LineChartSeries(
            stringResource(R.string.loan_planner_minimum_series), MaterialTheme.colorScheme.outline, padded(minimumPoints),
        ))
        add(LineChartSeries(stringResource(R.string.loan_planner_your_series), MaterialTheme.colorScheme.primary, padded(chosenPoints), fill = true))
    }
    val description = stringResource(R.string.loan_planner_chart_description, loanDurationText(chosen.paymentCount))
    LineChart(
        periods = LoanPlanner.periods(start, count),
        series = series,
        formatValue = { formatMoneyCents(it, hideDecimals) },
        formatAxis = { formatMoneyCents(it, true) },
        contentDescription = description,
        modifier = Modifier.fillMaxWidth(),
    )
}
