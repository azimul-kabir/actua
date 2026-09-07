package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.data.sync.HybridLogicalClock
import com.azimulkabir.actua.data.schedules.DayDate
import java.util.UUID
import com.azimulkabir.actua.data.rules.Rule

/** CRDT mutation path shared by account/category/group/payee menu actions. */
class ActualEntityWriter(
    private val database: ActualBudgetDatabase,
    nodeId: String = HybridLogicalClock.generateNodeId(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onWrite: () -> Unit = {},
) {
    private val clock = HybridLogicalClock(nodeId)

    init {
        database.maxMessageTimestamp()?.let(HlcTimestamp::parse)?.let(clock::advance)
    }

    fun update(dataset: String, id: String, fields: Map<String, Any?>) {
        require(dataset in allowedFields) { "Unsupported Actual dataset: $dataset" }
        require(id.isNotBlank() && fields.isNotEmpty())
        val invalid = fields.keys - allowedFields.getValue(dataset)
        require(invalid.isEmpty()) { "Unsupported $dataset fields: ${invalid.sorted().joinToString()}" }
        val messages = fields.map { (column, value) ->
            CrdtMessage(clock.send(), dataset, id, column, CrdtValue.serialize(value))
        }
        database.applyLocalMessages(messages)
        database.saveClock(ActualBudgetDatabase.ClockRecord(
            clock.current().toString(), database.deriveMerkleFromMessageLog().root,
        ))
        onWrite()
    }

    fun renameAccount(id: String, name: String) = update("accounts", id, mapOf("name" to requiredName(name)))
    fun setAccountClosed(id: String, closed: Boolean) = update("accounts", id, mapOf("closed" to flag(closed)))
    fun renameCategory(id: String, name: String) = update("categories", id, mapOf("name" to requiredName(name)))
    fun setCategoryHidden(id: String, hidden: Boolean) = update("categories", id, mapOf("hidden" to flag(hidden)))
    fun deleteCategory(id: String) = update("categories", id, mapOf("tombstone" to 1))
    fun renameCategoryGroup(id: String, name: String) = update("category_groups", id, mapOf("name" to requiredName(name)))
    fun setCategoryGroupHidden(id: String, hidden: Boolean) = update("category_groups", id, mapOf("hidden" to flag(hidden)))
    fun renamePayee(id: String, name: String) = update("payees", id, mapOf("name" to requiredName(name)))
    fun deletePayee(id: String) = update("payees", id, mapOf("tombstone" to 1))
    fun setPreference(id: String, value: String?) = update("preferences", id, mapOf("value" to value))
    fun setNote(id: String, note: String) = update("notes", id, mapOf("note" to note))
    fun saveRule(rule: Rule) = update("rules", rule.id, mapOf(
        "stage" to rule.storedStage,
        "conditions_op" to rule.conditionsOp.name.lowercase(),
        "conditions" to rule.conditionsJson,
        "actions" to rule.actionsJson,
        "tombstone" to 0,
    ))
    fun deleteRule(id: String) = update("rules", id, mapOf("tombstone" to 1))

    /** PWA/iOS local-account shape: account + transfer payee + optional opening transaction. */
    @Synchronized
    fun createAccount(name: String, offBudget: Boolean, startingBalanceCents: Long): String {
        val clean = requiredName(name)
        require(database.fetchAccounts().none { it.name.equals(clean, true) }) { "An account named \"$clean\" already exists" }
        val accountId = idFactory(); val transferPayeeId = idFactory()
        val messages = mutableListOf<CrdtMessage>()
        messages += fields("accounts", accountId, linkedMapOf("name" to clean, "type" to "checking",
            "offbudget" to flag(offBudget), "closed" to 0, "tombstone" to 0, "sort_order" to nowMillis()))
        messages += fields("payees", transferPayeeId, linkedMapOf("name" to "", "transfer_acct" to accountId, "tombstone" to 0))
        messages += fields("payee_mapping", transferPayeeId, linkedMapOf("targetId" to transferPayeeId))
        if (startingBalanceCents != 0L) {
            val payee = database.findPayeeByName("Starting Balance")
            val payeeId = payee?.id ?: idFactory().also { id ->
                messages += fields("payees", id, linkedMapOf("name" to "Starting Balance", "transfer_acct" to null, "tombstone" to 0))
                messages += fields("payee_mapping", id, linkedMapOf("targetId" to id))
            }
            val category = if (offBudget) null else database.fetchCategoryGroups().flatMap { it.categories }
                .filter { it.isIncome }.let { rows -> rows.firstOrNull { it.name.equals("Starting Balances", true) } ?: rows.firstOrNull() }
            messages += fields("transactions", idFactory(), linkedMapOf(
                "acct" to accountId, "date" to DayDate.today().yyyymmdd, "description" to payeeId,
                "category" to category?.id, "amount" to startingBalanceCents, "notes" to null,
                "cleared" to 1, "reconciled" to 0, "transferred_id" to null, "isParent" to 0,
                "isChild" to 0, "parent_id" to null, "tombstone" to 0, "sort_order" to nowMillis().toDouble(),
                "imported_description" to null, "schedule" to null, "starting_balance_flag" to 1))
        }
        persist(messages); return accountId
    }

    @Synchronized
    fun createCategoryGroup(name: String): String {
        val clean = requiredName(name); val groups = database.fetchCategoryGroups()
        require(groups.none { it.name.equals(clean, true) }) { "A category group named \"$clean\" already exists" }
        val id = idFactory(); val sort = (groups.maxOfOrNull { it.sortOrder } ?: 0.0) + SortOrder.INCREMENT
        persist(fields("category_groups", id, linkedMapOf("name" to clean, "is_income" to 0,
            "hidden" to 0, "tombstone" to 0, "sort_order" to sort)))
        return id
    }

    @Synchronized
    fun createCategory(name: String, groupId: String): String {
        val clean = requiredName(name); val group = database.fetchCategoryGroups().firstOrNull { it.id == groupId }
            ?: error("That category group no longer exists")
        require(group.categories.none { it.name.equals(clean, true) }) { "${group.name} already has a category named \"$clean\"" }
        val positions = group.categories.sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { SortOrder.Position(it.id, it.sortOrder) }
        val placement = SortOrder.shove(positions, positions.firstOrNull()?.id); val id = idFactory()
        val messages = mutableListOf<CrdtMessage>()
        messages += fields("categories", id, linkedMapOf("name" to clean, "cat_group" to group.id,
            "is_income" to flag(group.isIncome), "hidden" to flag(group.hidden), "tombstone" to 0,
            "sort_order" to placement.sortOrder))
        messages += fields("category_mapping", id, linkedMapOf("transferId" to id))
        placement.moved.forEach { messages += fields("categories", it.id, linkedMapOf("sort_order" to it.sortOrder)) }
        persist(messages); return id
    }

    private fun fields(dataset: String, row: String, values: Map<String, Any?>) = values.map { (column, value) ->
        CrdtMessage(clock.send(), dataset, row, column, CrdtValue.serialize(value))
    }

    private fun persist(messages: List<CrdtMessage>) {
        database.applyLocalMessages(messages)
        database.saveClock(ActualBudgetDatabase.ClockRecord(clock.current().toString(), database.deriveMerkleFromMessageLog().root))
        onWrite()
    }

    private fun requiredName(value: String) = value.trim().also { require(it.isNotEmpty()) { "Name cannot be empty" } }
    private fun flag(value: Boolean) = if (value) 1 else 0

    companion object {
        private val allowedFields = mapOf(
            "accounts" to setOf("name", "closed", "offbudget", "tombstone", "sort_order"),
            "categories" to setOf("name", "hidden", "cat_group", "tombstone", "sort_order"),
            "category_groups" to setOf("name", "hidden", "tombstone", "sort_order"),
            "payees" to setOf("name", "tombstone"),
            "preferences" to setOf("value"),
            "notes" to setOf("note"),
            "rules" to setOf("stage", "conditions_op", "conditions", "actions", "tombstone"),
        )
    }
}
