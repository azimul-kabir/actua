package com.azimulkabir.actua.ui.transactions

import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type

/**
 * The pair the selection menu's Merge acts on, in selection order, or null when Merge is disabled.
 * Like Actual's mobile `TransactionList` (`twoTransactions` + `validForMerge`, 59fe126f): exactly
 * two posted rows in the same account with the same amount, and two transfers only to the same
 * account. The writer checks the stored rows again before anything is written.
 */
internal fun mergeablePair(selected: List<Transaction>): Pair<Transaction, Transaction>? {
    if (selected.size != 2) return null
    val (a, b) = selected
    if (a.id == b.id || a.id.isBlank() || b.id.isBlank() || a.isUpcoming || b.isUpcoming) return null
    val sameAccount = if (a.accountId != null && b.accountId != null) a.accountId == b.accountId else a.account == b.account
    if (!sameAccount || a.amountCents != b.amountCents) return null
    if (a.type == Type.TRANSFER && b.type == Type.TRANSFER) {
        val sameDestination = if (a.transferAccountId != null && b.transferAccountId != null) {
            a.transferAccountId == b.transferAccountId
        } else a.transferAccount == b.transferAccount
        if (!sameDestination) return null
    }
    return a to b
}
