package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.data.sync.HybridLogicalClock

/** Actual setBudget/transferCategory semantics through the shared CRDT path. */
class ActualBudgetWriter(
    private val database: ActualBudgetDatabase,
    nodeId: String = HybridLogicalClock.generateNodeId(),
    private val onWrite: () -> Unit = {},
) {
    private val clock = HybridLogicalClock(nodeId)

    init { database.maxMessageTimestamp()?.let(HlcTimestamp::parse)?.let(clock::advance) }

    @Synchronized
    fun setAmount(month: String, categoryId: String, amountCents: Long) {
        val cell = database.budgetCell(month, categoryId) ?: error("Budget table is missing")
        write(listOf(cell to amountCents))
    }

    @Synchronized
    fun setCarryover(months: List<String>, categoryId: String, enabled: Boolean) {
        val messages = months.flatMap { month ->
            val cell = database.budgetCell(month, categoryId) ?: error("Budget table is missing")
            buildList {
                if (!cell.exists) {
                    add(message(cell.table, cell.rowId, "month", cell.month))
                    add(message(cell.table, cell.rowId, "category", cell.categoryId))
                }
                add(message(cell.table, cell.rowId, "carryover", if (enabled) 1 else 0))
            }
        }
        database.applyLocalMessages(messages)
        database.saveClock(ActualBudgetDatabase.ClockRecord(
            clock.current().toString(), database.deriveMerkleFromMessageLog().root,
        ))
        onWrite()
    }

    @Synchronized
    fun transfer(month: String, fromCategoryId: String?, toCategoryId: String?, amountCents: Long) {
        require(amountCents > 0) { "Transfer amount must be positive" }
        require(fromCategoryId != toCategoryId) { "Choose two different budget locations" }
        val writes = buildList {
            fromCategoryId?.let { id ->
                val cell = database.budgetCell(month, id) ?: error("Budget table is missing")
                add(cell to cell.amountCents - amountCents)
            }
            toCategoryId?.let { id ->
                val cell = database.budgetCell(month, id) ?: error("Budget table is missing")
                add(cell to cell.amountCents + amountCents)
            }
        }
        require(writes.isNotEmpty())
        write(writes)
    }

    private fun write(writes: List<Pair<ActualBudgetDatabase.BudgetCell, Long>>) {
        val messages = writes.flatMap { (cell, amount) ->
            buildList {
                if (!cell.exists) {
                    add(message(cell.table, cell.rowId, "month", cell.month))
                    add(message(cell.table, cell.rowId, "category", cell.categoryId))
                }
                add(message(cell.table, cell.rowId, "amount", amount))
            }
        }
        database.applyLocalMessages(messages)
        database.saveClock(ActualBudgetDatabase.ClockRecord(
            clock.current().toString(), database.deriveMerkleFromMessageLog().root,
        ))
        onWrite()
    }

    private fun message(dataset: String, row: String, column: String, value: Any?) =
        CrdtMessage(clock.send(), dataset, row, column, CrdtValue.serialize(value))
}
