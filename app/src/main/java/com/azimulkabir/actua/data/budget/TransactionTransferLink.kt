package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualTransaction

/**
 * Linking two existing transactions as a transfer, ported from the desktop "Make transfer" action
 * at 59fe126f: `shared/transfer.ts` `validForTransfer` and `onSetTransfer` in
 * `desktop-client/src/hooks/useTransactionBatchActions.ts`. Planning is pure so every rule is
 * unit-tested; the writer applies the plan as one CRDT batch, so an invalid pair writes nothing.
 */
object TransactionTransferLink {
    /**
     * `validForTransfer`: why [a] and [b] can't become a transfer, or null when they can. Unlike
     * Actual, split parents and lines are refused, as Actua offers no transfers on split lines (#748).
     */
    fun invalidReason(a: ActualTransaction?, b: ActualTransaction?): String? = when {
        a == null || b == null || a.tombstone || b.tombstone -> "One of the provided transactions does not exist"
        a.id == b.id -> "Choose two different transactions"
        a.transferId != null || b.transferId != null -> "One of the transactions is already a transfer"
        a.accountId == b.accountId -> "Transfer transactions must be in different accounts"
        a.amountCents + b.amountCents != 0L -> "Transfer amounts must cancel each other out"
        a.isParent || b.isParent || a.parentId != null || b.parentId != null ->
            "Split transactions can't be made into a transfer"
        else -> null
    }

    /**
     * `onSetTransfer`: each row takes the other account's transfer payee, links to the other row and
     * loses its category; nothing else changes, and no other leg is created (`runTransfers: false`).
     * [transferPayeeOf] is an account's transfer payee id. Each update is (as read, as linked).
     */
    fun plan(
        a: ActualTransaction,
        b: ActualTransaction,
        transferPayeeOf: (accountId: String) -> String?,
    ): List<Pair<ActualTransaction, ActualTransaction>> {
        invalidReason(a, b)?.let { throw IllegalArgumentException(it) }
        val payeeForA = requireNotNull(transferPayeeOf(b.accountId)) { "That account has no transfer payee" }
        val payeeForB = requireNotNull(transferPayeeOf(a.accountId)) { "That account has no transfer payee" }
        return listOf(
            a to a.copy(payeeId = payeeForA, transferId = b.id, categoryId = null),
            b to b.copy(payeeId = payeeForB, transferId = a.id, categoryId = null),
        )
    }
}
