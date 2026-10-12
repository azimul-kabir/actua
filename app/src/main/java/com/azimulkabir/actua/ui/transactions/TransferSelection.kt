package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type

/**
 * The pair the selection menu's Make transfer acts on, or null when it is disabled. Like Actual's
 * `validForTransfer` (59fe126f): exactly two posted rows, neither already a transfer, in different
 * accounts, whose amounts cancel out. Split transactions are left out (#748). The writer checks the
 * stored rows again before anything is written.
 */
internal fun transferablePair(selected: List<Transaction>): Pair<Transaction, Transaction>? {
    if (selected.size != 2) return null
    val (a, b) = selected
    if (a.id == b.id || a.id.isBlank() || b.id.isBlank() || a.isUpcoming || b.isUpcoming) return null
    if (a.type == Type.TRANSFER || b.type == Type.TRANSFER) return null
    if (a.splits.isNotEmpty() || b.splits.isNotEmpty()) return null
    val sameAccount = if (a.accountId != null && b.accountId != null) a.accountId == b.accountId else a.account == b.account
    if (sameAccount || a.amountCents + b.amountCents != 0L) return null
    return a to b
}
