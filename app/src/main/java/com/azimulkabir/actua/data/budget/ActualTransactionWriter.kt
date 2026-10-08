package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualPayee
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HybridLogicalClock
import com.azimulkabir.actua.data.rules.RuleChangeGuard
import com.azimulkabir.actua.data.rules.RulesEngine
import com.azimulkabir.actua.data.rules.TransferLegRules
import java.util.UUID

/** Offline-first transaction mutations matching Actual's row/message shapes. */
class ActualTransactionWriter(
    private val database: ActualBudgetDatabase,
    nodeId: String = HybridLogicalClock.generateNodeId(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    nowMillis: () -> Long = System::currentTimeMillis,
    private val onWrite: () -> Unit = {},
) {
    private val clock = HybridLogicalClock(nodeId, nowMillis = nowMillis, highWater = database::messageLogHighWater)

    init {
        database.maxMessageTimestamp()?.let(com.azimulkabir.actua.data.sync.HlcTimestamp::parse)?.let(clock::advance)
    }

    fun resolveOrCreatePayee(name: String): ActualPayee {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Payee name cannot be empty" }
        database.findPayeeByName(clean)?.let { return it }
        val payee = ActualPayee(idFactory(), clean, null)
        val messages = fields("payees", payee.id, linkedMapOf(
            "name" to payee.name, "transfer_acct" to null, "tombstone" to 0,
        )) + fields("payee_mapping", payee.id, linkedMapOf("targetId" to payee.id))
        database.insertPayee(payee, messages)
        saveClock()
        return payee
    }

    /**
     * Inserts [transaction], running rules first when [applyRules]. With [runTransfers], a row whose
     * payee is another account's transfer payee also gets its other leg, as loot-core's
     * `addTransactions` (`runTransfers`) → `transfer.onInsert` → `addTransfer` does.
     */
    fun createTransaction(
        transaction: ActualTransaction,
        applyRules: Boolean = true,
        preserveCategory: Boolean = false,
        runTransfers: Boolean = false,
    ): ActualTransaction? {
        var final = transaction
        var splitChildren: List<com.azimulkabir.actua.data.rules.RuleSplitChild> = emptyList()
        if (applyRules && transaction.transferId == null) {
            val result = RulesEngine.apply(transaction, database.fetchRules(), database.ruleContext(), idFactory)
            if (result.isDeleted) return null
            final = result.transaction
            splitChildren = result.splitChildren
            if (splitChildren.isEmpty()) {
                result.pendingPayeeName?.let { final = final.copy(payeeId = resolveOrCreatePayee(it).id) }
            }
            if (preserveCategory &&
                !RuleChangeGuard.shouldApplyRuleChange("category", transaction.categoryId, final.categoryId)
            ) {
                final = final.copy(categoryId = transaction.categoryId)
            }
        }
        val offBudget = database.fetchAccounts().any { it.id == final.accountId && it.offBudget }
        if (offBudget) {
            final = final.copy(categoryId = null)
        }
        validateBase(final)
        if (splitChildren.isNotEmpty()) {
            val parent = final.copy(payeeId = null, categoryId = null, isParent = true, parentId = null)
            val children = splitChildren.map { child ->
                val payeeId = child.pendingPayeeName?.let { resolveOrCreatePayee(it).id } ?: child.transaction.payeeId
                child.transaction.copy(
                    payeeId = payeeId,
                    categoryId = child.transaction.categoryId.takeUnless { offBudget },
                    parentId = parent.id,
                )
            }
            createSplit(parent, children, allowSingleChild = true)
            return parent
        }
        require(!final.isParent && final.parentId == null) { "Use createSplit for split rows" }
        if (runTransfers && final.transferId == null) {
            transferLegsFor(final)?.let { (source, partner) ->
                createTransfer(source, partner)
                return source
            }
        }
        database.insertTransactions(listOf(final), fieldsForInsert(final))
        saveClock()
        return final
    }

    /**
     * loot-core `addTransfer`: the other leg goes to the payee's transfer account with the negated
     * amount, the source account's transfer payee, the same date, notes and schedule, uncleared, and
     * rules applied to it; the source keeps its category only for an on-budget → off-budget transfer
     * (`clearCategory`). Null when the payee isn't another account's transfer payee.
     */
    private fun transferLegsFor(source: ActualTransaction): Pair<ActualTransaction, ActualTransaction>? {
        val payeeId = source.payeeId ?: return null
        val transferAccount = database.fetchPayees().firstOrNull { it.id == payeeId }?.transferAccountId
            ?.takeIf { it != source.accountId } ?: return null
        val fromPayee = database.transferPayeeId(source.accountId) ?: error("This account has no transfer payee")
        val offBudget = database.fetchAccounts().filter { it.offBudget }.mapTo(mutableSetOf()) { it.id }
        val keepCategory = source.accountId !in offBudget && transferAccount in offBudget
        val partnerId = idFactory()
        val partner = ActualTransaction(
            id = partnerId, accountId = transferAccount, date = source.date, amountCents = -source.amountCents,
            payeeId = fromPayee, payeeName = null, categoryId = null, categoryName = null, notes = source.notes,
            cleared = false, reconciled = false, transferId = source.id, isParent = false, parentId = null,
            tombstone = false, sortOrder = source.sortOrder, importedPayee = null, scheduleId = source.scheduleId,
            transferAccountId = null,
        )
        return TransferLegRules.apply(
            source.copy(transferId = partnerId, categoryId = source.categoryId.takeIf { keepCategory }),
            partner, database.fetchRules(), database.ruleContext(),
        )
    }

    fun createTransfer(source: ActualTransaction, target: ActualTransaction) {
        validateBase(source)
        validateBase(target)
        require(source.accountId != target.accountId) { "Transfer accounts must be different" }
        require(source.transferId == target.id && target.transferId == source.id) { "Transfer legs must reference each other" }
        require(source.amountCents == -target.amountCents) { "Transfer amounts must balance" }
        require(!source.isParent && !target.isParent && source.parentId == null && target.parentId == null)
        val messages = fieldsForInsert(source) + fieldsForInsert(target)
        database.insertTransactions(listOf(source, target), messages)
        saveClock()
    }

    fun createSplit(parent: ActualTransaction, children: List<ActualTransaction>, allowSingleChild: Boolean = false) {
        validateBase(parent)
        require(parent.isParent && parent.parentId == null && parent.categoryId == null) { "Invalid split parent" }
        val minimumLines = if (allowSingleChild) 1 else 2
        require(children.size >= minimumLines) {
            "A split needs at least $minimumLines ${if (minimumLines == 1) "line" else "lines"}"
        }
        require(children.all { it.parentId == parent.id && !it.isParent && it.accountId == parent.accountId }) {
            "Every split child must reference its parent and account"
        }
        require(children.sumOf(ActualTransaction::amountCents) == parent.amountCents) { "Split amount does not match parent" }
        children.forEach(::validateBase)
        val rows = listOf(parent) + children
        database.insertTransactions(rows, rows.flatMap(::fieldsForInsert))
        saveClock()
    }

    fun updateTransaction(transaction: ActualTransaction, changedFields: Set<String>) {
        validateBase(transaction)
        val unknown = changedFields - mutableTransactionFields
        require(unknown.isEmpty()) { "Unknown transaction fields: ${unknown.sorted().joinToString()}" }
        database.updateTransaction(transaction, fields("transactions", transaction.id,
            transactionFields(transaction, changedFields)))
        saveClock()
    }

    /** Link or unlink a schedule; a transfer's other leg follows, as in loot-core `updateTransfer`. */
    @Synchronized
    fun setScheduleLink(transaction: ActualTransaction, scheduleId: String?) {
        val legs = listOf(transaction) + listOfNotNull(transaction.transferId?.let(database::fetchTransactionRow))
        val updates = legs.filter { it.scheduleId != scheduleId }.map { it to it.copy(scheduleId = scheduleId) }
        if (updates.isNotEmpty()) mutate(updates = updates)
    }

    /** Change cleared state while keeping split children aligned with their parent. */
    @Synchronized
    fun setCleared(transaction: ActualTransaction, cleared: Boolean) {
        require(!transaction.reconciled || transaction.cleared == cleared) {
            "Reconciled transactions are locked"
        }
        val originals = if (transaction.isParent) {
            listOf(transaction) + database.fetchChildTransactions(transaction.id)
        } else listOf(transaction)
        val updates = originals
            .filter { !it.reconciled && it.cleared != cleared }
            .map { it to it.copy(cleared = cleared) }
        if (updates.isNotEmpty()) mutate(updates = updates)
    }

    /** Lock all cleared rows for one account in a single CRDT/database transaction. */
    @Synchronized
    fun reconcileClearedTransactions(accountId: String): Int {
        val originals = database.fetchClearedUnreconciledTransactions(accountId)
        if (originals.isEmpty()) return 0
        mutate(updates = originals.map { it to it.copy(reconciled = true) })
        return originals.size
    }

    fun deleteTransaction(transaction: ActualTransaction) = deleteTransactions(listOf(transaction))

    /**
     * Tombstone [transactions] and their split children in one batch. Each deleted transfer leg
     * also loses its counterpart, like loot-core's `transfer.onDelete`.
     */
    @Synchronized
    fun deleteTransactions(transactions: List<ActualTransaction>) {
        val rows = transactions.flatMap { transaction ->
            if (transaction.isParent) listOf(transaction) + database.fetchChildTransactions(transaction.id)
            else listOf(transaction)
        }.distinctBy(ActualTransaction::id)
        if (rows.isEmpty()) return
        val deletedIds = rows.mapTo(linkedSetOf(), ActualTransaction::id)
        val detached = detachTransfers(rows, deletedIds)
        mutate(updates = detached.updates, tombstoneIds = deletedIds.toList() + detached.tombstoneIds)
    }

    /**
     * Merge [firstId] and [secondId], in the order they were selected, into one row in a single
     * batch (loot-core `transactions-merge`, see [TransactionMerge]). Returns the kept id.
     */
    @Synchronized
    fun mergeTransactions(firstId: String, secondId: String): String {
        val first = database.fetchTransactionRow(firstId)
        val second = database.fetchTransactionRow(secondId)
        TransactionMerge.invalidReason(first, second)?.let { throw IllegalArgumentException(it) }
        val onBudgetAccounts = database.fetchAccounts().filterNot { it.offBudget }.mapTo(mutableSetOf()) { it.id }
        val onBudgetTransferPayees = database.fetchPayees()
            .filter { it.transferAccountId in onBudgetAccounts }
            .mapTo(mutableSetOf()) { it.id }
        val plan = TransactionMerge.plan(
            requireNotNull(first), requireNotNull(second),
            rowOf = database::fetchTransactionRow,
            childrenOf = database::fetchChildTransactions,
            onBudgetTransferPayee = { it in onBudgetTransferPayees },
        )
        mutate(updates = plan.updates, tombstoneIds = plan.tombstoneIds)
        return plan.keptId
    }

    /**
     * loot-core's `removeTransfer` for every leg in [legs] that has a counterpart: the counterpart is
     * tombstoned, or unlinked (`transferred_id` and payee cleared) when it is a split child, and the
     * leg's own `transferred_id` is cleared. Counterparts in [removedIds] are already being deleted.
     */
    fun detachTransfers(legs: List<ActualTransaction>, removedIds: Set<String> = emptySet()): TransferDetach {
        val updates = mutableListOf<Pair<ActualTransaction, ActualTransaction>>()
        val tombstones = mutableListOf<String>()
        legs.filter { it.transferId != null }.forEach { leg ->
            val partnerId = requireNotNull(leg.transferId)
            if (partnerId !in removedIds && partnerId !in tombstones) {
                database.fetchTransactionRow(partnerId)?.let { partner ->
                    if (partner.parentId != null) {
                        updates += partner to partner.copy(transferId = null, payeeId = null)
                    } else {
                        tombstones += partner.id
                    }
                }
            }
            updates += leg to leg.copy(transferId = null)
        }
        return TransferDetach(updates, tombstones)
    }

    data class TransferDetach(
        val updates: List<Pair<ActualTransaction, ActualTransaction>>,
        val tombstoneIds: List<String>,
    )

    fun mutate(
        updates: List<Pair<ActualTransaction, ActualTransaction>> = emptyList(),
        inserts: List<ActualTransaction> = emptyList(),
        tombstoneIds: List<String> = emptyList(),
    ) {
        updates.forEach { (_, updated) -> validateBase(updated) }
        inserts.forEach(::validateBase)
        val messages = updates.flatMap { (original, updated) ->
            val changed = changedFields(original, updated)
            fields("transactions", updated.id, transactionFields(updated, changed))
        } + inserts.flatMap(::fieldsForInsert) +
            tombstoneIds.map { message("transactions", it, "tombstone", 1) }
        database.mutateTransactions(updates.map { it.second }, inserts, tombstoneIds, messages)
        saveClock()
    }

    private fun validateBase(transaction: ActualTransaction) {
        require(transaction.id.isNotBlank() && transaction.accountId.isNotBlank())
        require(transaction.date in 19000101..29991231) { "Invalid Actual YYYYMMDD date" }
        // Actual permits zero-valued imported/scheduled rows. Interactive forms
        // reject zero at their own validation boundary, matching iOS.
    }

    private fun fieldsForInsert(transaction: ActualTransaction) =
        // Actual leaves imported_description unset for hand-entered rows, so insert it only when present.
        fields("transactions", transaction.id, transactionFields(transaction).filterNot { (column, value) ->
            column == "imported_description" && value == null
        })

    /** Diffed update fields; changed import fields are sent even when they revert to null/false. */
    private fun transactionFields(transaction: ActualTransaction, changed: Set<String>): Map<String, Any?> =
        transactionFields(transaction, includeImportFields = changed.any { it in importFields })
            .filterKeys { it in changed }

    private fun transactionFields(
        transaction: ActualTransaction,
        includeImportFields: Boolean = false,
    ): LinkedHashMap<String, Any?> = linkedMapOf<String, Any?>(
        "acct" to transaction.accountId,
        "date" to transaction.date,
        "description" to transaction.payeeId,
        "category" to transaction.categoryId,
        "amount" to transaction.amountCents,
        "notes" to transaction.notes,
        "cleared" to if (transaction.cleared) 1 else 0,
        "reconciled" to if (transaction.reconciled) 1 else 0,
        "transferred_id" to transaction.transferId,
        "isParent" to if (transaction.isParent) 1 else 0,
        "isChild" to if (transaction.parentId != null) 1 else 0,
        "parent_id" to transaction.parentId,
        "tombstone" to if (transaction.tombstone) 1 else 0,
        "sort_order" to (transaction.sortOrder ?: System.currentTimeMillis().toDouble()),
        "imported_description" to transaction.importedPayee,
        "schedule" to transaction.scheduleId,
        "starting_balance_flag" to if (transaction.startingBalance) 1 else 0,
    ).apply {
        // These fields belong to provider imports. Omitting them for manual rows preserves the
        // established CRDT message shape instead of publishing redundant null/default writes.
        if (includeImportFields || transaction.financialId != null || transaction.rawSyncedData != null || transaction.pending) {
            put("financial_id", transaction.financialId)
            put("pending", if (transaction.pending) 1 else 0)
            put("raw_synced_data", transaction.rawSyncedData)
        }
    }

    private fun fields(dataset: String, row: String, values: Map<String, Any?>): List<CrdtMessage> =
        values.map { (column, value) -> message(dataset, row, column, value) }

    private fun message(dataset: String, row: String, column: String, value: Any?) =
        CrdtMessage(clock.send(), dataset, row, column, CrdtValue.serialize(value))

    private fun saveClock() {
        database.saveClock(ActualBudgetDatabase.ClockRecord(clock.current().toString(), database.deriveMerkleFromMessageLog().root))
        onWrite()
    }

    companion object {
        private val importFields = setOf("financial_id", "pending", "raw_synced_data")

        private val mutableTransactionFields = setOf(
            "acct", "date", "description", "category", "amount", "notes", "cleared",
            "reconciled", "transferred_id", "isParent", "parent_id", "tombstone", "schedule",
        )

        fun changedFields(original: ActualTransaction, updated: ActualTransaction): Set<String> = buildSet {
            if (original.accountId != updated.accountId) add("acct")
            if (original.date != updated.date) add("date")
            if (original.payeeId != updated.payeeId) add("description")
            if (original.categoryId != updated.categoryId) add("category")
            if (original.amountCents != updated.amountCents) add("amount")
            if (original.notes != updated.notes) add("notes")
            if (original.cleared != updated.cleared) add("cleared")
            if (original.reconciled != updated.reconciled) add("reconciled")
            if (original.transferId != updated.transferId) add("transferred_id")
            if (original.scheduleId != updated.scheduleId) add("schedule")
            if (original.isParent != updated.isParent) add("isParent")
            if (original.parentId != updated.parentId) add("parent_id")
            if (original.tombstone != updated.tombstone) add("tombstone")
            if (original.importedPayee != updated.importedPayee) add("imported_description")
            if (original.financialId != updated.financialId) add("financial_id")
            if (original.pending != updated.pending) add("pending")
            if (original.rawSyncedData != updated.rawSyncedData) add("raw_synced_data")
        }
    }
}
