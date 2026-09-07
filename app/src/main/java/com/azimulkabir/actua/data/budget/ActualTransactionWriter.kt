package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualPayee
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HybridLogicalClock
import com.azimulkabir.actua.data.rules.RulesEngine
import java.util.UUID

/** Offline-first transaction mutations matching Actual's row/message shapes. */
class ActualTransactionWriter(
    private val database: ActualBudgetDatabase,
    nodeId: String = HybridLogicalClock.generateNodeId(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    nowMillis: () -> Long = System::currentTimeMillis,
    private val onWrite: () -> Unit = {},
) {
    private val clock = HybridLogicalClock(nodeId, nowMillis = nowMillis)

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

    fun createTransaction(transaction: ActualTransaction, applyRules: Boolean = true): ActualTransaction? {
        var final = transaction
        if (applyRules && transaction.transferId == null) {
            val result = RulesEngine.apply(transaction, database.fetchRules(), database.ruleContext())
            if (result.isDeleted) return null
            final = result.transaction
            result.pendingPayeeName?.let { final = final.copy(payeeId = resolveOrCreatePayee(it).id) }
        }
        validateBase(final)
        require(!final.isParent && final.parentId == null) { "Use createSplit for split rows" }
        database.insertTransactions(listOf(final), fieldsForInsert(final))
        saveClock()
        return final
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

    fun createSplit(parent: ActualTransaction, children: List<ActualTransaction>) {
        validateBase(parent)
        require(parent.isParent && parent.parentId == null && parent.categoryId == null) { "Invalid split parent" }
        require(children.size >= 2) { "A split needs at least two lines" }
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
            transactionFields(transaction).filterKeys { it in changedFields }))
        saveClock()
    }

    fun deleteTransaction(transaction: ActualTransaction) {
        val ids = if (transaction.isParent) {
            database.fetchChildTransactions(transaction.id).map(ActualTransaction::id) + transaction.id
        } else listOf(transaction.id)
        database.tombstoneTransactions(ids, ids.map { message("transactions", it, "tombstone", 1) })
        saveClock()
    }

    fun mutate(
        updates: List<Pair<ActualTransaction, ActualTransaction>> = emptyList(),
        inserts: List<ActualTransaction> = emptyList(),
        tombstoneIds: List<String> = emptyList(),
    ) {
        updates.forEach { (_, updated) -> validateBase(updated) }
        inserts.forEach(::validateBase)
        val messages = updates.flatMap { (original, updated) ->
            val changed = changedFields(original, updated)
            fields("transactions", updated.id, transactionFields(updated).filterKeys { it in changed })
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
        fields("transactions", transaction.id, transactionFields(transaction))

    private fun transactionFields(transaction: ActualTransaction): LinkedHashMap<String, Any?> = linkedMapOf(
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
    )

    private fun fields(dataset: String, row: String, values: Map<String, Any?>): List<CrdtMessage> =
        values.map { (column, value) -> message(dataset, row, column, value) }

    private fun message(dataset: String, row: String, column: String, value: Any?) =
        CrdtMessage(clock.send(), dataset, row, column, CrdtValue.serialize(value))

    private fun saveClock() {
        database.saveClock(ActualBudgetDatabase.ClockRecord(clock.current().toString(), database.deriveMerkleFromMessageLog().root))
        onWrite()
    }

    companion object {
        private val mutableTransactionFields = setOf(
            "acct", "date", "description", "category", "amount", "notes", "cleared",
            "reconciled", "transferred_id", "isParent", "parent_id", "tombstone",
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
            if (original.isParent != updated.isParent) add("isParent")
            if (original.parentId != updated.parentId) add("parent_id")
            if (original.tombstone != updated.tombstone) add("tombstone")
        }
    }
}
