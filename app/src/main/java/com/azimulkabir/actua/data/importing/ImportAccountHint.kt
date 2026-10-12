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
    return accounts.singleOrNull { wanted in it.name.filter(Char::isDigit) }
}

/**
 * Names of open [accounts] that, sent as an import hint, select exactly that account: what the
 * Tasker plugin form offers to pick (#1042). A name shared by two accounts selects neither.
 */
fun importHintAccountNames(accounts: List<Account>): List<String> {
    val open = accounts.filterNot(Account::closed)
    return open.filter { accountForImportHint(it.name, open)?.id == it.id }.map { it.name.trim() }
}
