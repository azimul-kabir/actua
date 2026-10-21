package com.azimulkabir.actua.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.azimulkabir.actua.R
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.CreditCardCycle
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.ui.components.ActuaCardDivider
import com.azimulkabir.actua.ui.components.ActuaFormCard
import com.azimulkabir.actua.ui.components.ActuaFormRow
import com.azimulkabir.actua.ui.components.ActuaFormTextField
import com.azimulkabir.actua.ui.components.ActuaGroupLabel
import com.azimulkabir.actua.ui.components.ActuaMenuRow
import com.azimulkabir.actua.ui.components.ActuaPrimaryActionBar
import com.azimulkabir.actua.ui.components.ActuaScreenHeader
import com.azimulkabir.actua.ui.components.CurrencyDisplay
import com.azimulkabir.actua.ui.components.formatDate
import com.azimulkabir.actua.ui.components.formatMoneyCents
import com.azimulkabir.actua.ui.theme.Spacing
import com.azimulkabir.actua.ui.theme.success
import com.azimulkabir.actua.ui.theme.warning
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.Locale

@Composable
fun CreditCardsScreen(
    cards: List<CreditCardStatus>,
    accounts: List<Account>,
    hideDecimalPlaces: Boolean,
    onBack: () -> Unit,
    onSave: (String, Int, CreditCardCycle.PaymentDue, Long?) -> Unit,
    onRemove: (String) -> Unit,
    notificationsEnabled: Boolean = false,
    onNotificationsEnabledChange: (Boolean) -> Unit = {},
    onViewStatements: (CreditCardStatus) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<CreditCardStatus?>(null) }
    var adding by remember { mutableStateOf(false) }
    val availableAccounts = creditCardCandidates(accounts, cards.mapTo(mutableSetOf()) { it.accountId })

    Column(modifier.fillMaxSize()) {
        ActuaScreenHeader(title = stringResource(R.string.fs_credit_cards), onBack = onBack) {
            IconButton(onClick = { adding = true }, enabled = availableAccounts.isNotEmpty()) {
                Icon(Icons.Outlined.Add, stringResource(R.string.fs_add_credit_card))
            }
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                ActuaFormCard(Modifier.padding(horizontal = Spacing.screenHorizontal, vertical = Spacing.sm)) {
                    ActuaFormRow(
                        icon = Icons.Outlined.NotificationsActive,
                        label = stringResource(R.string.fs_payment_reminders),
                        value = null,
                        caption = stringResource(R.string.fs_payment_reminders_description),
                        checked = notificationsEnabled,
                        onClick = { onNotificationsEnabledChange(!notificationsEnabled) },
                    )
                }
            }
            if (cards.isEmpty()) item {
                Text(stringResource(R.string.fs_no_credit_cards),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp))
            }
            items(cards, key = { it.accountId }) { card ->
                CreditCardRow(card, hideDecimalPlaces,
                    Modifier.padding(horizontal = 16.dp).clickable { editing = card },
                    onViewStatements = { onViewStatements(card) })
            }
        }
    }

    if (adding) CardEditorSheet(null, availableAccounts, onDismiss = { adding = false }, onSave = { id, day, paymentDue, limit ->
        onSave(id, day, paymentDue, limit); adding = false
    })
    editing?.let { card -> CardEditorSheet(card, accounts, onDismiss = { editing = null }, onSave = { id, day, paymentDue, limit ->
        onSave(id, day, paymentDue, limit); editing = null
    }, onRemove = { onRemove(card.accountId); editing = null }) }
}

/**
 * Open accounts that can be marked as a card: credit-type accounts first, then by name,
 * with an id tie-break so the pre-selected first entry is stable (as in Actuali).
 */
internal fun creditCardCandidates(accounts: List<Account>, configuredIds: Set<String>): List<Account> =
    accounts.filter { !it.closed && it.id !in configuredIds }.sortedWith(compareBy(
        { if (it.type.equals("credit", ignoreCase = true)) 0 else 1 },
        { it.name.lowercase(Locale.ROOT) },
        { it.id },
    ))

@Composable
private fun CreditCardRow(
    card: CreditCardStatus, hideDecimals: Boolean, modifier: Modifier = Modifier,
    onViewStatements: () -> Unit = {},
) {
    val statementDue = card.pendingStatementDue()
    val isPaid = statementDue?.isPaid == true
    val days = card.cycle.daysUntilDue(dueDate = statementDue?.dueDate)
    val urgency = when {
        isPaid -> MaterialTheme.colorScheme.success
        days <= 3 -> MaterialTheme.colorScheme.error
        days <= 7 -> MaterialTheme.colorScheme.warning
        else -> Color(0xFFF9A825)
    }
    // The pill's short text and its long form for TalkBack, which keeps the date the pill drops.
    val pillText = duePillText(statementDue, isPaid, card.cycle, days, hideDecimals, short = true)
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(vertical = 0.dp).align(Alignment.CenterVertically)) {
                Surface(color = urgency, modifier = Modifier.padding(0.dp)) { Box(Modifier.padding(horizontal = 2.dp, vertical = 34.dp)) }
            }
            // One spoken element per card, worded like Actuali's row; the long-form due summary
            // keeps the date the pill drops. The statements button stays its own element.
            val description = creditCardRowDescription(
                card.accountName,
                formatMoneyCents(card.balanceCents, hideDecimals),
                formatMoneyCents(card.cycleSpendCents, hideDecimals),
                duePillText(statementDue, isPaid, card.cycle, days, hideDecimals, short = false),
                card.availableCreditCents?.let { formatMoneyCents(it, hideDecimals) },
                stringResource(R.string.fs_row_balance_label),
                stringResource(R.string.fs_row_cycle_spend_label),
                stringResource(R.string.fs_row_available_credit_label),
            )
            Column(
                Modifier.padding(12.dp).weight(1f).clearAndSetSemantics { contentDescription = description },
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(card.accountName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(formatMoneyCents(card.balanceCents, hideDecimals), fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        pluralStringResource(
                            R.plurals.fs_card_spend_days_left,
                            card.cycle.daysRemainingInCycle(),
                            formatMoneyCents(card.cycleSpendCents, hideDecimals),
                            card.cycle.daysRemainingInCycle(),
                        ),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(pillText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp))
                }
                card.availableCreditCents?.let {
                    Text(
                        stringResource(R.string.fs_available_credit, formatMoneyCents(it, hideDecimals)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            IconButton(onClick = onViewStatements) {
                Icon(Icons.Outlined.History, stringResource(R.string.fs_view_recent_statements))
            }
        }
    }
}

/** TalkBack description for a card row: "Visa, balance -$120.00, cycle spend $80.00, Due … (9d)". */
internal fun creditCardRowDescription(
    name: String, balance: String, cycleSpend: String, dueSummary: String, availableCredit: String?,
    balanceLabel: String, cycleSpendLabel: String, availableCreditLabel: String,
): String = buildString {
    append("$name, $balanceLabel $balance, $cycleSpendLabel $cycleSpend, $dueSummary")
    availableCredit?.let { append(", $availableCreditLabel $it") }
}

/** The row's due pill: "Paid", "$342.18 · Due in 27d", or the summary alone once settled. */
@Composable
private fun duePillText(
    statementDue: CreditCardCycle.StatementDue?,
    isPaid: Boolean,
    cycle: CreditCardCycle,
    days: Int,
    hideDecimals: Boolean,
    short: Boolean,
): String {
    if (isPaid) return stringResource(R.string.fs_status_paid)
    val summary = if (short) dueShortLabel(cycle, days, statementDue?.dueDate)
        else dueLongLabel(cycle, days, statementDue?.dueDate)
    return statementDue?.let {
        CreditCardCycle.duePillText(formatMoneyCents(it.remainingDue, hideDecimals), summary, it.remainingDue)
    } ?: summary
}

/** Localized "Due in Nd" short summary for the cards row (long form for a day or less). */
@Composable
private fun dueShortLabel(cycle: CreditCardCycle, days: Int, dueDate: DayDate?): String =
    if (days <= 1) dueLongLabel(cycle, days, dueDate)
    else stringResource(R.string.fs_due_in_days_short, days)

/** Localized "Due today"/"Due tomorrow"/"Due <date> (Nd)" summary in the user's date format. */
@Composable
private fun dueLongLabel(cycle: CreditCardCycle, days: Int, dueDate: DayDate?): String = when (days) {
    0 -> stringResource(R.string.fs_due_today)
    1 -> stringResource(R.string.fs_due_tomorrow)
    else -> {
        val due = dueDate ?: cycle.upcomingDueDate()
        stringResource(
            R.string.fs_due_on_date_days,
            formatDate(LocalDate.of(due.year, due.month, due.day)),
            days,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardEditorSheet(
    card: CreditCardStatus?, accounts: List<Account>, onDismiss: () -> Unit,
    onSave: (String, Int, CreditCardCycle.PaymentDue, Long?) -> Unit, onRemove: (() -> Unit)? = null,
) {
    var accountId by remember { mutableStateOf(card?.accountId ?: accounts.firstOrNull()?.id.orEmpty()) }
    var day by remember { mutableStateOf((card?.config?.statementDay ?: 15).toString()) }
    var offset by remember { mutableStateOf((card?.config?.dueOffsetDays ?: CreditCardCycle.DEFAULT_DUE_OFFSET_DAYS).toString()) }
    var useFixedDueDay by remember { mutableStateOf(card?.config?.dueDay != null) }
    var dueDay by remember { mutableStateOf((card?.config?.dueDay ?: 1).toString()) }
    var limit by remember { mutableStateOf(card?.config?.limitCents?.let { BigDecimal(it).movePointLeft(CurrencyDisplay.decimalPlaces).toPlainString() }.orEmpty()) }
    val account = accounts.firstOrNull { it.id == accountId }
    val validDay = day.toIntOrNull()?.takeIf { it in 1..31 }
    val validOffset = offset.toIntOrNull()?.takeIf { it in 1..CreditCardCycle.MAX_DUE_OFFSET_DAYS }
    val validDueDay = dueDay.toIntOrNull()?.takeIf { it in 1..31 }
    val limitCents = runCatching { limit.takeIf(String::isNotBlank)?.let {
        BigDecimal(it).movePointRight(CurrencyDisplay.decimalPlaces).setScale(0, RoundingMode.HALF_UP).longValueExact().takeIf { cents -> cents > 0 }
    } }.getOrNull()
    val validPaymentDue = if (useFixedDueDay) validDueDay != null else validOffset != null
    val numberKeyboard = KeyboardOptions(keyboardType = KeyboardType.Number)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth()) {
            Column(
                Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.screenHorizontal),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Text(if (card == null) stringResource(R.string.fs_add_credit_card_title)
                    else stringResource(R.string.fs_edit_card),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                ActuaFormCard {
                    if (card == null) {
                        ActuaMenuRow(
                            icon = Icons.Outlined.CreditCard,
                            label = stringResource(R.string.fs_account),
                            value = account?.name ?: stringResource(R.string.fs_select_account),
                            valueIsPlaceholder = account == null,
                            choices = accounts.map { it.name to it.id },
                        ) { accountId = it }
                    } else {
                        ActuaFormRow(icon = Icons.Outlined.CreditCard, label = stringResource(R.string.fs_account), value = card.accountName)
                    }
                    ActuaCardDivider()
                    ActuaFormTextField(
                        icon = Icons.Outlined.CalendarMonth,
                        label = stringResource(R.string.fs_statement_closing_day),
                        value = day,
                        onValueChange = { day = it.filter(Char::isDigit).take(2) },
                        keyboardOptions = numberKeyboard,
                    )
                }
                ActuaGroupLabel(stringResource(R.string.fs_payment_due))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(
                        false to stringResource(R.string.fs_days_after),
                        true to stringResource(R.string.fs_day_of_month),
                    ).forEachIndexed { index, (fixed, label) ->
                        SegmentedButton(
                            selected = useFixedDueDay == fixed,
                            onClick = { useFixedDueDay = fixed },
                            shape = SegmentedButtonDefaults.itemShape(index, 2),
                        ) { Text(label) }
                    }
                }
                ActuaFormCard {
                    if (useFixedDueDay) {
                        ActuaFormTextField(
                            icon = Icons.Outlined.Event,
                            label = stringResource(R.string.fs_payment_due_day),
                            value = dueDay,
                            onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
                            keyboardOptions = numberKeyboard,
                        )
                    } else {
                        ActuaFormTextField(
                            icon = Icons.Outlined.Event,
                            label = stringResource(R.string.fs_payment_due_after),
                            value = offset,
                            onValueChange = { offset = it.filter(Char::isDigit).take(2) },
                            keyboardOptions = numberKeyboard,
                        )
                    }
                    ActuaCardDivider()
                    ActuaFormTextField(
                        icon = Icons.Outlined.Payments,
                        label = stringResource(R.string.fs_credit_limit_optional),
                        value = limit,
                        onValueChange = { value -> limit = value.filter { it.isDigit() || it == '.' } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                Text(
                    stringResource(
                        if (useFixedDueDay) R.string.fs_fixed_due_explanation
                        else R.string.fs_offset_due_explanation,
                    ),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.xs))
                if (onRemove != null) TextButton(onClick = onRemove, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.fs_remove_credit_card_tracking), color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.fs_cancel)) }
            }
            ActuaPrimaryActionBar(
                text = stringResource(R.string.fs_save),
                enabled = accountId.isNotBlank() && validDay != null && validPaymentDue && (limit.isBlank() || limitCents != null),
                onClick = {
                    val paymentDue = if (useFixedDueDay) CreditCardCycle.PaymentDue.DayOfMonth(validDueDay!!)
                    else CreditCardCycle.PaymentDue.DaysAfter(validOffset!!)
                    onSave(accountId, validDay!!, paymentDue, limitCents)
                },
                icon = Icons.Outlined.Check,
            )
        }
    }
}
