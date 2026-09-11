package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.data.sync.HybridLogicalClock
import java.util.UUID

/** CRDT mutation path shared by account/category/group/payee menu actions. */
class ActualEntityWriter(
    private val database: ActualBudgetDatabase,
    nodeId: String = HybridLogicalClock.generateNodeId(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onWrite: () -> Unit = {},
) {
    private val clock = HybridLogicalClock(nodeId)

    init { database.maxMessageTimestamp()?.let(HlcTimestamp::parse)?.let(clock::advance) }

    fun update(dataset: String, id: String, fields: Map<String, Any?>) {
        require(dataset in allowedFields) { "Unsupported Actual dataset: $dataset" }
        require(id.isNotBlank() && fields.isNotEmpty())
        val invalid = fields.keys - allowedFields.getValue(dataset)
        require(invalid.isEmpty()) { "Unsupported $dataset fields: ${invalid.sorted().joinToString()}" }
        persist(fields(dataset, id, fields))
    }

    fun renameAccount(id: String, name: String) = update("accounts", id, mapOf("name" to requiredName(name)))
    fun setAccountClosed(id: String, closed: Boolean) = update("accounts", id, mapOf("closed" to flag(closed)))
    fun renameCategory(id: String, name: String) = update("categories", id, mapOf("name" to requiredName(name)))
    fun setCategoryHidden(id: String, hidden: Boolean) = update("categories", id, mapOf("hidden" to flag(hidden)))
    fun setCategoryTarget(id: String, goalDef: String?) = update("categories", id, mapOf(
        "goal_def" to goalDef, "template_settings" to "{\"source\":\"ui\"}",
    ))

    /** Actual deletion keeps historical transaction references and tombstones the category. */
    fun deleteCategory(id: String) = update("categories", id, mapOf("tombstone" to 1))
    fun renameCategoryGroup(id: String, name: String) = update("category_groups", id, mapOf("name" to requiredName(name)))
    fun setCategoryGroupHidden(id: String, hidden: Boolean) = update("category_groups", id, mapOf("hidden" to flag(hidden)))
    fun renamePayee(id: String, name: String) = update("payees", id, mapOf("name" to requiredName(name)))

    /**
     * Ordinary payees may be deleted directly. Transfer payees are account-owned and must
     * disappear only as part of deleting their account so transfer semantics cannot be broken.
     */
    fun deletePayee(id: String) {
        val payee = database.fetchPayees().firstOrNull { it.id == id } ?: error("Payee no longer exists")
        require(payee.transferAccountId == null) { "Transfer payees cannot be deleted independently" }
        update("payees", id, mapOf("tombstone" to 1))
    }

    /** Actual payee merge is mapping-based: old references resolve to the surviving payee. */
    @Synchronized
    fun mergePayees(targetId: String, mergeIds: Collection<String>) {
        val payees = database.fetchPayees()
        val target = payees.firstOrNull { it.id == targetId } ?: error("Target payee no longer exists")
        require(target.transferAccountId == null) { "Transfer payees cannot be merge targets" }
        val sources = mergeIds.toSet() - targetId
        require(sources.isNotEmpty()) { "Select at least one other payee to merge" }
        val sourceRows = payees.filter { it.id in sources }
        require(sourceRows.size == sources.size) { "One or more payees no longer exist" }
        require(sourceRows.all { it.transferAccountId == null }) { "Transfer payees cannot be merged" }
        val messages = mutableListOf<CrdtMessage>()
        sources.sorted().forEach { id ->
            messages += fields("payee_mapping", id, mapOf("targetId" to targetId))
            messages += fields("payees", id, mapOf("tombstone" to 1))
        }
        persist(messages)
    }

    /** Delete an account and its owned transfer payee together, matching Actual's reference model. */
    @Synchronized
    fun deleteAccount(id: String) {
        require(database.fetchAccounts().any { it.id == id }) { "Account no longer exists" }
        val transferPayees = database.fetchPayees().filter { it.transferAccountId == id }
        val messages = mutableListOf<CrdtMessage>()
        transferPayees.forEach { messages += fields("payees", it.id, mapOf("tombstone" to 1)) }
        messages += fields("accounts", id, mapOf("tombstone" to 1))
        persist(messages)
    }

    /** Delete every category in the group, then the group itself, as one CRDT batch. */
    @Synchronized
    fun deleteCategoryGroup(id: String) {
        val group = database.fetchCategoryGroups().firstOrNull { it.id == id } ?: error("Category group no longer exists")
        require(!group.isIncome) { "The income category group cannot be deleted" }
        val messages = mutableListOf<CrdtMessage>()
        group.categories.forEach { messages += fields("categories", it.id, mapOf("tombstone" to 1)) }
        messages += fields("category_groups", id, mapOf("tombstone" to 1))
        persist(messages)
    }

    /** Move a category before another category in the same group, or to the end when beforeId is null. */
    @Synchronized
    fun moveCategory(id: String, beforeId: String?) {
        val groups = database.fetchCategoryGroups()
        val group = groups.firstOrNull { it.categories.any { category -> category.id == id } }
            ?: error("Category no longer exists")
        require(beforeId == null || group.categories.any { it.id == beforeId }) { "Categories can only be reordered within their group" }
        if (beforeId == id) return
        val positions = group.categories.filterNot { it.id == id }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { SortOrder.Position(it.id, it.sortOrder) }
        val placement = SortOrder.shove(positions, beforeId)
        val messages = mutableListOf<CrdtMessage>()
        placement.moved.forEach { messages += fields("categories", it.id, mapOf("sort_order" to it.sortOrder)) }
        messages += fields("categories", id, mapOf("sort_order" to placement.sortOrder))
        persist(messages)
    }

    /** Move a category group before another group, or to the end when beforeId is null. */
    @Synchronized
    fun moveCategoryGroup(id: String, beforeId: String?) {
        val groups = database.fetchCategoryGroups()
        val moving = groups.firstOrNull { it.id == id } ?: error("Category group no longer exists")
        require(!moving.isIncome) { "The income category group has a fixed position" }
        require(beforeId == null || groups.any { it.id == beforeId && !it.isIncome }) { "Invalid category group destination" }
        if (beforeId == id) return
        val positions = groups.filterNot { it.isIncome || it.id == id }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { SortOrder.Position(it.id, it.sortOrder) }
        val placement = SortOrder.shove(positions, beforeId)
        val messages = mutableListOf<CrdtMessage>()
        placement.moved.forEach { messages += fields("category_groups", it.id, mapOf("sort_order" to it.sortOrder)) }
        messages += fields("category_groups", id, mapOf("sort_order" to placement.sortOrder))
        persist(messages)
    }

    fun setPreference(id: String, value: String?) = update("preferences", id, mapOf("value" to value))
    fun setNote(id: String, note: String) = update("notes", id, mapOf("note" to note))
    fun saveRule(rule: Rule) = update("rules", rule.id, mapOf(
        "stage" to rule.storedStage, "conditions_op" to rule.conditionsOp.name.lowercase(),
        "conditions" to rule.conditionsJson, "actions" to rule.actionsJson, "tombstone" to 0,
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
            val payeeId = payee?.id ?: idFactory().also { newId ->
                messages += fields("payees", newId, linkedMapOf("name" to "Starting Balance", "transfer_acct" to null, "tombstone" to 0))
                messages += fields("payee_mapping", newId, linkedMapOf("targetId" to newId))
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
        if (messages.isEmpty()) return
        database.applyLocalMessages(messages)
        database.saveClock(ActualBudgetDatabase.ClockRecord(clock.current().toString(), database.deriveMerkleFromMessageLog().root))
        onWrite()
    }

    private fun requiredName(value: String) = value.trim().also { require(it.isNotEmpty()) { "Name cannot be empty" } }
    private fun flag(value: Boolean) = if (value) 1 else 0

    companion object {
        private val allowedFields = mapOf(
            "accounts" to setOf("name", "closed", "offbudget", "tombstone", "sort_order"),
            "categories" to setOf("name", "hidden", "cat_group", "tombstone", "sort_order", "goal_def", "template_settings"),
            "category_groups" to setOf("name", "hidden", "tombstone", "sort_order"),
            "payees" to setOf("name", "tombstone"),
            "preferences" to setOf("value"), "notes" to setOf("note"),
            "rules" to setOf("stage", "conditions_op", "conditions", "actions", "tombstone"),
        )
    }
}
