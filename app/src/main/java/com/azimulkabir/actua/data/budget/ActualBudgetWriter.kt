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

    /** Applies a template preview as one CRDT/database batch. Reapplying an unchanged plan is a no-op. */
    @Synchronized
    fun setAmounts(month: String, amounts: Map<String, Long>, expectedCurrent: Map<String, Long> = emptyMap()) {
        require(amounts.keys.none(String::isBlank)) { "Category ids cannot be blank" }
        require(expectedCurrent.keys == amounts.keys || expectedCurrent.isEmpty()) { "Expected amounts must cover every category" }
        val cells = amounts.keys.associateWith { categoryId ->
            val cell = database.budgetCell(month, categoryId) ?: error("Budget table is missing")
            val expected = expectedCurrent[categoryId]
            require(expected == null || expected == cell.amountCents) {
                "The budget changed after this preview. Review the template again."
            }
            cell
        }
        val writes = amounts.entries.mapNotNull { (categoryId, amount) ->
            val cell = cells.getValue(categoryId)
            (cell to amount).takeIf { !cell.exists || cell.amountCents != amount }
        }
        if (writes.isNotEmpty()) write(writes)
    }

    /** Atomically writes the budget and goal parts of a confirmed automation preview. */
    @Synchronized
    fun applyTemplate(
        month: String,
        amounts: Map<String, Long>,
        expectedAmounts: Map<String, Long>,
        goals: Map<String, Long?>,
        expectedGoals: Map<String, Long?>,
    ) {
        require(expectedAmounts.keys == amounts.keys)
        require(expectedGoals.keys == goals.keys)
        require((amounts.keys + goals.keys).none(String::isBlank))
        val cells = (amounts.keys + goals.keys).associateWith { categoryId ->
            database.budgetCell(month, categoryId) ?: error("Budget table is missing")
        }
        amounts.forEach { (id, _) -> require(cells.getValue(id).amountCents == expectedAmounts[id]) {
            "The budget changed after this preview. Review the template again."
        } }
        goals.forEach { (id, _) -> require(cells.getValue(id).goalCents == expectedGoals[id]) {
            "The goal changed after this preview. Review the template again."
        } }
        val messages = buildList {
            cells.values.filterNot(ActualBudgetDatabase.BudgetCell::exists).forEach { cell ->
                add(message(cell.table, cell.rowId, "month", cell.month))
                add(message(cell.table, cell.rowId, "category", cell.categoryId))
            }
            amounts.forEach { (id, amount) ->
                val cell = cells.getValue(id)
                if (!cell.exists || cell.amountCents != amount) add(message(cell.table, cell.rowId, "amount", amount))
            }
            goals.forEach { (id, goal) ->
                val cell = cells.getValue(id)
                if (!cell.exists || cell.goalCents != goal || !cell.longGoal) {
                    add(message(cell.table, cell.rowId, "goal", goal))
                    add(message(cell.table, cell.rowId, "long_goal", goal?.let { 1 }))
                }
            }
        }
        if (messages.isEmpty()) return
        database.applyLocalMessages(messages)
        database.saveClock(ActualBudgetDatabase.ClockRecord(
            clock.current().toString(), database.deriveMerkleFromMessageLog().root,
        ))
        onWrite()
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
