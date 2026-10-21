package com.azimulkabir.actua.ui.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Percent
import androidx.compose.material.icons.outlined.Receipt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.LoanStatus
import com.azimulkabir.actua.model.paymentSplit
import com.azimulkabir.actua.model.parseNonNegativeCents
import com.azimulkabir.actua.model.parsePositiveCents
import com.azimulkabir.actua.model.suggestedPaymentCents
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaFormTextField
import com.azimulkabir.actua.ui.components.ActuaMenuRow
import com.azimulkabir.actua.ui.components.ActuaPrimaryActionBar
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.Spacing
import java.math.BigDecimal

/** A loan payment to record; the date is the day it was entered. */
data class LoanPaymentRequest(
    val fromAccountId: String,
    val paymentCents: Long,
    val interestCents: Long,
    val escrowCents: Long,
    val date: Int,
)

private fun centsText(cents: Long): String = BigDecimal(cents).movePointLeft(CurrencyDisplay.decimalPlaces).toPlainString()

/** Open, on-budget accounts a loan payment can come from, in name order. */
internal fun loanFundingAccounts(accounts: List<Account>, loanAccountId: String): List<Account> =
    accounts.filter { !it.closed && !it.offBudget && it.id != loanAccountId }.sortedBy { it.name.lowercase(java.util.Locale.ROOT) }

/**
 * Records a payment: the payment itself, with the interest and escrow the lender charged. Interest
 * and escrow start as estimates from the balance and rate and can be corrected; principal follows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LoanPaymentSheet(
    loan: LoanStatus,
    fundingAccounts: List<Account>,
    hideDecimals: Boolean,
    onDismiss: () -> Unit,
    onRecord: (LoanPaymentRequest) -> Unit,
) {
    val config = loan.config
    val suggested = config.suggestedPaymentCents(loan.balanceCents)
    val estimate = config.paymentSplit(loan.balanceCents, suggested)
    var fromId by remember { mutableStateOf(fundingAccounts.firstOrNull()?.id.orEmpty()) }
    var payment by remember { mutableStateOf(centsText(suggested)) }
    var interest by remember { mutableStateOf(centsText(estimate.interestCents)) }
    var escrow by remember { mutableStateOf(centsText(estimate.escrowCents)) }
    val decimals = CurrencyDisplay.decimalPlaces
    val paymentCents = parsePositiveCents(payment, decimals)
    val interestCents = parseNonNegativeCents(interest, decimals)
    val escrowCents = if (config.escrowOrFeesCents == null) 0L else parseNonNegativeCents(escrow, decimals)
    val principal = if (paymentCents != null && interestCents != null && escrowCents != null) paymentCents - interestCents - escrowCents else null
    val valid = fromId.isNotBlank() && paymentCents != null && interestCents != null && escrowCents != null
    val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    val filter: (String) -> String = { value -> value.filter { it.isDigit() || it == '.' || it == ',' } }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(stringResource(R.string.loan_payment_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ActuaFormCard {
                    ActuaMenuRow(
                        icon = Icons.Outlined.AccountBalance,
                        label = stringResource(R.string.loan_payment_from),
                        value = fundingAccounts.firstOrNull { it.id == fromId }?.name ?: stringResource(R.string.fs_select_account),
                        valueIsPlaceholder = fromId.isBlank(),
                        choices = fundingAccounts.map { it.name to it.id },
                    ) { fromId = it }
                    ActuaCardDivider()
                    ActuaFormTextField(Icons.Outlined.Payments, stringResource(R.string.loan_payment_amount), payment,
                        { payment = filter(it) }, keyboardOptions = decimal)
                }
                ActuaFormCard {
                    ActuaFormTextField(Icons.Outlined.Percent, stringResource(R.string.loan_payment_interest), interest,
                        { interest = filter(it) }, keyboardOptions = decimal)
                    if (config.escrowOrFeesCents != null) {
                        ActuaCardDivider()
                        ActuaFormTextField(Icons.Outlined.Receipt, stringResource(R.string.loan_payment_escrow), escrow,
                            { escrow = filter(it) }, keyboardOptions = decimal)
                    }
                    ActuaCardDivider()
                    ActuaFormRow(
                        icon = Icons.Outlined.Payments,
                        label = stringResource(R.string.loan_payment_principal),
                        value = principal?.let { formatMoneyCents(it, hideDecimals) },
                    )
                }
                Text(
                    stringResource(if (principal != null && principal <= 0) R.string.loan_payment_grows_note else R.string.loan_payment_estimate_note),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
                if (fundingAccounts.isEmpty()) Text(
                    stringResource(R.string.loan_payment_no_funding),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = Spacing.xs),
                )
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fs_cancel)) }
            }
            ActuaPrimaryActionBar(
                text = stringResource(R.string.loan_payment_record),
                enabled = valid,
                onClick = {
                    onRecord(LoanPaymentRequest(fromId, paymentCents!!, interestCents!!, escrowCents!!, DayDate.today().yyyymmdd))
                },
                icon = Icons.Outlined.Check,
            )
        }
    }
}
