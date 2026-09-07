package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class ActualBudgetReadModelTest {
    @Test
    fun readsActualRelationshipsAndSplitAwareBalances() = withDatabase { database ->
        val accounts = database.fetchAccounts()
        assertEquals(listOf("checking", "savings"), accounts.map { it.id })
        assertEquals(ActualAccountType.CHECKING, accounts.first().type)
        // -1000 ordinary, -1000 transfer, and (-600 + -400) split children.
        // The split parent is excluded.
        // The orphan and half-synced rows are excluded too.
        assertEquals(-3_000, accounts.first().balanceCents)
        assertEquals(0, accounts.first().clearedCents)
        assertEquals(-3_000, accounts.first().unclearedCents)
        assertEquals(0, accounts.first().reconciledCents)
        assertEquals(-700, accounts.last().balanceCents)

        val groups = database.fetchCategoryGroups()
        assertEquals("Essentials", groups.single().name)
        assertEquals(listOf("Groceries", "Rent", "Budget Test"), groups.single().categories.map { it.name })
        assertEquals(3, database.fetchPayees().size)
        assertTrue(database.fetchPayees().any { it.name == "Store" })

        val transactions = database.fetchTransactions(accountId = "checking")
        val ordinary = transactions.first { it.id == "ordinary" }
        val transfer = transactions.first { it.id == "transfer-out" }
        val split = transactions.first { it.id == "split-parent" }
        assertEquals("Store", ordinary.payeeName)
        assertEquals("Savings", transfer.payeeName)
        assertEquals("savings", transfer.transferAccountId)
        assertEquals(listOf("Groceries", "Rent"), split.splitPortions.map { it.categoryName })
        assertEquals(listOf("split-a", "split-b"), split.splitPortions.map { it.id })
        assertNull(split.categoryName)
    }

    @Test
    fun migrationAddsScheduleAndReplaysLatestStoredValue() = withDatabase { database ->
        val transaction = database.fetchTransactions().first { it.id == "ordinary" }
        assertEquals("schedule-new", transaction.scheduleId)
    }

    @Test
    fun writesPayeesTransactionsTransfersSplitsUpdatesAndDeletesWithMessages() = withDatabase { database ->
        var nextId = 0
        val writer = ActualTransactionWriter(database, idFactory = { "new-${++nextId}" })
        val payee = writer.resolveOrCreatePayee("New Shop")
        assertEquals(payee.id, writer.resolveOrCreatePayee("new shop").id)

        val ordinary = transaction("new-tx", "checking", -250, 20260904, payee.id, "grocery")
        writer.createTransaction(ordinary)
        writer.updateTransaction(ordinary.copy(amountCents = -300, cleared = true), setOf("amount", "cleared"))

        val source = transaction("new-source", "checking", -500, 20260904, "transfer-savings", null, transfer = "new-target")
        val target = transaction("new-target", "savings", 500, 20260904, null, null, transfer = "new-source")
        writer.createTransfer(source, target)

        val parent = transaction("new-parent", "checking", -1_000, 20260904, payee.id, null, parentFlag = true)
        val childA = transaction("new-child-a", "checking", -700, 20260904, payee.id, "grocery", parent = parent.id)
        val childB = transaction("new-child-b", "checking", -300, 20260904, payee.id, "rent", parent = parent.id)
        writer.createSplit(parent, listOf(childA, childB))
        writer.deleteTransaction(parent)

        val written = database.fetchTransactions(accountId = "checking")
        assertEquals(-300, written.first { it.id == "new-tx" }.amountCents)
        assertTrue(written.first { it.id == "new-tx" }.cleared)
        assertEquals("Savings", written.first { it.id == "new-source" }.payeeName)
        assertTrue(written.none { it.id == "new-parent" })
        assertTrue(database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString()).any {
            it.dataset == "payee_mapping" && it.row == payee.id
        })
        assertTrue(database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString()).any {
            it.row == "new-parent" && it.column == "tombstone" && it.value == "N:1"
        })
    }

    @Test
    fun formServiceRoutesExpenseTransferAndSplitEditsLikeIos() = withDatabase { database ->
        var next = 0
        val ids = { "form-${++next}" }
        val writer = ActualTransactionWriter(database, idFactory = ids)
        val service = ActualTransactionFormService(database, writer, idFactory = ids, nowMillis = { 2_000_000.0.toLong() })

        val expenseId = service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "12.345", payeeName = "Corner Shop", categoryId = "grocery",
            date = 20260904, cleared = true,
        ))
        assertEquals(-1_235L, database.fetchTransaction(expenseId!!)?.amountCents)
        assertEquals("Corner Shop", database.fetchTransaction(expenseId)?.payeeName)

        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "5", transferToAccountId = "savings", date = 20260904,
        ))
        val transfer = database.fetchTransactions("checking").first { it.id.startsWith("form-") && it.transferId != null }
        assertEquals(-500L, transfer.amountCents)
        assertEquals(500L, database.fetchTransaction(transfer.transferId!!)?.amountCents)

        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Store", date = 20260904,
            splits = listOf(
                ActualSplitLineForm(categoryId = "grocery", amount = "6"),
                ActualSplitLineForm(categoryId = "rent", amount = "4"),
            ),
        ))
        val parent = database.fetchTransactions("checking").first { it.id.startsWith("form-") && it.isParent }
        val children = database.fetchChildTransactions(parent.id)
        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Store", date = 20260905, cleared = true,
            splits = listOf(
                ActualSplitLineForm(childId = children[0].id, categoryId = "rent", amount = "7"),
                ActualSplitLineForm(categoryId = "grocery", amount = "3"),
            ),
        ), original = parent)
        val editedChildren = database.fetchChildTransactions(parent.id)
        assertEquals(listOf(-700L, -300L), editedChildren.map { it.amountCents })
        assertTrue(editedChildren.all { it.date == 20260905 && it.cleared })
        assertTrue(editedChildren.none { it.id == children[1].id })

        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Store", categoryId = "grocery",
            date = 20260906, collapseSplit = true,
        ), original = database.fetchTransaction(parent.id))
        val collapsed = database.fetchTransaction(parent.id)!!
        assertTrue(!collapsed.isParent)
        assertEquals(-1_000L, collapsed.amountCents)
        assertEquals("grocery", collapsed.categoryId)
        assertTrue(database.fetchChildTransactions(parent.id).isEmpty())
    }

    @Test
    fun entityMenuMutationsUpdateRowsAndCrdtLog() = withDatabase { database ->
        val writer = ActualEntityWriter(database, nodeId = "bbbbbbbbbbbbbbbb")
        writer.renameAccount("checking", "Daily")
        writer.setAccountClosed("checking", true)
        writer.renameCategory("grocery", "Food")
        writer.setCategoryHidden("grocery", true)
        writer.renameCategoryGroup("essential", "Needs")
        writer.setCategoryGroupHidden("essential", true)
        writer.renamePayee("store", "Market")

        assertEquals("Daily", database.fetchAccounts().first { it.id == "checking" }.name)
        assertTrue(database.fetchAccounts().first { it.id == "checking" }.closed)
        val group = database.fetchCategoryGroups().single()
        assertEquals("Needs", group.name)
        assertTrue(group.hidden)
        assertEquals("Food", group.categories.first { it.id == "grocery" }.name)
        assertTrue(group.categories.first { it.id == "grocery" }.hidden)
        assertEquals("Market", database.fetchPayees().first { it.id == "store" }.name)
        val messages = database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString())
        assertTrue(messages.any { it.dataset == "accounts" && it.row == "checking" && it.column == "closed" })
        assertTrue(messages.any { it.dataset == "categories" && it.row == "grocery" && it.column == "hidden" })
    }

    @Test
    fun deletingCategoryUsesTombstoneMutation() = withDatabase { database ->
        val writer = ActualEntityWriter(database, nodeId = "dddddddddddddddd")

        writer.deleteCategory("grocery")

        assertTrue(database.fetchCategoryGroups().flatMap { it.categories }.none { it.id == "grocery" })
        assertTrue(database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString()).any {
            it.dataset == "categories" && it.row == "grocery" && it.column == "tombstone"
        })
    }

    @Test
    fun creditCardConfigUsesIosPreferenceContractAndSyncLog() = withDatabase { database ->
        val writer = ActualEntityWriter(database, nodeId = "cccccccccccccccc")
        writer.setPreference(
            ActualBudgetDatabase.CREDIT_CARD_PREFERENCE_PREFIX + "checking",
            "{\"statementDay\":18,\"dueOffsetDays\":25,\"limit\":500000}",
        )
        val config = database.fetchCreditCardConfigs().getValue("checking")
        assertEquals(18, config.statementDay)
        assertEquals(25, config.dueOffsetDays)
        assertEquals(500_000L, config.limitCents)
        assertTrue(database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString()).any {
            it.dataset == "preferences" && it.row == "actuali:credit_card:checking" && it.column == "value"
        })
        writer.setPreference(ActualBudgetDatabase.CREDIT_CARD_PREFERENCE_PREFIX + "checking", null)
        assertTrue(database.fetchCreditCardConfigs().isEmpty())
    }

    @Test
    fun createsActualAccountGraphAndCategoryMappingsThroughCrdt() = withDatabase { database ->
        var number = 0
        val writer = ActualEntityWriter(database, nodeId = "eeeeeeeeeeeeeeee",
            idFactory = { "entity-${++number}" }, nowMillis = { 1_789_000_000_000L })
        val groupId = writer.createCategoryGroup("New Group")
        val categoryId = writer.createCategory("First Category", groupId)
        val accountId = writer.createAccount("Wallet", offBudget = false, startingBalanceCents = 12_345)

        assertEquals("New Group", database.fetchCategoryGroups().first { it.id == groupId }.name)
        assertEquals("First Category", database.fetchCategoryGroups().flatMap { it.categories }
            .first { it.id == categoryId }.name)
        assertEquals(12_345L, database.fetchAccounts().first { it.id == accountId }.balanceCents)
        val opening = database.fetchTransactions(accountId).single()
        assertTrue(opening.startingBalance)
        assertEquals("Starting Balance", opening.payeeName)
        val messages = database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString())
        assertTrue(messages.any { it.dataset == "category_mapping" && it.row == categoryId })
        assertTrue(messages.any { it.dataset == "payees" && it.column == "transfer_acct" && it.value == "S:$accountId" })
        assertTrue(messages.any { it.dataset == "transactions" && it.column == "starting_balance_flag" && it.value == "N:1" })
    }

    @Test
    fun envelopeBudgetWalkCarriesBalancesAndReusesBudgetCellIds() = withDatabase { database ->
        val august = database.fetchBudgetMonth("2026-08").categories.single { it.categoryId == "budgetcat" }
        assertEquals(-200L, august.availableCents)
        val september = database.fetchBudgetMonth("2026-09").categories.single { it.categoryId == "budgetcat" }
        assertEquals(-200L, september.carryoverCents)
        assertEquals(1_300L, september.availableCents)

        ActualBudgetWriter(database, "cccccccccccccccc").setAmount("2026-09", "budgetcat", 2_500)
        val updated = database.fetchBudgetMonth("2026-09").categories.single { it.categoryId == "budgetcat" }
        assertEquals(2_500L, updated.budgetedCents)
        assertEquals(1_800L, updated.availableCents)
        assertTrue(database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString()).any {
            it.dataset == "zero_budgets" && it.row == "custom-september-row" && it.column == "amount" && it.value == "N:2500"
        })
    }

    @Test
    fun budgetTransferWritesBothCellsAtomicallyLikeIos() = withDatabase { database ->
        var scheduledPushes = 0
        ActualBudgetWriter(database, "abababababababab", onWrite = { scheduledPushes++ })
            .transfer("2026-09", "budgetcat", "grocery", 300)
        val rows = database.fetchBudgetMonth("2026-09").categories.associateBy { it.categoryId }
        assertEquals(1_700L, rows.getValue("budgetcat").budgetedCents)
        assertEquals(300L, rows.getValue("grocery").budgetedCents)
        val messages = database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString())
        assertTrue(messages.any { it.dataset == "zero_budgets" && it.row == "202609-grocery" && it.column == "category" })
        assertTrue(messages.any { it.dataset == "zero_budgets" && it.row == "custom-september-row" && it.value == "N:1700" })
        assertEquals(1, scheduledPushes)
    }

    @Test
    fun incomingTransactionRunsStoredRulesAndCreatesNamedPayee() = withDatabase { database ->
        val writer = ActualTransactionWriter(database, nodeId = "dddddddddddddddd", idFactory = { "rule-payee" })
        val incoming = transaction("ruled", "checking", -450, 20260904, null, null)
            .copy(importedPayee = "THE COFFEE PLACE")
        val result = writer.createTransaction(incoming)
        assertEquals("grocery", result?.categoryId)
        assertEquals("Coffee Shop", database.fetchTransaction("ruled")?.payeeName)
        assertTrue(database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString()).any {
            it.dataset == "payees" && it.row == "rule-payee" && it.column == "name"
        })
    }

    @Test
    fun readsEffectivePostableScheduleAndDeduplicatesLinkedTransactions() = withDatabase { database ->
        val schedule = database.fetchSchedules().single()
        assertEquals("rent-schedule", schedule.id)
        assertEquals(20260905, schedule.nextDate.yyyymmdd)
        assertEquals("checking", schedule.accountId)
        assertEquals("store", schedule.payeeId)
        assertEquals("rent", schedule.categoryId)
        assertEquals(-1_500L, schedule.amount?.postAmount)
        assertTrue(schedule.dateCondition is com.azimulkabir.actua.data.schedules.ScheduleDateCondition.Recurring)
        assertTrue(!database.hasScheduleTransaction(schedule.id, 20260905))
        ActualTransactionWriter(database, "eeeeeeeeeeeeeeee").createTransaction(
            transaction("scheduled-payment", "checking", -1_500, 20260905, "store", "rent")
                .copy(scheduleId = schedule.id), applyRules = false)
        assertTrue(database.hasScheduleTransaction(schedule.id, 20260905))
    }

    @Test
    fun schedulePosterPostsDueOccurrenceAdvancesAndGatesTheDay() = withDatabase { database ->
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val poster = com.azimulkabir.actua.data.schedules.SchedulePoster(
            context, database,
            ActualTransactionWriter(database, "ffffffffffffffff", idFactory = { "posted-schedule" }),
            com.azimulkabir.actua.data.schedules.ActualScheduleWriter(database, "abababababababab"),
            idFactory = { "posted-schedule" },
        )
        val budgetId = "poster-${UUID.randomUUID()}"
        assertEquals(1, poster.runIfNeeded(budgetId, com.azimulkabir.actua.data.schedules.DayDate(2026,9,5)))
        val posted = requireNotNull(database.fetchTransaction("posted-schedule"))
        assertEquals("rent-schedule", posted.scheduleId)
        assertEquals(-1_500L, posted.amountCents)
        assertEquals("rent", posted.categoryId)
        assertEquals(20261005, database.fetchSchedules().single().nextDate.yyyymmdd)
        assertEquals(0, poster.runIfNeeded(budgetId, com.azimulkabir.actua.data.schedules.DayDate(2026,9,5)))
    }

    @Test
    fun scheduleListIncludesLifecycleAndComputesPaidState() = withDatabase { database ->
        val summary = database.fetchScheduleSummaries().single()
        assertEquals("Rent", summary.name)
        assertEquals("rent-rule", summary.ruleId)
        assertEquals(20260905, summary.nextDate?.yyyymmdd)
        assertEquals(com.azimulkabir.actua.data.schedules.ScheduleAmountOp.EXACT, summary.amountOp)
        assertTrue(summary.isRecurring)
        assertTrue(summary.isCustom)
        assertTrue(database.scheduleNameExists("Rent"))
        assertTrue(!database.scheduleNameExists("Rent", excludingId = summary.id))
        ActualTransactionWriter(database, "cdcdcdcdcdcdcdcd").createTransaction(
            transaction("early-payment", "checking", -1_500, 20260903, "store", "rent")
                .copy(scheduleId = summary.id), applyRules = false)
        // Automatic schedules do not use the two-day manual lookback.
        assertTrue(database.fetchPaidScheduleIds(listOf(summary)).isEmpty())
        ActualTransactionWriter(database, "dededededededede").createTransaction(
            transaction("due-payment", "checking", -1_500, 20260905, "store", "rent")
                .copy(scheduleId = summary.id), applyRules = false)
        assertEquals(setOf(summary.id), database.fetchPaidScheduleIds(listOf(summary)))
    }

    @Test
    fun scheduleWritePlanPersistsThroughCrdtAndRemainsReadable() = withDatabase { database ->
        val plan = com.azimulkabir.actua.data.schedules.ScheduleWriteBuilder.create(
            com.azimulkabir.actua.data.schedules.ScheduleFormFields(
                "Utilities", "store", "checking",
                com.azimulkabir.actua.data.schedules.ScheduledAmount.Fixed(-2_000),
                date = com.azimulkabir.actua.data.schedules.ScheduleDateCondition.Fixed(
                    com.azimulkabir.actua.data.schedules.DayDate(2026,10,1)),
                postsTransaction = false,
            ), "new-schedule", "new-rule", "new-next", 500,
            com.azimulkabir.actua.data.schedules.DayDate(2026,9,5),
        )
        com.azimulkabir.actua.data.schedules.ActualScheduleWriter(database, "efefefefefefefef").apply(plan)
        val inserted = database.fetchScheduleSummaries().single { it.id == "new-schedule" }
        assertEquals("Utilities", inserted.name)
        assertEquals(20261001, inserted.nextDate?.yyyymmdd)
        assertEquals(-2_000L, inserted.postAmount)
        assertTrue(!inserted.postsTransaction)
        assertTrue(database.getMessagesSince(com.azimulkabir.actua.data.sync.HlcTimestamp.ZERO.toString()).any {
            it.dataset == "schedules" && it.row == "new-schedule" && it.column == "rule"
        })
    }

    private fun withDatabase(block: (ActualBudgetDatabase) -> Unit) {
        val file = createDatabaseFile()
        try {
            ActualBudgetDatabase.open(file).use(block)
        } finally {
            file.delete()
        }
    }

    private fun createDatabaseFile(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "read-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, type TEXT, offbudget INTEGER, closed INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE category_groups (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER, hidden INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, cat_group TEXT, is_income INTEGER, hidden INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT)")
            db.execSQL("CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, transfer_acct TEXT, tombstone INTEGER)")
            db.execSQL("CREATE TABLE payee_mapping (id TEXT PRIMARY KEY, targetId TEXT)")
            db.execSQL("CREATE TABLE transactions (id TEXT PRIMARY KEY, isParent INTEGER, isChild INTEGER, acct TEXT, category TEXT, amount INTEGER, description TEXT, notes TEXT, date INTEGER, imported_description TEXT, transferred_id TEXT, cleared INTEGER, reconciled INTEGER, sort_order REAL, tombstone INTEGER, parent_id TEXT)")
            db.execSQL("CREATE TABLE zero_budgets (id TEXT PRIMARY KEY, month INTEGER, category TEXT, amount INTEGER, carryover INTEGER)")
            db.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            db.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, `column` TEXT NOT NULL, value BLOB NOT NULL)")
            db.execSQL("CREATE TABLE preferences (id TEXT PRIMARY KEY, value TEXT)")
            db.execSQL("CREATE TABLE rules (id TEXT PRIMARY KEY, stage TEXT, conditions_op TEXT, conditions TEXT, actions TEXT, tombstone INTEGER)")
            db.execSQL("CREATE TABLE schedules (id TEXT PRIMARY KEY, rule TEXT, name TEXT, posts_transaction INTEGER, completed INTEGER, custom_upcoming_length TEXT, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE schedules_next_date (id TEXT PRIMARY KEY, schedule_id TEXT, local_next_date INTEGER, local_next_date_ts INTEGER, base_next_date INTEGER, base_next_date_ts INTEGER)")

            db.execSQL("INSERT INTO accounts VALUES ('checking','Checking','checking',0,0,0,1), ('savings','Savings','savings',0,0,0,2)")
            db.execSQL("INSERT INTO category_groups VALUES ('essential','Essentials',0,0,0,1)")
            db.execSQL("INSERT INTO categories VALUES ('grocery','Groceries','essential',0,0,0,1), ('rent','Rent','essential',0,0,0,2), ('budgetcat','Budget Test','essential',0,0,0,3)")
            db.execSQL("INSERT INTO category_mapping VALUES ('grocery','grocery'), ('rent','rent'), ('budgetcat','budgetcat')")
            db.execSQL("INSERT INTO payees VALUES ('store','Store',NULL,0), ('transfer-savings',NULL,'savings',0), ('transfer-checking',NULL,'checking',0)")
            db.execSQL("INSERT INTO payee_mapping VALUES ('store','store'), ('transfer-savings','transfer-savings'), ('transfer-checking','transfer-checking')")

            insertTransaction(db, "ordinary", 0, 0, "checking", "grocery", -1000, "store", 20260901, 1.0)
            insertTransaction(db, "transfer-out", 0, 0, "checking", null, -1000, "transfer-savings", 20260902, 2.0, "transfer-in")
            insertTransaction(db, "transfer-in", 0, 0, "savings", null, 1000, null, 20260902, 2.0, "transfer-out")
            insertTransaction(db, "split-parent", 1, 0, "checking", null, -1000, null, 20260903, 3.0)
            insertTransaction(db, "split-a", 0, 1, "checking", "grocery", -600, "store", 20260903, 5.0, parent = "split-parent")
            insertTransaction(db, "split-b", 0, 1, "checking", "rent", -400, "store", 20260903, 4.0, parent = "split-parent")
            insertTransaction(db, "orphan", 0, 1, "checking", "rent", -9999, "store", 20260903, 6.0, parent = "missing")
            insertTransaction(db, "half", 0, 0, "checking", "rent", -9999, "store", null, 7.0)
            insertTransaction(db, "august-spend", 0, 0, "savings", "budgetcat", -1200, "store", 20260810, 1.0)
            insertTransaction(db, "september-spend", 0, 0, "savings", "budgetcat", -500, "store", 20260910, 1.0)
            db.execSQL("INSERT INTO zero_budgets(id,month,category,amount,carryover) VALUES ('custom-august-row',202608,'budgetcat',1000,1), ('custom-september-row',202609,'budgetcat',2000,0)")
            db.execSQL("""INSERT INTO rules VALUES ('coffee-rule',NULL,'and',
                '[{"op":"contains","field":"imported_description","value":"coffee"}]',
                '[{"op":"set","field":"category","value":"grocery"},{"op":"set","field":"payee_name","value":"Coffee Shop"}]',0)""")
            db.execSQL("""INSERT INTO rules VALUES ('rent-rule',NULL,'and',
                '[{"op":"is","field":"acct","value":"checking"},{"op":"is","field":"description","value":"store"},{"op":"is","field":"amount","value":-1500},{"op":"isapprox","field":"date","value":{"frequency":"monthly","start":"2026-09-05"}}]',
                '[{"op":"set","field":"category","value":"rent"},{"op":"link-schedule","value":"rent-schedule"}]',0)""")
            db.execSQL("INSERT INTO schedules VALUES ('rent-schedule','rent-rule','Rent',1,0,NULL,0,1)")
            db.execSQL("INSERT INTO schedules_next_date VALUES ('rent-next','rent-schedule',20260905,100,20260904,100)")

            db.execSQL("INSERT INTO messages_crdt(timestamp,dataset,row,`column`,value) VALUES ('2026-09-04T10:00:00.000Z-0000-aaaaaaaaaaaaaaaa','transactions','ordinary','schedule','S:schedule-old')")
            db.execSQL("INSERT INTO messages_crdt(timestamp,dataset,row,`column`,value) VALUES ('2026-09-04T10:01:00.000Z-0000-aaaaaaaaaaaaaaaa','transactions','ordinary','schedule','S:schedule-new')")
        }
        return file
    }

    private fun insertTransaction(db: SQLiteDatabase, id: String, parentFlag: Int, childFlag: Int, account: String, category: String?, amount: Int, payee: String?, date: Int?, sort: Double, transfer: String? = null, parent: String? = null) {
        db.execSQL(
            "INSERT INTO transactions VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            arrayOf<Any?>(id, parentFlag, childFlag, account, category, amount, payee, null, date, null, transfer, 0, 0, sort, 0, parent),
        )
    }

    private fun transaction(
        id: String,
        account: String,
        amount: Long,
        date: Int,
        payee: String?,
        category: String?,
        transfer: String? = null,
        parentFlag: Boolean = false,
        parent: String? = null,
    ) = ActualTransaction(
        id = id, accountId = account, date = date, amountCents = amount,
        payeeId = payee, payeeName = null, categoryId = category, categoryName = null,
        notes = null, cleared = false, reconciled = false, transferId = transfer,
        isParent = parentFlag, parentId = parent, tombstone = false,
        sortOrder = date.toDouble(), importedPayee = null, scheduleId = null,
        transferAccountId = null,
    )
}
