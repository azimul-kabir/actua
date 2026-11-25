package com.azimulkabir.actua.data.importing

import com.azimulkabir.actua.model.Account

/**
 * The account an import [hint] names, or null. An exact, case-insensitive name match wins when it
 * is unique; otherwise the hint is read as a card/account suffix and matched against the digits in
 * account names, as the message parser extracts them (#1033).
 */
fun accountForImportHint(hint: String, accounts: List<Account>): Account? {
    val wanted = hint.trim()
    if (wanted.isEmpty()) return null
    accounts.filter { it.name.trim().equals(wanted, ignoreCase = true) }.singleOrNull()?.let { return it }
    accounts.singleOrNull { wanted in it.name.filter(Char::isDigit) }?.let { return it }
    // A card alert's hint is "<card name> <last digits>", e.g. "CIBC Costco Mastercard 2085".
    val card = CARD_HINT.matchEntire(wanted) ?: return null
    val (name, digits) = card.destructured
    accounts.singleOrNull { digits in it.name.filter(Char::isDigit) }?.let { return it }
    val words = importHintWords(name)
    return accounts.singleOrNull { account ->
        importHintWords(account.name).let { it.isNotEmpty() && words.containsAll(it) }
    }
}

private val CARD_HINT = Regex("(.*\\p{L}.*?)\\s+(\\d{4})")
private val HINT_WORD = Regex("[\\p{L}\\p{N}]+")

/** The lower-cased words of [text], so an account named "Costco Mastercard" matches "CIBC Costco Mastercard". */
private fun importHintWords(text: String): Set<String> =
    HINT_WORD.findAll(text.lowercase()).map { it.value }.toSet()

/**
 * Names of open [accounts] that, sent as an import hint, select exactly that account: what the
 * Tasker plugin form offers to pick (#1042). A name shared by two accounts selects neither.
 */
fun importHintAccountNames(accounts: List<Account>): List<String> {
    val open = accounts.filterNot(Account::closed)
    return open.filter { accountForImportHint(it.name, open)?.id == it.id }.map { it.name.trim() }
}

/**
 * The key a captured row's chosen account is remembered under (#1045): its account hint when it
 * has one ("9921"), otherwise its source ("com.bank.app", "Tasker: GPay").
 */
fun importAccountMemoryKey(hint: String?, source: String): String =
    hint?.trim()?.takeIf(String::isNotEmpty)?.let { "hint:" + it.lowercase() } ?: "source:" + source.trim()

/** The memory key of the account captures fall back to when nothing else names one (#1064). */
const val DEFAULT_IMPORT_ACCOUNT_KEY = "default"

/**
 * The account a captured row starts with on review: the account its [hint] names, else the account
 * last chosen for that hint, else the one last chosen for its [source], else the default capture
 * account. [remembered] maps a memory key to an account id; closed or missing accounts are never
 * returned.
 */
fun prefilledImportAccount(
    hint: String?,
    source: String,
    accounts: List<Account>,
    remembered: (String) -> String?,
): Account? {
    val open = accounts.filterNot(Account::closed)
    fun byId(key: String) = remembered(key)?.let { id -> open.firstOrNull { it.id == id } }
    val cleanHint = hint?.trim()?.takeIf(String::isNotEmpty)
    return cleanHint?.let { accountForImportHint(it, open) }
        ?: cleanHint?.let { byId(importAccountMemoryKey(it, source)) }
        ?: byId(importAccountMemoryKey(null, source))
        // Broadcasts without a source were labeled "Tasker" before #1756; keep their remembered account.
        ?: source.trim().takeIf { it == AutomationIntent.DEFAULT_SOURCE }
            ?.let { byId(importAccountMemoryKey(null, AutomationIntent.LEGACY_DEFAULT_SOURCE)) }
        ?: byId(DEFAULT_IMPORT_ACCOUNT_KEY)
}
