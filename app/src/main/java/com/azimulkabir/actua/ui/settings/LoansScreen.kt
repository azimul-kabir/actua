package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material.icons.outlined.RequestQuote
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.LoanConfig
import com.azimulkabir.actua.model.LoanStatus
import com.azimulkabir.actua.model.loanCandidates
import com.azimulkabir.actua.model.parsePositiveCents
import com.azimulkabir.actua.model.parseRatePercent
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaFormTextField
import com.azimulkabir.actua.ui.components.ActuaMenuRow
import com.azimulkabir.actua.ui.components.ActuaPrimaryActionBar
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.formatDate
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.Spacing
import java.math.BigDecimal
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun LoansScreen(
    loans: List<LoanStatus>,
    accounts: List<Account>,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onSave: (String, LoanConfig) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<LoanStatus?>(null) }
    var adding by remember { mutableStateOf(false) }
    val candidates = loanCandidates(accounts, loans.mapTo(mutableSetOf()) { it.accountId })

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.loans_title), onBack = onBack) {
            IconButton(onClick = { adding = true }, enabled = candidates.isNotEmpty()) {
                Icon(Icons.Outlined.Add, stringResource(R.string.loans_add))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (loans.isEmpty()) item {
                Text(stringResource(R.string.loans_empty),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp))
            }
            if (candidates.isEmpty()) item {
                Text(stringResource(R.string.loans_off_budget_hint),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp))
            }
            items(loans, key = { it.accountId }) { loan ->
                LoanRow(loan, hideDecimalPlaces, Modifier.padding(horizontal = 16.dp).clickable { editing = loan })
            }
        }
    }

    if (adding) LoanEditorSheet(null, candidates, onDismiss = { adding = false }, onSave = { id, config ->
        onSave(id, config); adding = false
    })
    editing?.let { loan ->
        LoanEditorSheet(loan, accounts, onDismiss = { editing = null }, onSave = { id, config ->
            onSave(id, config); editing = null
        }, onRemove = { onRemove(loan.accountId); editing = null })
    }
}

/** Whole-or-one-decimal percent for a 0..1000 permille value, in the user's locale ("12.5%"). */
internal fun paidOffPercentText(permille: Int): String = NumberFormat.getPercentInstance().apply {
    maximumFractionDigits = 1
}.format(permille / 1000.0)

/** "Mar 2029" in the user's locale. */
internal fun payoffMonthText(month: DayDate): String =
    java.time.YearMonth.of(month.year, month.month).format(
        java.time.format.DateTimeFormatter.ofPattern("LLL yyyy", java.util.Locale.getDefault()),
    )

@Composable
private fun LoanRow(loan: LoanStatus, hideDecimals: Boolean, modifier: Modifier = Modifier) {
    val balance = formatMoneyCents(loan.balanceCents, hideDecimals)
    val percent = if (loan.isPaidOff) stringResource(R.string.loans_paid_off)
        else stringResource(R.string.loans_paid_off_percent, paidOffPercentText(loan.paidOffPermille))
    val payment = formatMoneyCents(loan.config.minimumPaymentCents, hideDecimals)
    val payoff = when {
        loan.isPaidOff -> null
        else -> loan.payoffMonth()?.let { stringResource(R.string.loans_payoff_month, payoffMonthText(it)) }
            ?: stringResource(R.string.loans_payment_too_small)
    }
    val description = stringResource(R.string.loans_row_description, loan.accountName, balance, percent, payment) +
        (payoff?.let { ", $it" }.orEmpty())
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(
            Modifier.padding(12.dp).clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(Modifier.fillMaxWidth()) {
                Text(loan.accountName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(balance, fontWeight = FontWeight.SemiBold)
            }
            LinearProgressIndicator(progress = { loan.paidOffPermille / 1000f }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth()) {
                Text(percent, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.loans_monthly_payment_value, payment),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            payoff?.let {
                Text(it, style = MaterialTheme.typography.bodySmall,
                    color = if (loan.payoffMonth() == null) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun centsText(cents: Long?): String =
    cents?.let { BigDecimal(it).movePointLeft(CurrencyDisplay.decimalPlaces).toPlainString() }.orEmpty()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoanEditorSheet(
    loan: LoanStatus?, accounts: List<Account>, onDismiss: () -> Unit,
    onSave: (String, LoanConfig) -> Unit, onRemove: (() -> Unit)? = null,
) {
    val existing = loan?.config
    var accountId by remember { mutableStateOf(loan?.accountId ?: accounts.firstOrNull()?.id.orEmpty()) }
    var original by remember { mutableStateOf(centsText(existing?.originalBalanceCents)) }
    var rate by remember { mutableStateOf(existing?.annualRatePercent?.let { BigDecimal.valueOf(it).stripTrailingZeros().toPlainString() }.orEmpty()) }
    var payment by remember { mutableStateOf(centsText(existing?.minimumPaymentCents)) }
    var escrow by remember { mutableStateOf(centsText(existing?.escrowOrFeesCents)) }
    var term by remember { mutableStateOf(existing?.termMonths?.toString().orEmpty()) }
    var paymentDay by remember { mutableStateOf(existing?.paymentDay?.toString().orEmpty()) }
    var startDate by remember { mutableStateOf(existing?.startDate?.let(DayDate::fromYyyymmdd)) }
    var pickingDate by remember { mutableStateOf(false) }
    val termValue = term.toIntOrNull()?.takeIf { it in 1..LoanConfig.MAX_TERM_MONTHS }
    val dayValue = paymentDay.toIntOrNull()?.takeIf { it in 1..31 }
    val account = accounts.firstOrNull { it.id == accountId }
    val decimals = CurrencyDisplay.decimalPlaces
    val originalCents = parsePositiveCents(original, decimals)
    val paymentCents = parsePositiveCents(payment, decimals)
    val escrowCents = parsePositiveCents(escrow, decimals)
    // A blank rate means an interest-free loan; anything typed must parse.
    val rateValue = if (rate.isBlank()) 0.0 else parseRatePercent(rate)
    val valid = accountId.isNotBlank() && originalCents != null && paymentCents != null &&
        rateValue != null && (escrow.isBlank() || escrowCents != null) &&
        (term.isBlank() || termValue != null) && (paymentDay.isBlank() || dayValue != null)
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    if (pickingDate) {
        val picker = rememberDatePickerState(
            initialSelectedDateMillis = (startDate ?: DayDate.today()).let { LocalDate.of(it.year, it.month, it.day) }
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    picker.selectedDateMillis?.let { millis ->
                        startDate = DayDate.from(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    pickingDate = false
                }) { Text(stringResource(R.string.fs_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { startDate = null; pickingDate = false }) { Text(stringResource(R.string.loans_clear_date)) }
            },
        ) { DatePicker(picker) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(stringResource(if (loan == null) R.string.loans_add_title else R.string.loans_edit_title),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ActuaFormCard {
                    if (loan == null) {
                        ActuaMenuRow(
                            icon = Icons.Outlined.AccountBalance,
                            label = stringResource(R.string.fs_account),
                            value = account?.name ?: stringResource(R.string.fs_select_account),
                            valueIsPlaceholder = account == null,
                            choices = accounts.map { it.name to it.id },
                        ) { accountId = it }
                    } else {
                        ActuaFormRow(icon = Icons.Outlined.AccountBalance, label = stringResource(R.string.fs_account), value = loan.accountName)
                    }
                    ActuaCardDivider()
                    ActuaFormTextField(Icons.Outlined.RequestQuote, stringResource(R.string.loans_original_balance), original,
                        { value -> original = value.filter { it.isDigit() || it == '.' || it == ',' } }, keyboardOptions = decimal)
                    ActuaCardDivider()
                    ActuaFormTextField(Icons.Outlined.Percent, stringResource(R.string.loans_interest_rate), rate,
                        { value -> rate = value.filter { it.isDigit() || it == '.' || it == ',' } }, keyboardOptions = decimal)
                    ActuaCardDivider()
                    ActuaFormTextField(Icons.Outlined.Payments, stringResource(R.string.loans_monthly_payment), payment,
                        { value -> payment = value.filter { it.isDigit() || it == '.' || it == ',' } }, keyboardOptions = decimal)
                    ActuaCardDivider()
                    ActuaFormTextField(Icons.Outlined.Receipt, stringResource(R.string.loans_escrow_optional), escrow,
                        { value -> escrow = value.filter { it.isDigit() || it == '.' || it == ',' } }, keyboardOptions = decimal)
                    ActuaCardDivider()
                    ActuaFormTextField(Icons.Outlined.Schedule, stringResource(R.string.loans_term_months), term,
                        { term = it.filter(Char::isDigit).take(3) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    ActuaCardDivider()
                    ActuaFormTextField(Icons.Outlined.Event, stringResource(R.string.loans_payment_day), paymentDay,
                        { paymentDay = it.filter(Char::isDigit).take(2) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.CalendarMonth,
                        label = stringResource(R.string.loans_start_date),
                        value = startDate?.let { formatDate(LocalDate.of(it.year, it.month, it.day)) }
                            ?: stringResource(R.string.loans_not_set),
                        valueIsPlaceholder = startDate == null,
                        onClick = { pickingDate = true },
                    )
                }
                Text(stringResource(R.string.loans_details_explanation),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs))
                if (onRemove != null) TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.loans_remove_tracking), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fs_cancel)) }
            }
            ActuaPrimaryActionBar(
                text = stringResource(R.string.fs_save),
                enabled = valid,
                onClick = {
                    // Pairing and snooze aren't editable here, so an edit carries them through.
                    onSave(accountId, LoanConfig(
                        originalBalanceCents = originalCents!!,
                        annualRatePercent = rateValue!!,
                        minimumPaymentCents = paymentCents!!,
                        escrowOrFeesCents = escrowCents,
                        categoryId = existing?.categoryId,
                        targetSnoozedMonth = existing?.targetSnoozedMonth,
                        termMonths = termValue,
                        paymentDay = dayValue,
                        startDate = startDate?.yyyymmdd,
                    ))
                },
                icon = Icons.Outlined.Check,
            )
        }
    }
}
