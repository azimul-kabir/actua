package com.azimulkabir.actua.data

import android.content.Context
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActiveBudgetStore
import com.azimulkabir.actua.data.budget.ActualTransactionForm
import com.azimulkabir.actua.data.budget.ActualTransactionFormService
import com.azimulkabir.actua.data.budget.ActualTransactionType
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.budget.ActualSplitLineForm
import com.azimulkabir.actua.data.budget.ActualEntityWriter
import com.azimulkabir.actua.data.budget.ActualBudgetWriter
import com.azimulkabir.actua.data.budget.BudgetFileManager
import com.azimulkabir.actua.model.Account
import com.azimulkabir.actua.model.BudgetCategory
import com.azimulkabir.actua.model.BudgetGroup
import com.azimulkabir.actua.model.BudgetOverview
import com.azimulkabir.actua.model.BudgetHistory
import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.Type
import com.azimulkabir.actua.model.SplitLine
import com.azimulkabir.actua.model.ReportCategory
import com.azimulkabir.actua.model.ReportMonth
import com.azimulkabir.actua.model.ReportSnapshot
import com.azimulkabir.actua.model.CreditCardConfig
import com.azimulkabir.actua.model.CreditCardCycle
import com.azimulkabir.actua.model.CreditCardStatus
import com.azimulkabir.actua.data.sync.ActualSyncScheduler
import org.json.JSONObject
import com.azimulkabir.actua.data.rules.Rule
import com.azimulkabir.actua.data.rules.RuleChoice
import com.azimulkabir.actua.data.rules.RuleEditorData

class ActuaRepository(context: Context) {
    private val appContext = context.applicationContext
    private val scheduleSync = { ActualSyncScheduler.scheduleMutation(appContext) }
    private val actualDatabase: ActualBudgetDatabase? = BudgetFileManager(context).let { files ->
        val budgets = files.listLocalBudgets()
        val selectedId = ActiveBudgetStore(context).budgetId
        val selected = budgets.firstOrNull { it.id == selectedId } ?: budgets.firstOrNull()
        selected?.let { metadata ->
            runCatching { ActualBudgetDatabase.open(files.databaseFile(metadata.id)) }.getOrNull()
        }
    }
    private val actualWriter = actualDatabase?.let { ActualTransactionWriter(it, onWrite = scheduleSync) }
    private val actualEntities = actualDatabase?.let { ActualEntityWriter(it, onWrite = scheduleSync) }
    private val actualBudgets = actualDatabase?.let { ActualBudgetWriter(it, onWrite = scheduleSync) }
    private val actualForms = actualDatabase?.let { db ->
        ActualTransactionFormService(db, requireNotNull(actualWriter))
    }

    val isUsingActualBudget: Boolean get() = actualDatabase != null

    fun close() {
        actualDatabase?.close()
    }

    fun categoryNames(): List<String> = actualDatabase?.fetchCategoryGroups()
        ?.flatMap { it.categories }
        ?.filterNot { it.hidden }
        ?.map { it.name }
        ?: emptyList()

    fun payeeNames(): List<String> = actualDatabase?.fetchPayees()
        ?.filter { it.transferAccountId == null && it.name != "Unknown" }
        ?.map { it.name }
        ?: emptyList()

    fun rules(): List<Rule> = actualDatabase?.fetchRules().orEmpty()

    fun rulesSupported(): Boolean = actualDatabase?.rulesSupported() == true

    fun scheduleOwnedRuleIds(): Set<String> = actualDatabase?.scheduleOwnedRuleIds().orEmpty()

    fun ruleEditorData(): RuleEditorData {
        val db = actualDatabase ?: return RuleEditorData()
        val groups = db.fetchCategoryGroups()
        val allAccounts = db.fetchAccounts()
        val allPayees = db.fetchPayees()
        val allCategories = groups.flatMap { it.categories }
        val accountNames = allAccounts.associate { it.id to it.name }
        val allNames = (allAccounts.map { it.id to it.name } + allPayees.map { payee ->
            val name = payee.name.takeIf { it.isNotBlank() }
                ?: payee.transferAccountId?.let(accountNames::get)?.let { "Transfer: $it" }
                ?: "Unknown payee"
            payee.id to name
        } +
            allCategories.map { it.id to it.name } + groups.map { it.id to it.name }).toMap()
        return RuleEditorData(
            accounts = allAccounts.filterNot { it.closed }.map { RuleChoice(it.id, it.name) },
            payees = allPayees.filter { it.transferAccountId == null && it.name.isNotBlank() && it.name != "Unknown" }
                .map { RuleChoice(it.id, it.name) },
            categories = groups.filterNot { it.hidden }.flatMap { it.categories }.filterNot { it.hidden }
                .map { RuleChoice(it.id, it.name) },
            categoryGroups = groups.filterNot { it.hidden }.map { RuleChoice(it.id, it.name) },
            names = allNames,
        )
    }

    fun saveRule(rule: Rule): Boolean {
        require(rule.conditions.isNotEmpty()) { "Add at least one condition" }
        require(rule.actions.isNotEmpty()) { "Add at least one action" }
        actualEntities?.saveRule(rule) ?: return false
        return true
    }

    fun deleteRule(ruleId: String): Boolean {
        val db = actualDatabase ?: return false
        require(ruleId !in db.scheduleOwnedRuleIds()) { "This rule belongs to a schedule" }
        actualEntities!!.deleteRule(ruleId)
        return true
    }

    fun budgetGroups(month: String = currentMonth()): List<BudgetGroup> {
        actualDatabase?.let { db ->
            val budget = db.fetchBudgetMonth(month)
            val selectedMonth = java.time.YearMonth.parse(month)
            val histories = (1L..6L).map { offset -> db.fetchBudgetMonth(selectedMonth.minusMonths(offset).toString()) }
            val expenseGroups = (budget.categories + budget.hiddenCategories).groupBy { it.groupId }.values
                .sortedBy { it.first().groupSortOrder }
                .map { rows ->
                    BudgetGroup(rows.first().groupName, rows.sortedBy { it.categorySortOrder }.map {
                        BudgetCategory(
                            it.categoryName,
                            centsToDisplayUnits(it.budgetedCents),
                            centsToDisplayUnits(-it.spentCents),
                            centsToDisplayUnits(it.availableCents),
                            it.budgetedCents,
                            it.categoryId,
                            it.availableCents,
                            it.hidden,
                            -it.spentCents,
                            it.carryoverEnabled,
                            db.fetchNote(it.categoryId),
                            histories.mapNotNull { historyMonth ->
                                (historyMonth.categories + historyMonth.hiddenCategories)
                                    .firstOrNull { row -> row.categoryId == it.categoryId }
                                    ?.let { row -> BudgetHistory(historyMonth.month, row.budgetedCents, row.spentCents) }
                            },
                        )
                    }, hidden = rows.first().groupHidden)
                }
            val incomeGroups = (budget.incomeCategories + budget.hiddenIncomeCategories)
                .groupBy { it.groupName }
                .map { (groupName, rows) ->
                    BudgetGroup(
                        name = groupName,
                        categories = rows.sortedBy { it.sortOrder }.map {
                            BudgetCategory(
                                name = it.categoryName,
                                assigned = centsToDisplayUnits(it.budgetedCents),
                                spent = centsToDisplayUnits(it.receivedCents),
                                actualAvailable = centsToDisplayUnits(it.receivedCents),
                                actualAssignedCents = it.budgetedCents,
                                id = it.categoryId,
                                availableCents = it.receivedCents,
                                hidden = it.hidden,
                                spentCents = -it.receivedCents,
                                note = db.fetchNote(it.categoryId),
                                isIncome = true,
                            )
                        },
                        hidden = rows.first().groupHidden,
                        isIncome = true,
                    )
                }
            return expenseGroups + incomeGroups
        }
        return emptyList()
    }

    fun budgetOverview(month: String = currentMonth()): BudgetOverview {
        actualDatabase?.let { db ->
            val budget = db.fetchBudgetMonth(month)
            return BudgetOverview(
                toBudgetCents = budget.toBudgetCents,
                budgetedCents = budget.categories.sumOf { it.budgetedCents },
                spentCents = budget.categories.sumOf { it.spentCents },
                availableCents = budget.categories.sumOf { it.availableCents },
            )
        }
        return BudgetOverview(null, 0, 0, 0)
    }

    fun accounts(): List<Account> {
        actualDatabase?.let { db ->
            return db.fetchAccounts().map {
                Account(
                    name = it.name,
                    balance = centsToDisplayUnits(it.balanceCents),
                    type = it.type.name.lowercase().replaceFirstChar(Char::uppercase),
                    offBudget = it.offBudget,
                    closed = it.closed,
                    balanceCents = it.balanceCents,
                    id = it.id,
                    clearedCents = it.clearedCents,
                    unclearedCents = it.unclearedCents,
                    reconciledCents = it.reconciledCents,
                    note = db.fetchNote("account-${it.id}"),
                )
            }
        }
        return emptyList()
    }

    fun creditCards(includeClosed: Boolean = false): List<CreditCardStatus> {
        val db = actualDatabase ?: return emptyList()
        val configs = db.fetchCreditCardConfigs()
        return db.fetchAccounts().mapNotNull { account ->
            val config = configs[account.id] ?: return@mapNotNull null
            if (account.closed && !includeClosed) return@mapNotNull null
            val cycle = CreditCardCycle(config.statementDay, config.dueOffsetDays)
            val range = cycle.cycleRange()
            CreditCardStatus(
                account.id, account.name, account.balanceCents, config,
                db.fetchAccountSpend(account.id, range.first.yyyymmdd, range.second.yyyymmdd),
                config.limitCents?.plus(account.balanceCents), account.closed,
            )
        }.sortedWith(compareBy({ it.cycle.daysUntilDue() }, { it.accountName.lowercase() }))
    }

    fun setCreditCard(accountId: String, statementDay: Int?, dueOffsetDays: Int = CreditCardCycle.DEFAULT_DUE_OFFSET_DAYS,
        limitCents: Long? = null): Boolean {
        val db = actualDatabase ?: return false
        require(db.fetchAccounts().any { it.id == accountId }) { "That account no longer exists" }
        val value = statementDay?.let {
            require(it in 1..31) { "Statement day must be between 1 and 31" }
            require(dueOffsetDays in 1..CreditCardCycle.MAX_DUE_OFFSET_DAYS) { "Payment due period must be between 1 and 60 days" }
            JSONObject().put("statementDay", it).put("dueOffsetDays", dueOffsetDays).apply {
                if (limitCents != null && limitCents > 0) put("limit", limitCents)
            }.toString()
        }
        actualEntities!!.setPreference(ActualBudgetDatabase.CREDIT_CARD_PREFERENCE_PREFIX + accountId, value)
        return true
    }

    fun transactions(): List<Transaction> {
        actualDatabase?.let { db ->
            val accountNames = db.fetchAccounts().associate { it.id to it.name }
            // The database API defaults to a 500-row page. This repository currently backs
            // an in-memory Compose list, so explicitly load the complete history; otherwise
            // older synced transactions exist locally but silently disappear from Accounts.
            return db.fetchTransactions(limit = Int.MAX_VALUE).map {
                val isTransfer = it.transferId != null
                Transaction(
                    id = it.id,
                    date = it.date.toString(),
                    payee = it.payeeName ?: if (it.isParent) "Split" else "",
                    category = when {
                        isTransfer -> ""
                        it.isParent -> "Split"
                        else -> it.categoryName ?: "Uncategorized"
                    },
                    account = accountNames[it.accountId] ?: "Unknown",
                    amount = centsToDisplayUnits(it.amountCents),
                    cleared = it.cleared,
                    amountCents = it.amountCents,
                    type = when {
                        isTransfer -> Type.TRANSFER
                        it.amountCents >= 0 -> Type.INCOME
                        else -> Type.EXPENSE
                    },
                    transferAccount = it.transferAccountId?.let(accountNames::get),
                    notes = it.notes.orEmpty(),
                    splits = it.splitPortions.map { part ->
                        SplitLine(
                            category = part.categoryName.orEmpty(),
                            amountCents = kotlin.math.abs(part.amountCents),
                            notes = part.notes.orEmpty(),
                            payee = part.payeeName.takeUnless { name -> name == it.payeeName }.orEmpty(),
                            isOpposite = (part.amountCents < 0) != (it.amountCents < 0),
                            childId = part.id,
                        )
                    },
                )
            }
        }
        return emptyList()
    }

    fun reports(): ReportSnapshot {
        val db = actualDatabase ?: return ReportSnapshot(emptyList(), emptyList(), 0)
        val accounts = db.fetchAccounts()
        val onBudgetIds = accounts.filter { !it.offBudget && !it.closed }.mapTo(mutableSetOf()) { it.id }
        val rows = db.fetchTransactionsForReports().filter { it.accountId in onBudgetIds }
        val currentMonth = currentMonth()
        val monthRows = rows.filter { it.transferId == null }.groupBy { dateMonth(it.date) }
        val end = java.time.YearMonth.parse(currentMonth)
        val months = (5 downTo 0).map { offset ->
            val month = end.minusMonths(offset.toLong()).toString()
            val transactions = monthRows[month].orEmpty()
            ReportMonth(
                month,
                transactions.filter { it.amountCents >= 0 }.sumOf { it.amountCents },
                transactions.filter { it.amountCents < 0 }.sumOf { -it.amountCents },
            )
        }
        val categories = rows.asSequence()
            .filter { it.transferId == null && it.amountCents < 0 && dateMonth(it.date) == currentMonth }
            .groupBy { it.categoryName ?: "Uncategorized" }
            .map { (name, transactions) -> ReportCategory(name, transactions.sumOf { -it.amountCents }) }
            .sortedByDescending { it.spentCents }
        return ReportSnapshot(months, categories, accounts.filterNot { it.closed }.sumOf { it.balanceCents })
    }

    fun saveTransaction(transaction: Transaction) {
        actualDatabase?.let { db ->
            val account = db.fetchAccounts().firstOrNull { it.name == transaction.account && !it.closed }
                ?: error("Select an account")
            val categories = db.fetchCategoryGroups().flatMap { it.categories }
            val category = categories.firstOrNull { it.name == transaction.category && !it.hidden }
            if (transaction.splits.isEmpty() && transaction.category.isNotBlank() &&
                transaction.category != "Uncategorized" && category == null) {
                error("Select a category from the list")
            }
            val transferAccount = transaction.transferAccount?.let { name ->
                db.fetchAccounts().firstOrNull { it.name == name && !it.closed }
                    ?: error("Select a destination account")
            }
            val original = transaction.id.takeIf(String::isNotBlank)?.let(db::fetchTransaction)
            actualForms!!.save(
                ActualTransactionForm(
                    accountId = account.id,
                    type = when (transaction.type) {
                        Type.EXPENSE -> ActualTransactionType.EXPENSE
                        Type.INCOME -> ActualTransactionType.INCOME
                        Type.TRANSFER -> ActualTransactionType.TRANSFER
                    },
                    amount = com.azimulkabir.actua.ui.components.centsToInput(kotlin.math.abs(transaction.amountCents)),
                    payeeName = transaction.payee,
                    transferToAccountId = transferAccount?.id,
                    categoryId = category?.id,
                    notes = transaction.notes,
                    date = parseDate(transaction.date),
                    cleared = transaction.cleared,
                    splits = transaction.splits.map { line ->
                        val lineCategory = categories
                            .firstOrNull { it.name == line.category }
                            ?: error("Select a category for every split")
                        ActualSplitLineForm(
                            childId = line.childId,
                            categoryId = lineCategory.id,
                            amount = com.azimulkabir.actua.ui.components.centsToInput(line.amountCents),
                            isOpposite = line.isOpposite,
                            notes = line.notes,
                            payeeName = line.payee,
                        )
                    },
                    collapseSplit = original?.isParent == true && transaction.splits.isEmpty(),
                ),
                original = original,
            )
            return
        }
        error("Connect to Actual and download a budget before adding transactions")
    }

    fun ruleCategoryFor(transaction: Transaction): String? {
        val db = actualDatabase ?: return null
        if (transaction.type == Type.TRANSFER) return null
        val account = db.fetchAccounts().firstOrNull { it.name == transaction.account && !it.closed } ?: return null
        val payee = db.fetchPayees().firstOrNull {
            it.transferAccountId == null && it.name.equals(transaction.payee.trim(), ignoreCase = true)
        }
        val categories = db.fetchCategoryGroups().flatMap { it.categories }
        val currentCategory = categories.firstOrNull { it.name == transaction.category }?.id
        val signedAmount = when (transaction.type) {
            Type.EXPENSE -> -kotlin.math.abs(transaction.amountCents)
            Type.INCOME -> kotlin.math.abs(transaction.amountCents)
            Type.TRANSFER -> transaction.amountCents
        }
        val preview = com.azimulkabir.actua.data.rules.RulesEngine.apply(
            com.azimulkabir.actua.data.budget.model.ActualTransaction(
                id = "rule-preview",
                accountId = account.id,
                date = parseDate(transaction.date),
                amountCents = signedAmount,
                payeeId = payee?.id,
                payeeName = payee?.name ?: transaction.payee.trim().takeIf(String::isNotEmpty),
                categoryId = currentCategory,
                categoryName = transaction.category.takeIf(String::isNotEmpty),
                notes = transaction.notes.takeIf(String::isNotEmpty),
                cleared = transaction.cleared,
                reconciled = false,
                transferId = null,
                isParent = false,
                parentId = null,
                tombstone = false,
                sortOrder = null,
                importedPayee = transaction.payee.trim().takeIf(String::isNotEmpty),
                scheduleId = null,
                transferAccountId = null,
            ),
            db.fetchRules(),
            db.ruleContext(),
        ).transaction
        return preview.categoryId?.let { id -> categories.firstOrNull { it.id == id }?.name }
    }

    fun setAccountClosed(name: String, closed: Boolean): Boolean {
        val db = actualDatabase ?: return false
        val account = db.fetchAccounts().firstOrNull { it.name == name } ?: return false
        actualEntities!!.setAccountClosed(account.id, closed)
        return true
    }

    fun renameAccount(oldName: String, newName: String): Boolean {
        val account = actualDatabase?.fetchAccounts()?.firstOrNull { it.name == oldName } ?: return false
        actualEntities!!.renameAccount(account.id, newName); return true
    }

    fun renameCategory(groupName: String, oldName: String, newName: String): Boolean {
        val category = actualDatabase?.fetchCategoryGroups()?.firstOrNull { it.name == groupName }
            ?.categories?.firstOrNull { it.name == oldName } ?: return false
        actualEntities!!.renameCategory(category.id, newName); return true
    }

    fun renameCategoryGroup(oldName: String, newName: String): Boolean {
        val group = actualDatabase?.fetchCategoryGroups()?.firstOrNull { it.name == oldName } ?: return false
        actualEntities!!.renameCategoryGroup(group.id, newName); return true
    }

    fun createAccount(name: String, offBudget: Boolean, startingBalance: String): Boolean {
        val cents = ActualTransactionFormService.cents(startingBalance.ifBlank { "0" }) ?: return false
        actualEntities?.createAccount(name, offBudget, cents) ?: return false
        return true
    }

    fun createCategory(groupName: String, name: String): Boolean {
        val group = actualDatabase?.fetchCategoryGroups()?.firstOrNull { it.name == groupName } ?: return false
        actualEntities!!.createCategory(name, group.id); return true
    }

    fun createCategoryGroup(name: String): Boolean {
        actualEntities?.createCategoryGroup(name) ?: return false
        return true
    }

    fun setCategoryHidden(groupName: String, categoryName: String, hidden: Boolean): Boolean {
        val db = actualDatabase ?: return false
        val group = db.fetchCategoryGroups().firstOrNull { it.name == groupName } ?: return false
        val category = group.categories.firstOrNull { it.name == categoryName } ?: return false
        actualEntities!!.setCategoryHidden(category.id, hidden)
        return true
    }

    fun deleteCategory(groupName: String, categoryName: String): Boolean {
        val db = actualDatabase ?: return false
        val group = db.fetchCategoryGroups().firstOrNull { it.name == groupName } ?: return false
        val category = group.categories.firstOrNull { it.name == categoryName } ?: return false
        actualEntities!!.deleteCategory(category.id)
        return true
    }

    fun setCategoryGroupHidden(groupName: String, hidden: Boolean): Boolean {
        val group = actualDatabase?.fetchCategoryGroups()?.firstOrNull { it.name == groupName } ?: return false
        actualEntities!!.setCategoryGroupHidden(group.id, hidden)
        return true
    }

    fun setBudgetAmount(groupName: String, categoryName: String, amountCents: Long, month: String = currentMonth()): Boolean {
        val db = actualDatabase ?: return false
        val category = db.fetchCategoryGroups().firstOrNull { it.name == groupName }
            ?.categories?.firstOrNull { it.name == categoryName } ?: return false
        actualBudgets!!.setAmount(month, category.id, amountCents)
        return true
    }

    fun setCategoryNote(categoryId: String, note: String): Boolean {
        actualEntities?.setNote(categoryId, normalizeNote(note)) ?: return false
        return true
    }

    fun setAccountNote(accountId: String, note: String): Boolean {
        actualEntities?.setNote("account-$accountId", normalizeNote(note)) ?: return false
        return true
    }

    fun setCategoryCarryover(categoryId: String, enabled: Boolean, month: String = currentMonth()): Boolean {
        val start = java.time.YearMonth.parse(month)
        val end = java.time.YearMonth.now().plusMonths(12)
        val months = generateSequence(start) { current -> current.plusMonths(1).takeIf { it <= end } }.toList()
            .ifEmpty { listOf(start) }.map(java.time.YearMonth::toString)
        actualBudgets?.setCarryover(months, categoryId, enabled) ?: return false
        return true
    }

    fun transferBudget(fromGroup: String?, fromCategory: String?, toGroup: String?, toCategory: String?,
        amountCents: Long, month: String = currentMonth()): Boolean {
        val db = actualDatabase ?: return false
        val groups = db.fetchCategoryGroups()
        val from = if (fromGroup == null || fromCategory == null) null else
            groups.firstOrNull { it.name == fromGroup }?.categories?.firstOrNull { it.name == fromCategory } ?: return false
        val to = if (toGroup == null || toCategory == null) null else
            groups.firstOrNull { it.name == toGroup }?.categories?.firstOrNull { it.name == toCategory } ?: return false
        actualBudgets!!.transfer(month, from?.id, to?.id, amountCents)
        return true
    }

    fun setTransactionCleared(id: String, cleared: Boolean): Boolean {
        val transaction = actualDatabase?.fetchTransaction(id) ?: return false
        actualWriter!!.updateTransaction(transaction.copy(cleared = cleared), setOf("cleared"))
        return true
    }

    fun deleteTransaction(id: String): Boolean {
        val transaction = actualDatabase?.fetchTransaction(id) ?: return false
        actualWriter!!.deleteTransaction(transaction)
        return true
    }

    private fun parseDate(value: String): Int {
        value.filter(Char::isDigit).toIntOrNull()?.takeIf { it in 19000101..29991231 }?.let { return it }
        error("Enter a valid date as YYYY-MM-DD")
    }

    private fun centsToDisplayUnits(cents: Long): Int =
        (cents / 100L).coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

    private fun normalizeNote(note: String): String = if (note.isBlank()) "" else note

    private fun currentMonth(): String = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US)
        .format(java.util.Date())

    private fun dateMonth(date: Int): String = "%04d-%02d".format(date / 10_000, date / 100 % 100)
}
