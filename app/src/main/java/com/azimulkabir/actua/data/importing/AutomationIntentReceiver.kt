package com.azimulkabir.actua.data.importing

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * Accepts transactions already parsed by Tasker or another automation app and adds them to the
 * notification review queue. Nothing reaches the budget until the user imports it from review.
 */
class AutomationIntentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutomationIntent.ACTION_QUEUE_TRANSACTION) return
        val bundle = intent.extras ?: return
        val extras = AutomationIntent.EXTRAS.associateWith { key ->
            @Suppress("DEPRECATION") // Tasker infers numeric extra types, so accept any value.
            bundle.get(key)?.toString()
        }
        val store = NotificationImportPreferences(context)
        if (!isAuthorizedAutomation(store.automationEnabled, store.automationToken, extras[AutomationIntent.EXTRA_TOKEN])) return
        parseAutomationExtras(extras, profile = store.profile()).candidates.forEach(store::enqueue)
    }
}

object AutomationIntent {
    const val ACTION_QUEUE_TRANSACTION = "com.azimulkabir.actua.action.QUEUE_TRANSACTION"
    const val EXTRA_TOKEN = "token"
    const val EXTRA_AMOUNT = "amount"
    const val EXTRA_TYPE = "type"
    const val EXTRA_PAYEE = "payee"
    const val EXTRA_DATE = "date"
    const val EXTRA_NOTES = "notes"
    const val EXTRA_REFERENCE = "reference"
    const val EXTRA_ACCOUNT = "account"
    const val EXTRA_SOURCE = "source"
    const val EXTRA_TEXT = "text"
    const val DEFAULT_SOURCE = "Tasker"
    val EXTRAS = listOf(EXTRA_TOKEN, EXTRA_AMOUNT, EXTRA_TYPE, EXTRA_PAYEE, EXTRA_DATE, EXTRA_NOTES,
        EXTRA_REFERENCE, EXTRA_ACCOUNT, EXTRA_SOURCE, EXTRA_TEXT)
}

internal fun isAuthorizedAutomation(enabled: Boolean, expectedToken: String?, providedToken: String?): Boolean =
    enabled && !expectedToken.isNullOrBlank() && providedToken != null &&
        MessageDigest.isEqual(expectedToken.toByteArray(), providedToken.trim().toByteArray())

/** Converts automation extras into at most one review candidate; invalid input yields a problem. */
fun parseAutomationExtras(
    extras: Map<String, String?>,
    today: LocalDate = LocalDate.now(),
    profile: FinancialMessageProfile = FinancialMessageParser.defaultProfile,
): ImportParseResult {
    fun value(key: String) = extras[key]?.trim()?.takeIf(String::isNotEmpty)
    val source = value(AutomationIntent.EXTRA_SOURCE)?.take(80) ?: AutomationIntent.DEFAULT_SOURCE
    val rawAmount = value(AutomationIntent.EXTRA_AMOUNT)
    if (rawAmount == null) {
        val text = value(AutomationIntent.EXTRA_TEXT) ?: return automationFailure("No amount or text was provided")
        return FinancialMessageParser.parse(text, source, today, profile)
    }
    val amount = rawAmount.replace(",", "").replace(" ", "").toBigDecimalOrNull()
        ?.stripTrailingZeros()?.takeIf { it.scale() <= 2 }
        ?: return automationFailure("Amount must be a number with at most two decimals")
    if (amount.abs() >= MAX_AUTOMATION_AMOUNT) return automationFailure("Amount is too large")
    val cents = amount.setScale(2).movePointRight(2).longValueExact()
    val signed = when (value(AutomationIntent.EXTRA_TYPE)?.lowercase(Locale.ROOT)) {
        null -> cents
        "debit", "expense", "outflow" -> -kotlin.math.abs(cents)
        "credit", "income", "inflow" -> kotlin.math.abs(cents)
        else -> return automationFailure("Type must be debit or credit")
    }
    val date = value(AutomationIntent.EXTRA_DATE)?.let {
        try { LocalDate.parse(it) } catch (_: DateTimeParseException) { null }
            ?: return automationFailure("Date must use YYYY-MM-DD")
    } ?: today
    val candidate = ImportCandidate(
        sourceRow = 1,
        date = date.year * 10_000 + date.monthValue * 100 + date.dayOfMonth,
        payee = value(AutomationIntent.EXTRA_PAYEE)?.take(200) ?: source,
        notes = value(AutomationIntent.EXTRA_NOTES)?.take(500) ?: "Imported from $source",
        amountCents = signed,
        reference = value(AutomationIntent.EXTRA_REFERENCE)?.take(100),
        confidence = ImportConfidence.HIGH,
        sourceLabel = source,
        accountHint = value(AutomationIntent.EXTRA_ACCOUNT)?.take(100),
    )
    return ImportParseResult(listOf(candidate), emptyList())
}

private val MAX_AUTOMATION_AMOUNT = BigDecimal("10000000000")

private fun automationFailure(message: String) = ImportParseResult(emptyList(), listOf(ImportProblem(1, message)))
