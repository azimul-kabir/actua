package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualTransaction

/**
 * Merging two transactions into one, ported from loot-core `server/transactions/merge.ts` and
 * `shared/merge.ts` at 59fe126f. Planning is pure so every rule is unit-tested; the writer applies
 * the plan as one CRDT batch, so an invalid pair writes nothing.
 */
object TransactionMerge {
    /** The rows to write: each update is (as read, as merged), and [keptId] survives. */
    data class Plan(
        val keptId: String,
        val updates: List<Pair<ActualTransaction, ActualTransaction>>,
        val tombstoneIds: List<String>,
    )

    /** `validForMergeExplanation`: why [a] and [b] can't be merged, or null when they can. */
    fun invalidReason(a: ActualTransaction?, b: ActualTransaction?): String? = when {
        a == null || b == null -> "One of the provided transactions does not exist"
        a.accountId != b.accountId -> "Cannot merge transactions from different accounts"
        a.amountCents != b.amountCents -> "Cannot merge transactions with different amounts"
        // A->B merges with A->B, never with A->C.
        a.transferId != null && b.transferId != null && a.payeeId != b.payeeId ->
            "Cannot merge transfers to different accounts"
        else -> null
    }

    /**
     * `determineKeepDrop`: keep the bank-imported row (`imported_id`, Actua's `financial_id`), then
     * the one with an imported payee, then the earlier date; on a tie the second row is kept.
     */
    fun keepDrop(a: ActualTransaction, b: ActualTransaction): Pair<ActualTransaction, ActualTransaction> = when {
        b.financialId != null && a.financialId == null -> b to a
        a.financialId != null && b.financialId == null -> a to b
        b.importedPayee != null && a.importedPayee == null -> b to a
        a.importedPayee != null && b.importedPayee == null -> a to b
        a.date < b.date -> a to b
        else -> b to a
    }

    /**
     * `mergeTransactions` for [a] and [b], in the order they were selected.
     *
     * @param rowOf a live row by id, split children included.
     * @param childrenOf the live children of a split parent.
     * @param onBudgetTransferPayee true when a payee is the transfer payee of an on-budget account.
     */
    fun plan(
        a: ActualTransaction,
        b: ActualTransaction,
        rowOf: (String) -> ActualTransaction?,
        childrenOf: (String) -> List<ActualTransaction>,
        onBudgetTransferPayee: (String) -> Boolean,
    ): Plan {
        require(a.id != b.id) { "Merging is only possible with 2 distinct transactions" }
        invalidReason(a, b)?.let { throw IllegalArgumentException(it) }
        // Actual's lists select whole transactions; a split line is merged through its parent.
        require(a.parentId == null && b.parentId == null) { "Split lines can't be merged" }
        val batch = Batch(rowOf, childrenOf)
        val aTransferId = a.transferId
        val bTransferId = b.transferId
        if (aTransferId == null && bTransferId == null) {
            return batch.finish(batch.mergeNoTransfer(a, b))
        }

        val transferPayee = if (aTransferId != null) a.payeeId else b.payeeId
        val partners = listOfNotNull(aTransferId, bTransferId).map { id ->
            rowOf(id) ?: throw ActualTransactionFormException.TransferPartnerMissing
        }
        (listOf(a, b) + partners).forEach { row -> batch.edit(row) { it.copy(transferId = null) } }
        val partnerId = if (partners.size == 1) partners.single().id else {
            val (aPartner, bPartner) = partners
            invalidReason(aPartner, bPartner)?.let { throw IllegalArgumentException(it) }
            // Merging two split lines in another account would need loot-core's split recalculation.
            require(aPartner.parentId == null && bPartner.parentId == null) {
                "Transfers from split lines can't be merged"
            }
            batch.mergeNoTransfer(batch.current(aPartner), batch.current(bPartner))
        }
        val keptId = batch.mergeNoTransfer(batch.current(a), batch.current(b))
        batch.edit(keptId) { it.copy(transferId = partnerId, payeeId = transferPayee) }
        batch.edit(partnerId) { it.copy(transferId = keptId) }

        // A transfer between on-budget accounts has no category, so one kept from the non-transfer
        // is cleared. With two transfers there was no non-transfer category to keep.
        if (transferPayee != null && partners.size == 1 && onBudgetTransferPayee(transferPayee)) {
            batch.edit(keptId) { it.copy(categoryId = null) }
        }
        return batch.finish(keptId)
    }

    /** Every edit to a row lands on its latest state, so the batch writes each row once. */
    private class Batch(
        private val rowOf: (String) -> ActualTransaction?,
        private val childrenOf: (String) -> List<ActualTransaction>,
    ) {
        private val rows = linkedMapOf<String, Pair<ActualTransaction, ActualTransaction>>()
        private val tombstones = linkedSetOf<String>()

        fun current(row: ActualTransaction): ActualTransaction = rows[row.id]?.second ?: row

        fun edit(row: ActualTransaction, change: (ActualTransaction) -> ActualTransaction) {
            val original = rows[row.id]?.first ?: row
            rows[row.id] = original to change(current(row))
        }

        fun edit(id: String, change: (ActualTransaction) -> ActualTransaction) {
            val row = rows[id]?.second ?: rowOf(id) ?: return
            edit(row, change)
        }

        /** `mergeTransactionsNoTransfer`; returns the kept id. */
        fun mergeNoTransfer(a: ActualTransaction, b: ActualTransaction): String {
            val (keep, drop) = keepDrop(a, b)
            val keepChildren = if (keep.isParent) childrenOf(keep.id) else emptyList()
            val dropChildren = if (drop.isParent) childrenOf(drop.id) else emptyList()
            fun ActualTransaction.filledFrom(other: ActualTransaction) = copy(
                payeeId = payeeId ?: other.payeeId,
                notes = notes?.takeIf(String::isNotEmpty) ?: other.notes,
                cleared = cleared || other.cleared,
                reconciled = reconciled || other.reconciled,
                scheduleId = scheduleId ?: other.scheduleId,
            )
            if (keepChildren.isEmpty() && dropChildren.isNotEmpty()) {
                // The dropped split's lines move to the kept row, which becomes their parent.
                dropChildren.forEach { child -> edit(child) { it.copy(parentId = keep.id) } }
                edit(keep) { it.filledFrom(drop).copy(isParent = true, categoryId = null) }
                tombstones += drop.id
            } else {
                edit(keep) { it.filledFrom(drop).copy(categoryId = it.categoryId ?: drop.categoryId) }
                tombstones += drop.id
                tombstones += dropChildren.map(ActualTransaction::id)
            }
            return keep.id
        }

        /**
         * Tombstones the dropped rows. A dropped split line that is a transfer loses its other leg
         * too, as loot-core's `deleteTransaction` diff runs `transfer.onDelete` for it.
         */
        fun finish(keptId: String): Plan {
            tombstones.toList().forEach { id ->
                val row = rows[id]?.second ?: rowOf(id) ?: return@forEach
                val partnerId = row.transferId ?: return@forEach
                if (partnerId in tombstones) return@forEach
                val partner = rows[partnerId]?.second ?: rowOf(partnerId) ?: return@forEach
                if (partner.parentId != null) edit(partner) { it.copy(transferId = null, payeeId = null) }
                else tombstones += partner.id
                edit(row) { it.copy(transferId = null) }
            }
            // Like loot-core, a dropped row's cleared transfer link is written before its tombstone.
            return Plan(keptId, rows.values.filter { (original, updated) -> original != updated }, tombstones.toList())
        }
    }
}
