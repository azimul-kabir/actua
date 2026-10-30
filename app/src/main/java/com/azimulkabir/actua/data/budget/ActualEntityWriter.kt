package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.rules.CategoryLearning
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.CrdtValue
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.data.sync.HybridLogicalClock
import com.azimulkabir.actua.data.reports.SavedReportRow
import java.util.UUID

/** CRDT mutation path shared by account/category/group/payee menu actions. */
class ActualEntityWriter(
    private val database: ActualBudgetDatabase,
    nodeId: String = HybridLogicalClock.generateNodeId(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val onWrite: () -> Unit = {},
) {
    private val clock = HybridLogicalClock(nodeId, nowMillis = nowMillis, highWater = database::messageLogHighWater)

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
    fun setAccountType(id: String, type: String) = update("accounts", id, mapOf("type" to type))
    fun setAccountGroup(id: String, groupId: String?) {
        if (groupId != null) {
            require(database.fetchAccountGroups().any { it.id == groupId }) { "Account group no longer exists" }
        }
        update("accounts", id, mapOf("account_group_id" to groupId))
    }
    /**
     * Stores a sync outcome; with [balanceCurrent] (integer cents) also Actual's
     * `updateAccountBalance`, written after every successful download that reports a balance.
     */
    fun recordBankSyncStatus(id: String, status: String, syncedAt: String? = null, balanceCurrent: Long? = null) = update(
        "accounts", id, bankSyncStatusCells(status, syncedAt, balanceCurrent),
    )

    /** The `accounts` cells [recordBankSyncStatus] writes, for an import batch to include. */
    fun bankSyncStatusCells(status: String, syncedAt: String? = null, balanceCurrent: Long? = null): Map<String, Any?> = buildMap {
        put("bank_sync_status", status)
        if (syncedAt != null) put("last_sync", syncedAt)
        if (balanceCurrent != null) put("balance_current", balanceCurrent)
    }

    /** Cells for one synced row, so another writer's batch can commit them together with its own. */
    data class Cells(val dataset: String, val row: String, val values: Map<String, Any?>)

    /**
     * Actual's first-sync opening row (`processBankSyncDownload` with `getStartingBalancePayee`): a
     * cleared `starting_balance_flag` row for [amountCents] on [date], paid by "Starting Balance"
     * and, on budget, categorized as the "Starting Balances" (or first) income category.
     */
    @Synchronized
    fun addStartingBalance(accountId: String, amountCents: Long, date: Int) {
        persist(startingBalanceCells(accountId, amountCents, date).flatMap { fields(it.dataset, it.row, it.values) })
    }

    /** The cells [addStartingBalance] writes, for an import batch to include (#1007). */
    @Synchronized
    fun startingBalanceCells(accountId: String, amountCents: Long, date: Int): List<Cells> {
        val offBudget = database.fetchAccounts().firstOrNull { it.id == accountId }?.offBudget ?: false
        return startingBalanceCells(accountId, offBudget, amountCents, date)
    }

    private fun startingBalanceCells(accountId: String, offBudget: Boolean, amountCents: Long, date: Int): List<Cells> {
        val cells = mutableListOf<Cells>()
        val payee = database.findPayeeByName("Starting Balance")
        val payeeId = payee?.id ?: idFactory().also { newId ->
            cells += Cells("payees", newId, linkedMapOf("name" to "Starting Balance", "transfer_acct" to null, "tombstone" to 0))
            cells += Cells("payee_mapping", newId, linkedMapOf("targetId" to newId))
        }
        val category = if (offBudget) null else database.fetchCategoryGroups().flatMap { it.categories }
            .filter { it.isIncome }.let { rows -> rows.firstOrNull { it.name.equals("Starting Balances", true) } ?: rows.firstOrNull() }
        cells += Cells("transactions", idFactory(), linkedMapOf(
            "acct" to accountId, "date" to date, "description" to payeeId,
            "category" to category?.id, "amount" to amountCents, "notes" to null,
            "cleared" to 1, "reconciled" to 0, "transferred_id" to null, "isParent" to 0,
            "isChild" to 0, "parent_id" to null, "tombstone" to 0, "sort_order" to nowMillis().toDouble(),
            "imported_description" to null, "schedule" to null, "starting_balance_flag" to 1))
        return cells
    }

    /**
     * Links an existing Actual account to a discovered provider account. Like Actual, a GoCardless
     * requisition is kept as a `banks` row (`bank_id` = requisition id) that `accounts.bank` points
     * to; the row is reused when the same requisition is linked again. Enable Banking keeps one row per
     * account (`bank_id` = its account id) and names it after the institution, as Actual's
     * `linkEnableBankingAccount` does; [bankName] is that name.
     */
    @Synchronized
    fun linkBankAccount(
        id: String, externalAccountId: String, source: String, requisitionId: String? = null,
        bankName: String? = null,
        /** SimpleFIN links get a `banks` row even when the org reports no domain or id. */
        createBank: Boolean = requisitionId != null,
    ) {
        require(id.isNotBlank() && externalAccountId.isNotBlank() && source.isNotBlank())
        val messages = mutableListOf<CrdtMessage>()
        val account = linkedMapOf<String, Any?>("account_id" to externalAccountId, "account_sync_source" to source)
        if (createBank) {
            // Actual's `findOrCreateBank`: reuse a row only when both the bank id and name match.
            account["bank"] = database.findBankId(requisitionId, bankName) ?: idFactory().also { bankId ->
                messages += fields("banks", bankId, linkedMapOf("bank_id" to requisitionId, "name" to bankName, "tombstone" to 0))
            }
        }
        messages += fields("accounts", id, account)
        persist(messages)
    }

    /**
     * Actual's `unlinkAccount`: clears the link, the provider balances and the sync status, and keeps
     * `last_sync`. Returns the GoCardless requisition to remove from the server once no account
     * uses its `banks` row any more, else null.
     */
    @Synchronized
    fun unlinkBankAccount(id: String): String? {
        val link = database.fetchBankLink(id) ?: return null
        // Actual skips an account without a `bank`; Actua still clears it so SimpleFIN links made
        // before banks rows were written (#1004) can be unlinked.
        persist(fields("accounts", id, linkedMapOf<String, Any?>("account_id" to null, "bank" to null).apply {
            link.balanceColumns.forEach { put(it, null) }
            put("account_sync_source", null); put("bank_sync_status", null)
        }))
        val bankRowId = link.bankRowId ?: return null
        return if (link.source == "goCardless") database.unusedBankRequisition(bankRowId) else null
    }
    /**
     * Budgets created by Actua before #716 were uploaded without a dashboard page, and Actual's Reports
     * screen needs one. When no live page exists, create Actual's `Main` page through the message log:
     * existing widgets move onto it, and an empty dashboard gets Actual's default widgets. The ids are
     * fixed, so devices repairing the same budget offline write the same rows instead of two pages.
     * Returns whether anything was written.
     */
    @Synchronized
    fun ensureDashboardPage(): Boolean {
        if (!database.dashboardPagesSupported() || database.fetchDashboardPages().isNotEmpty()) return false
        val messages = mutableListOf<CrdtMessage>()
        messages += fields("dashboard_pages", DEFAULT_DASHBOARD_PAGE_ID, linkedMapOf("name" to "Main", "tombstone" to 0))
        val widgets = database.liveDashboardWidgetIds()
        if (widgets.isNotEmpty()) {
            widgets.forEach { messages += fields("dashboard", it, linkedMapOf("dashboard_page_id" to DEFAULT_DASHBOARD_PAGE_ID)) }
        } else {
            BlankBudgetFactory.DEFAULT_DASHBOARD.forEachIndexed { index, widget ->
                messages += fields("dashboard", defaultDashboardWidgetId(index), linkedMapOf(
                    "type" to widget.type, "width" to widget.width, "height" to widget.height,
                    "x" to widget.x, "y" to widget.y, "meta" to widget.meta, "tombstone" to 0,
                    "dashboard_page_id" to DEFAULT_DASHBOARD_PAGE_ID,
                ))
            }
        }
        persist(messages)
        return true
    }

    /** Rejects a name another category in the same group has, hidden ones included, as Actual does. */
    @Synchronized
    fun renameCategory(id: String, name: String) {
        val clean = requiredName(name)
        val group = database.fetchCategoryGroups().firstOrNull { group -> group.categories.any { it.id == id } }
            ?: error("Category no longer exists")
        require(group.categories.none { it.id != id && it.name.equals(clean, true) }) { duplicateCategoryMessage(clean) }
        update("categories", id, mapOf("name" to clean))
    }
    fun setCategoryHidden(id: String, hidden: Boolean) = update("categories", id, mapOf("hidden" to flag(hidden)))
    fun setCategoryTarget(id: String, goalDef: String?) = update("categories", id, mapOf(
        "goal_def" to goalDef, "template_settings" to "{\"source\":\"ui\"}",
    ))

    fun setCategoryNoteTarget(id: String, goalDef: String?) = update("categories", id, mapOf(
        "goal_def" to goalDef, "template_settings" to "{\"source\":\"notes\"}",
    ))

    /** Writes a note-template refresh (#855) in one batch: refreshed definitions and cleared ones. */
    @Synchronized
    fun refreshNoteTemplates(goalDefs: Map<String, String?>, resets: Set<String>) {
        val messages = mutableListOf<CrdtMessage>()
        goalDefs.forEach { (id, goalDef) ->
            messages += fields("categories", id, mapOf("goal_def" to goalDef, "template_settings" to "{\"source\":\"notes\"}"))
        }
        resets.forEach { id -> messages += fields("categories", id, mapOf("goal_def" to null)) }
        persist(messages)
    }

    /**
     * Rewrites every category's `cleanup_def` in one atomic batch, matching Actual's
     * `storeNoteCleanups()`: group upserts (create/un-tombstone) and orphan tombstones from
     * one note re-scan land together with the category rows that reference them.
     */
    @Synchronized
    fun refreshCleanupDefinitions(
        categoryCleanupDefs: Map<String, String?>,
        groupUpserts: Map<String, String>,
        orphanGroupIds: Set<String>,
    ) {
        val messages = mutableListOf<CrdtMessage>()
        groupUpserts.forEach { (id, name) -> messages += fields("cleanup_groups", id, mapOf("name" to name, "tombstone" to 0)) }
        orphanGroupIds.forEach { id -> messages += fields("cleanup_groups", id, mapOf("tombstone" to 1)) }
        categoryCleanupDefs.forEach { (id, cleanupDef) -> messages += fields("categories", id, mapOf("cleanup_def" to cleanupDef)) }
        persist(messages)
    }

    /**
     * Actual's `category-delete`. Without [transferId] the category is only tombstoned. With one,
     * as a single batch: an expense category's budgeted amount is added to the target's in every
     * month of the budget range (upstream `doTransfer`), every `category_mapping` row resolving to
     * the category and then its own row are forwarded to the target, and the category is tombstoned.
     */
    @Synchronized
    fun deleteCategory(id: String, transferId: String? = null) {
        if (transferId == null) {
            update("categories", id, mapOf("tombstone" to 1))
            return
        }
        val categories = database.fetchCategoryGroups().flatMap { it.categories }
        val category = categories.firstOrNull { it.id == id } ?: error("Category no longer exists")
        val target = categories.firstOrNull { it.id == transferId } ?: error("The transfer category no longer exists")
        require(target.id != category.id) { "Choose a different category to transfer to" }
        require(target.isIncome == category.isIncome) { "Cannot transfer between income and expense categories" }
        val messages = mutableListOf<CrdtMessage>()
        if (!category.isIncome) {
            val range = database.budgetRange()
            generateSequence(range.start) { month -> month.plusMonths(1).takeIf { it <= range.endInclusive } }
                .forEach { month ->
                    val source = database.budgetCell(month.toString(), id) ?: error("Budget table is missing")
                    val cell = database.budgetCell(month.toString(), transferId) ?: error("Budget table is missing")
                    if (!cell.exists) {
                        messages += fields(cell.table, cell.rowId, linkedMapOf("month" to cell.month, "category" to cell.categoryId))
                    }
                    messages += fields(cell.table, cell.rowId, mapOf("amount" to source.amountCents + cell.amountCents))
                }
        }
        database.categoryMappingsTo(id).forEach { mapping ->
            messages += fields("category_mapping", mapping, mapOf("transferId" to transferId))
        }
        messages += fields("category_mapping", id, mapOf("transferId" to transferId))
        messages += fields("categories", id, mapOf("tombstone" to 1))
        persist(messages)
    }
    /** Rejects a name another live group has, as Actual's `updateCategoryGroup` does. */
    @Synchronized
    fun renameCategoryGroup(id: String, name: String) {
        val clean = requiredName(name)
        require(database.fetchCategoryGroups().none { it.id != id && it.name.equals(clean, true) }) {
            "A category group named \"$clean\" already exists"
        }
        update("category_groups", id, mapOf("name" to clean))
    }
    fun setCategoryGroupHidden(id: String, hidden: Boolean) = update("category_groups", id, mapOf("hidden" to flag(hidden)))
    fun renamePayee(id: String, name: String) = updateOrdinaryPayee(id, "name", requiredName(name))

    /**
     * Ordinary payees may be deleted directly. Transfer payees are account-owned and must
     * disappear only as part of deleting their account so transfer semantics cannot be broken.
     */
    fun deletePayee(id: String) {
        val payee = database.fetchPayees().firstOrNull { it.id == id } ?: error("Payee no longer exists")
        require(payee.transferAccountId == null) { "Transfer payees cannot be deleted independently" }
        update("payees", id, mapOf("tombstone" to 1))
    }

    /**
     * Upstream `payees-batch-change` deletions: tombstones every payee in one batch. Transfer payees
     * belong to their account and are refused.
     */
    @Synchronized
    fun deletePayees(ids: Collection<String>) {
        val targets = ids.toSet()
        require(targets.isNotEmpty()) { "Select at least one payee" }
        val payees = database.fetchPayees().filter { it.id in targets }
        require(payees.size == targets.size) { "One or more payees no longer exist" }
        require(payees.all { it.transferAccountId == null }) { "Transfer payees cannot be deleted independently" }
        persist(targets.sorted().flatMap { id -> fields("payees", id, mapOf("tombstone" to 1)) })
    }

    /** Payee `favorite` / `learn_categories` (integer booleans), as the Payees page writes them. */
    fun setPayeeFavorite(id: String, favorite: Boolean) = updateOrdinaryPayee(id, "favorite", flag(favorite))
    fun setPayeeLearnCategories(id: String, learn: Boolean) = updateOrdinaryPayee(id, "learn_categories", flag(learn))

    private fun updateOrdinaryPayee(id: String, column: String, value: Any?) {
        val payee = database.fetchPayees().firstOrNull { it.id == id } ?: error("Payee no longer exists")
        require(payee.transferAccountId == null) { "Transfer payees can't be edited" }
        update("payees", id, mapOf(column to value))
    }

    /** Synced `learn-categories` preference, stored as the string `"true"`/`"false"` like the PWA. */
    fun setLearnCategoriesEnabled(enabled: Boolean) =
        update("preferences", CategoryLearning.PREFERENCE_ID, mapOf("value" to enabled.toString()))

    /**
     * Actual payee merge is mapping-based: old references resolve to the surviving payee. As in
     * upstream `mergePayees`, mappings that already point at a merged payee (earlier merges into
     * it) are re-pointed too, because mapping reads are a single hop.
     */
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
        (database.payeeMappingsTargeting(sources).toSortedSet() + sources.sorted()).forEach { id ->
            messages += fields("payee_mapping", id, mapOf("targetId" to targetId))
        }
        sources.sorted().forEach { id -> messages += fields("payees", id, mapOf("tombstone" to 1)) }
        persist(messages)
    }

    enum class CloseOutcome { DELETED, FORCE_DELETED, CLOSED }

    /**
     * Actual's `closeAccount` as one CRDT batch. Bank sync is unlinked first. An account with no
     * transactions is deleted. [forced] deletes the account, its transfer payee and every
     * transaction in it, detaching the other leg of each transfer. Otherwise the account is marked
     * closed, and a non-zero balance moves to [transferAccountId] as a `Closing account` transfer
     * dated today; an on-budget to off-budget transfer needs [categoryId].
     * Returns null when the account is missing or already closed, where Actual does nothing.
     */
    @Synchronized
    fun closeAccount(
        id: String,
        transferAccountId: String? = null,
        categoryId: String? = null,
        forced: Boolean = false,
    ): CloseOutcome? {
        val accounts = database.fetchAccounts()
        val account = accounts.firstOrNull { it.id == id } ?: return null
        if (account.closed) return null
        val state = database.fetchAccountCloseState(id) ?: return null
        val messages = mutableListOf<CrdtMessage>()
        if (state.bankLinked) {
            messages += fields("accounts", id, linkedMapOf<String, Any?>(
                "account_id" to null, "bank" to null,
            ).apply {
                state.balanceColumns.forEach { put(it, null) }
                put("account_sync_source", null); put("bank_sync_status", null)
            })
        }
        val outcome = when {
            state.transactions.isEmpty() -> {
                messages += fields("accounts", id, mapOf("tombstone" to 1))
                CloseOutcome.DELETED
            }
            forced -> {
                val transferPayeeId = state.transferPayeeId ?: error("Transfer payee for ${account.name} not found")
                state.transactions.forEach { row ->
                    row.transferId?.let {
                        messages += fields("transactions", it, linkedMapOf("description" to null, "transferred_id" to null))
                    }
                    messages += fields("transactions", row.id, mapOf("tombstone" to 1))
                }
                messages += fields("accounts", id, mapOf("tombstone" to 1))
                messages += fields("payees", transferPayeeId, mapOf("tombstone" to 1))
                CloseOutcome.FORCE_DELETED
            }
            else -> {
                val balance = account.balanceCents
                require(balance == 0L || transferAccountId != null) { "Choose an account to transfer the balance to" }
                require(transferAccountId != id) { "The balance can't be transferred to the account being closed" }
                messages += fields("accounts", id, mapOf("closed" to 1))
                if (balance != 0L && transferAccountId != null) {
                    val target = accounts.firstOrNull { it.id == transferAccountId && !it.closed }
                        ?: error("The transfer account is closed or no longer exists")
                    val needsCategory = !account.offBudget && target.offBudget
                    require(!needsCategory || categoryId != null) {
                        "Choose a category for the transfer to an off-budget account"
                    }
                    if (needsCategory) require(database.fetchCategoryGroups().any { group -> group.categories.any { it.id == categoryId } }) {
                        "The category no longer exists"
                    }
                    messages += closingTransfer(account, target, balance, if (needsCategory) categoryId else null)
                }
                CloseOutcome.CLOSED
            }
        }
        persist(messages)
        return outcome
    }

    /**
     * Actual's `transaction-add` of `-balance` to the target's transfer payee, plus the counterpart
     * leg its transfer hook inserts. The category is kept only when budget types differ, as
     * `clearCategory` does.
     */
    private fun closingTransfer(account: ActualAccount, target: ActualAccount, balance: Long, categoryId: String?): List<CrdtMessage> {
        val targetPayee = database.transferPayeeId(target.id) ?: error("Transfer payee for ${target.name} not found")
        val sourcePayee = database.transferPayeeId(account.id) ?: error("Transfer payee for ${account.name} not found")
        val sourceId = idFactory(); val targetId = idFactory()
        val date = DayDate.today().yyyymmdd; val sortOrder = nowMillis().toDouble()
        fun leg(acct: String, payee: String, amount: Long, category: String?, cleared: Boolean, transfer: String) = linkedMapOf<String, Any?>(
            "acct" to acct, "date" to date, "description" to payee, "category" to category, "amount" to amount,
            "notes" to CLOSING_ACCOUNT_NOTE, "cleared" to flag(cleared), "reconciled" to 0,
            "transferred_id" to transfer, "isParent" to 0, "isChild" to 0, "parent_id" to null,
            "tombstone" to 0, "sort_order" to sortOrder, "imported_description" to null, "schedule" to null,
            "starting_balance_flag" to 0,
        )
        return fields("transactions", sourceId, leg(account.id, targetPayee, -balance, categoryId, cleared = true, transfer = targetId)) +
            fields("transactions", targetId, leg(target.id, sourcePayee, balance, null, cleared = false, transfer = sourceId))
    }

    /**
     * Deletes a category group by moving all its child categories to [targetGroupId],
     * then tombstoning the group itself, as one atomic CRDT batch.
     */
    @Synchronized
    fun deleteCategoryGroup(id: String, targetGroupId: String) {
        require(id != targetGroupId) { "Source and target category groups must be different" }
        val groups = database.fetchCategoryGroups()
        val source = groups.firstOrNull { it.id == id } ?: error("Category group no longer exists")
        val target = groups.firstOrNull { it.id == targetGroupId } ?: error("Target category group no longer exists")
        require(!source.isIncome) { "The income category group cannot be deleted" }
        require(!target.isIncome) { "Cannot move categories to the income category group" }

        val existingTargetNames = target.categories.map { it.name.trim().lowercase() }.toMutableSet()
        val movingCategories = source.categories.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        movingCategories.forEach { category ->
            val normalized = category.name.trim().lowercase()
            require(normalized !in existingTargetNames) {
                duplicateCategoryMessage(category.name)
            }
            existingTargetNames += normalized
        }

        var currentOrder = target.categories.maxOfOrNull { it.sortOrder } ?: 0.0
        val messages = mutableListOf<CrdtMessage>()
        movingCategories.forEach { category ->
            currentOrder += SortOrder.INCREMENT
            messages += fields("categories", category.id, mapOf("cat_group" to targetGroupId, "sort_order" to currentOrder))
        }
        messages += fields("category_groups", id, mapOf("tombstone" to 1))
        persist(messages)
    }

    /**
     * Move a category before another category within [groupId] (or to the end when beforeId is
     * null). Like Actual's `moveCategory` it writes only `sort_order` and, when the group changes,
     * `cat_group`: the category keeps its own hidden and income flags.
     */
    @Synchronized
    fun moveCategory(id: String, groupId: String, beforeId: String?) {
        val groups = database.fetchCategoryGroups()
        val category = groups.flatMap { it.categories }.firstOrNull { it.id == id }
            ?: error("Category no longer exists")
        val target = groups.firstOrNull { it.id == groupId } ?: error("Category group no longer exists")
        require(beforeId == null || target.categories.any { it.id == beforeId }) { "Categories can only be reordered within their group" }
        require(target.categories.none { it.id != id && it.name.equals(category.name, true) }) {
            duplicateCategoryMessage(category.name)
        }
        if (beforeId == id) return
        val positions = target.categories.filterNot { it.id == id }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { SortOrder.Position(it.id, it.sortOrder) }
        val placement = SortOrder.shove(positions, beforeId)
        val messages = mutableListOf<CrdtMessage>()
        placement.moved.forEach { messages += fields("categories", it.id, mapOf("sort_order" to it.sortOrder)) }
        val categoryFields = linkedMapOf<String, Any?>("sort_order" to placement.sortOrder)
        if (category.groupId != groupId) categoryFields["cat_group"] = groupId
        messages += fields("categories", id, categoryFields)
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

    /** Move an account before another account, or to the end when beforeId is null, optionally updating its account group. */
    @Synchronized
    fun moveAccount(id: String, beforeId: String?, newGroupId: String? = null, updateGroupId: Boolean = false) {
        val accounts = database.fetchAccounts()
        require(accounts.any { it.id == id }) { "Account no longer exists" }
        require(beforeId == null || accounts.any { it.id == beforeId }) { "Invalid account destination" }
        if (updateGroupId && newGroupId != null) {
            require(database.fetchAccountGroups().any { it.id == newGroupId }) { "Account group no longer exists" }
        }
        val targetAccount = accounts.first { it.id == id }
        val groupNeedsUpdate = updateGroupId && targetAccount.groupId != newGroupId

        if (beforeId == id) {
            if (groupNeedsUpdate) {
                setAccountGroup(id, newGroupId)
            }
            return
        }
        val positions = accounts.filterNot { it.id == id }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { SortOrder.Position(it.id, it.sortOrder) }
        val placement = SortOrder.shove(positions, beforeId)
        val messages = mutableListOf<CrdtMessage>()
        placement.moved.forEach { messages += fields("accounts", it.id, mapOf("sort_order" to it.sortOrder)) }
        val accountFields = mutableMapOf<String, Any?>("sort_order" to placement.sortOrder)
        if (groupNeedsUpdate) {
            accountFields["account_group_id"] = newGroupId
        }
        messages += fields("accounts", id, accountFields)
        persist(messages)
    }

    /** Move an account group before another group, or to the end when beforeId is null. */
    @Synchronized
    fun moveAccountGroup(id: String, beforeId: String?) {
        val groups = database.fetchAccountGroups()
        require(groups.any { it.id == id }) { "Account group no longer exists" }
        require(beforeId == null || groups.any { it.id == beforeId }) { "Invalid account group destination" }
        if (beforeId == id) return
        val positions = groups.filterNot { it.id == id }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { SortOrder.Position(it.id, it.sortOrder) }
        val placement = SortOrder.shove(positions, beforeId)
        val messages = mutableListOf<CrdtMessage>()
        placement.moved.forEach { messages += fields("account_groups", it.id, mapOf("sort_order" to it.sortOrder)) }
        messages += fields("account_groups", id, mapOf("sort_order" to placement.sortOrder))
        persist(messages)
    }

    /** Rejects a name another live account group has. */
    @Synchronized
    fun renameAccountGroup(id: String, name: String) {
        val clean = requiredName(name)
        val groups = database.fetchAccountGroups()
        require(groups.any { it.id == id }) { "Account group no longer exists" }
        require(groups.none { it.id != id && it.name.equals(clean, true) }) {
            "An account group named \"$clean\" already exists"
        }
        update("account_groups", id, mapOf("name" to clean))
    }

    /**
     * Actual's `account-group-delete`: unlinks associated accounts by clearing `account_group_id`,
     * and tombstones the account group in one atomic batch.
     */
    @Synchronized
    fun deleteAccountGroup(id: String) {
        val groups = database.fetchAccountGroups()
        require(groups.any { it.id == id }) { "Account group no longer exists" }
        val accounts = database.fetchAccounts().filter { it.groupId == id }
        val messages = mutableListOf<CrdtMessage>()
        accounts.forEach { account ->
            messages += fields("accounts", account.id, mapOf("account_group_id" to null))
        }
        messages += fields("account_groups", id, mapOf("tombstone" to 1))
        persist(messages)
    }

    fun setPreference(id: String, value: String?) = update("preferences", id, mapOf("value" to value))
    fun setNote(id: String, note: String) = update("notes", id, mapOf("note" to note))
    fun saveRule(rule: Rule) = update("rules", rule.id, ruleFields(rule))

    private fun ruleFields(rule: Rule): Map<String, Any?> = linkedMapOf(
        "stage" to rule.storedStage, "conditions_op" to rule.conditionsOp.name.lowercase(),
        "conditions" to rule.conditionsJson, "actions" to rule.actionsJson, "tombstone" to 0,
    )
    fun deleteRule(id: String) = update("rules", id, mapOf("tombstone" to 1))

    /**
     * Upstream `updateCategoryRules` for transactions that were just added with a category or had
     * one set. Honours the synced `learn-categories` preference and `payees.learn_categories`;
     * every created or updated rule is written in one batch.
     */
    @Synchronized
    fun learnCategories(touched: List<ActualTransaction>) {
        val rows = touched.filter { !it.isParent && it.payeeId != null }
        if (rows.isEmpty() || !database.rulesSupported()) return
        if (!CategoryLearning.enabled(database.learnCategoriesPreference())) return
        val oldest = DayDate.fromYyyymmdd(rows.minOf(ActualTransaction::date)) ?: return
        val register = database.fetchCategoryLearningRegister(
            rows.mapNotNull(ActualTransaction::payeeId),
            oldest.addingDays(-CategoryLearning.WINDOW_DAYS).yyyymmdd,
            DayDate.today().addingDays(CategoryLearning.WINDOW_DAYS).yyyymmdd,
        )
        val learned = CategoryLearning.categoriesToSet(rows.map { CategoryLearning.Touched(it.id, it.payeeId) }, register)
        val rules = CategoryLearning.rulesToSave(learned, database.fetchRules(), idFactory)
        persist(rules.flatMap { rule -> fields("rules", rule.id, ruleFields(rule)) })
    }

    /** PWA/iOS local-account shape: account + transfer payee + optional opening transaction. */
    @Synchronized
    fun createAccount(
        name: String,
        offBudget: Boolean,
        startingBalanceCents: Long,
        type: String = "checking",
        groupId: String? = null,
    ): String {
        val clean = requiredName(name)
        require(database.fetchAccounts().none { it.name.equals(clean, true) }) { "An account named \"$clean\" already exists" }
        if (groupId != null) {
            require(database.fetchAccountGroups().any { it.id == groupId }) { "Account group no longer exists" }
        }
        val accountId = idFactory(); val transferPayeeId = idFactory()
        val messages = mutableListOf<CrdtMessage>()
        val accountValues = linkedMapOf<String, Any?>(
            "name" to clean, "type" to type,
            "offbudget" to flag(offBudget), "closed" to 0, "tombstone" to 0, "sort_order" to nowMillis(),
        )
        if (groupId != null) {
            accountValues["account_group_id"] = groupId
        }
        messages += fields("accounts", accountId, accountValues)
        messages += fields("payees", transferPayeeId, linkedMapOf("name" to "", "transfer_acct" to accountId, "tombstone" to 0))
        messages += fields("payee_mapping", transferPayeeId, linkedMapOf("targetId" to transferPayeeId))
        if (startingBalanceCents != 0L) {
            messages += startingBalanceCells(accountId, offBudget, startingBalanceCents, DayDate.today().yyyymmdd)
                .flatMap { fields(it.dataset, it.row, it.values) }
        }
        persist(messages); return accountId
    }

    @Synchronized
    fun createAccountGroup(name: String): String {
        val clean = requiredName(name)
        val groups = database.fetchAccountGroups()
        require(groups.none { it.name.equals(clean, true) }) { "An account group named \"$clean\" already exists" }
        val id = idFactory()
        val sort = (groups.maxOfOrNull { it.sortOrder } ?: 0.0) + SortOrder.INCREMENT
        persist(fields("account_groups", id, linkedMapOf("name" to clean, "sort_order" to sort, "tombstone" to 0)))
        return id
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
            // Visible itself, as Actual creates it; a hidden group hides it through the group.
            "is_income" to flag(group.isIncome), "hidden" to 0, "tombstone" to 0,
            "sort_order" to placement.sortOrder))
        messages += fields("category_mapping", id, linkedMapOf("transferId" to id))
        placement.moved.forEach { messages += fields("categories", it.id, linkedMapOf("sort_order" to it.sortOrder)) }
        persist(messages); return id
    }

    @Synchronized
    fun saveCustomReport(report: SavedReportRow): String {
        val cleanName = report.name.trim().ifEmpty { "Untitled report" }
        val id = report.id.ifBlank { idFactory() }
        val values = linkedMapOf<String, Any?>(
            "name" to cleanName,
            "start_date" to report.startDate,
            "end_date" to report.endDate,
            "date_static" to flag(report.dateStatic),
            "date_range" to report.dateRange,
            "mode" to report.mode,
            "group_by" to report.groupBy,
            "balance_type" to report.balanceType,
            "show_empty" to flag(report.showEmpty),
            "show_offbudget" to flag(report.showOffBudget),
            "show_hidden" to flag(report.showHidden),
            "show_uncategorized" to flag(report.showUncategorized),
            "selected_categories" to report.selectedCategories,
            "graph_type" to report.graphType,
            "conditions" to (report.conditions ?: "[]"),
            "conditions_op" to (report.conditionsOp ?: "and"),
            "interval" to report.interval,
            "tombstone" to 0,
            "include_current" to flag(report.includeCurrent),
            "sort_by" to report.sortBy,
            "trim_intervals" to flag(report.trimIntervals),
        )
        persist(fields("custom_reports", id, values))
        return id
    }

    @Synchronized
    fun deleteCustomReport(id: String) {
        val messages = mutableListOf<CrdtMessage>()
        messages += fields("custom_reports", id, linkedMapOf("tombstone" to 1))
        database.findDashboardWidgetsForCustomReport(id).forEach { widgetId ->
            messages += fields("dashboard", widgetId, linkedMapOf("tombstone" to 1))
        }
        persist(messages)
    }

    @Synchronized
    fun addDashboardWidget(pageId: String?, type: String, metaJson: String?, width: Int = 12, height: Int = 2): String {
        val id = idFactory()
        val nextY = database.nextDashboardWidgetY(pageId)
        val values = linkedMapOf<String, Any?>(
            "type" to type,
            "width" to width,
            "height" to height,
            "x" to 0,
            "y" to nextY,
            "meta" to metaJson,
            "tombstone" to 0,
            "dashboard_page_id" to pageId,
        )
        persist(fields("dashboard", id, values))
        return id
    }

    @Synchronized
    fun deleteDashboardWidget(id: String) {
        persist(fields("dashboard", id, linkedMapOf("tombstone" to 1)))
    }

    @Synchronized
    fun reorderDashboardWidgets(pageId: String?, widgetIds: List<String>) {
        val messages = mutableListOf<CrdtMessage>()
        widgetIds.forEachIndexed { index, widgetId ->
            messages += fields("dashboard", widgetId, linkedMapOf("y" to index * 2, "x" to 0))
        }
        persist(messages)
    }

    @Synchronized
    fun createDashboardPage(name: String): String {
        val clean = requiredName(name)
        val id = idFactory()
        persist(fields("dashboard_pages", id, linkedMapOf("name" to clean, "tombstone" to 0)))
        return id
    }

    @Synchronized
    fun renameDashboardPage(id: String, name: String) {
        val clean = requiredName(name)
        update("dashboard_pages", id, mapOf("name" to clean))
    }

    @Synchronized
    fun deleteDashboardPage(id: String) {
        val messages = mutableListOf<CrdtMessage>()
        messages += fields("dashboard_pages", id, linkedMapOf("tombstone" to 1))
        database.fetchDashboardWidgets(id).forEach {
            messages += fields("dashboard", it.id, linkedMapOf("tombstone" to 1))
        }
        persist(messages)
    }

    /**
     * The synced `prefs/budgetName` message Actual's `savePrefs` sends when a budget is renamed, so other
     * clients' logs agree. Actua doesn't apply `prefs` messages (there is no table), it only logs them.
     */
    @Synchronized
    fun recordBudgetName(name: String) {
        persist(fields("prefs", "budgetName", linkedMapOf("value" to requiredName(name))))
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
    private fun duplicateCategoryMessage(name: String) = "Category \"$name\" already exists in group (it may be hidden)"
    private fun flag(value: Boolean) = if (value) 1 else 0

    companion object {
        /** The note Actual puts on the transfer that empties a closing account. */
        const val CLOSING_ACCOUNT_NOTE = "Closing account"

        internal val DEFAULT_DASHBOARD_PAGE_ID: String =
            UUID.nameUUIDFromBytes("actua:default-dashboard:page".toByteArray()).toString()

        internal fun defaultDashboardWidgetId(index: Int): String =
            UUID.nameUUIDFromBytes("actua:default-dashboard:widget:$index".toByteArray()).toString()

        internal val allowedFields = mapOf(
            "accounts" to setOf("name", "type", "closed", "offbudget", "tombstone", "sort_order", "account_group_id",
                "bank_sync_status", "last_sync", "account_id", "account_sync_source", "bank", "balance_current"),
            "account_groups" to setOf("name", "sort_order", "tombstone"),
            "categories" to setOf("name", "hidden", "cat_group", "tombstone", "sort_order", "goal_def", "template_settings", "cleanup_def"),
            "category_groups" to setOf("name", "hidden", "tombstone", "sort_order"),
            "cleanup_groups" to setOf("name", "tombstone"),
            "payees" to setOf("name", "tombstone", "favorite", "learn_categories"),
            "preferences" to setOf("value"), "notes" to setOf("note"),
            "rules" to setOf("stage", "conditions_op", "conditions", "actions", "tombstone"),
            "custom_reports" to setOf(
                "name", "start_date", "end_date", "date_static", "date_range", "mode", "group_by",
                "balance_type", "show_empty", "show_offbudget", "show_hidden", "show_uncategorized",
                "selected_categories", "graph_type", "conditions", "conditions_op", "metadata",
                "interval", "color_scheme", "tombstone", "include_current", "sort_by", "trim_intervals",
                "show_trend_lines",
            ),
            "dashboard" to setOf("type", "width", "height", "x", "y", "meta", "tombstone", "dashboard_page_id"),
            "dashboard_pages" to setOf("name", "tombstone"),
        )
    }
}
