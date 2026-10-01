package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.HlcTimestamp
import com.azimulkabir.actua.model.TransactionStatusFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Transfer-pair and split invariants that match Actual v26.9.0 (see docs/TRANSACTIONS_PARITY.md).
 * Upstream: loot-core `server/transactions/transfer.ts` and `shared/transactions.ts` at 59fe126f.
 */
class TransactionParityTest {
    @Test
    fun newTransferWritesBothLegsWithEachOthersTransferPayee() = withDatabase { database ->
        val service = formService(database, "pair")

        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "25", transferToAccountId = "savings", notes = "rent pot", date = 20260910,
        ))

        val source = database.fetchTransactions("checking").single { it.transferId != null }
        val target = requireNotNull(database.fetchTransaction(requireNotNull(source.transferId)))
        // addTransfer: amount negated, payee is the *from* account's transfer payee, notes copied.
        assertEquals(source.id, target.transferId)
        assertEquals(-2_500L, source.amountCents)
        assertEquals(2_500L, target.amountCents)
        assertEquals("transfer-savings", source.payeeId)
        assertEquals("transfer-checking", target.payeeId)
        assertEquals("rent pot", target.notes)
        assertEquals(source.date, target.date)
        // clearCategory: two on-budget accounts never keep a category.
        assertNull(source.categoryId)
        assertNull(target.categoryId)

        val messages = transactionMessages(database)
        for (leg in listOf(source, target)) {
            val columns = messages.filter { it.row == leg.id }.map { it.column }.toSet()
            assertTrue(columns.containsAll(setOf("acct", "amount", "date", "description", "transferred_id")))
        }
        assertEquals("S:${target.id}", messages.single { it.row == source.id && it.column == "transferred_id" }.value)
        assertEquals("S:${source.id}", messages.single { it.row == target.id && it.column == "transferred_id" }.value)
    }

    @Test
    fun convertingToTransferInsertsAnUnclearedPartnerAndClearsOnBudgetCategory() = withDatabase { database ->
        val service = formService(database, "convert")
        val original = requireNotNull(database.fetchTransaction("ordinary"))
        assertTrue(original.cleared)

        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "10", transferToAccountId = "savings", date = 20260901, cleared = true,
        ), original)

        val leg = requireNotNull(database.fetchTransaction("ordinary"))
        val partner = requireNotNull(database.fetchTransaction(requireNotNull(leg.transferId)))
        assertEquals("ordinary", partner.transferId)
        assertEquals("transfer-checking", partner.payeeId)
        // addTransfer inserts the other leg with `cleared: false`.
        assertFalse(partner.cleared)
        assertTrue(leg.cleared)
        assertNull(leg.categoryId)
        assertNull(partner.categoryId)
    }

    @Test
    fun onBudgetToOffBudgetConversionKeepsTheOnBudgetLegCategory() = withDatabase { database ->
        val offBudget = ActualEntityWriter(database, idFactory = { "off-${UUID.randomUUID()}" })
            .createAccount("Brokerage", offBudget = true, startingBalanceCents = 0)
        val service = formService(database, "mixed")
        val original = requireNotNull(database.fetchTransaction("ordinary"))

        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "10", transferToAccountId = offBudget, categoryId = "grocery", date = 20260901,
        ), original)

        val leg = requireNotNull(database.fetchTransaction("ordinary"))
        val partner = requireNotNull(database.fetchTransaction(requireNotNull(leg.transferId)))
        // clearCategory returns false when exactly one side is off-budget.
        assertEquals("grocery", leg.categoryId)
        assertEquals(offBudget, partner.accountId)
        assertNull(partner.categoryId)
    }

    @Test
    fun splitParentEditsFlowToChildrenAndInheritedChildPayeesFollowTheParent() = withDatabase { database ->
        val service = formService(database, "split")
        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Store", date = 20260910,
            splits = listOf(
                ActualSplitLineForm(categoryId = "grocery", amount = "6"),
                ActualSplitLineForm(categoryId = "rent", amount = "4", payeeName = "Landlord"),
            ),
        ))
        val parent = database.fetchTransactions("checking").single { it.isParent }
        val children = database.fetchChildTransactions(parent.id)
        val inherited = children.single { it.categoryId == "grocery" }
        val own = children.single { it.categoryId == "rent" }
        assertEquals(parent.payeeId, inherited.payeeId)

        service.save(ActualTransactionForm(
            accountId = "savings", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Market", date = 20260912, cleared = true,
            splits = listOf(
                // The form leaves an inherited child payee blank, as ActuaRepository.toTransaction does.
                ActualSplitLineForm(childId = inherited.id, categoryId = "grocery", amount = "6"),
                ActualSplitLineForm(childId = own.id, categoryId = "rent", amount = "4", payeeName = "Landlord"),
            ),
        ), parent)

        val editedParent = requireNotNull(database.fetchTransaction(parent.id))
        val edited = database.fetchChildTransactions(parent.id).associateBy { it.id }
        assertNull(editedParent.categoryId)
        assertEquals(editedParent.amountCents, edited.values.sumOf { it.amountCents })
        // makeChild: account, date and cleared always follow the parent.
        assertTrue(edited.values.all { it.accountId == "savings" && it.date == 20260912 && it.cleared })
        // updateTransaction: a child whose payee equalled the old parent payee takes the new one.
        assertEquals(editedParent.payeeId, edited.getValue(inherited.id).payeeId)
        assertEquals(own.payeeId, edited.getValue(own.id).payeeId)
    }

    @Test
    fun aTransferLabelOnASplitLineNeverCreatesAPayee() = withDatabase { database ->
        val service = formService(database, "split-transfer")
        val payeesBefore = database.fetchPayees()
        val transactionsBefore = database.fetchTransactions("checking")
        val form = ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Store", date = 20260910,
            splits = listOf(
                ActualSplitLineForm(categoryId = "grocery", amount = "6", payeeName = "Corner Shop"),
                ActualSplitLineForm(categoryId = "rent", amount = "4", payeeName = "Transfer: Savings"),
            ),
        )

        assertEquals(
            ActualTransactionFormException.SplitLineTransfer,
            runCatching { service.save(form) }.exceptionOrNull(),
        )
        // Checked before anything is written: not even the other line's new payee.
        assertEquals(payeesBefore, database.fetchPayees())
        assertEquals(transactionsBefore, database.fetchTransactions("checking"))
        assertTrue(database.fetchPayees().none { it.name.startsWith("Transfer: ") })

        // A converted transaction is checked the same way.
        val original = requireNotNull(database.fetchTransaction("ordinary"))
        assertEquals(
            ActualTransactionFormException.SplitLineTransfer,
            runCatching { service.save(form, original) }.exceptionOrNull(),
        )
        assertEquals(payeesBefore, database.fetchPayees())
    }

    @Test
    fun aPayeeAlreadyNamedLikeATransferStillSavesOnASplitLine() = withDatabase(
        // A payee the old split editor created from a `Transfer:` label.
        extraSql = listOf(
            "INSERT INTO payees VALUES ('legacy','Transfer: Savings',NULL,0)",
            "INSERT INTO payee_mapping VALUES ('legacy','legacy')",
        ),
    ) { database ->
        formService(database, "legacy").save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Store", date = 20260910,
            splits = listOf(
                ActualSplitLineForm(categoryId = "grocery", amount = "6"),
                ActualSplitLineForm(categoryId = "rent", amount = "4", payeeName = "Transfer: Savings"),
            ),
        ))
        val parent = database.fetchTransactions("checking").single { it.isParent }
        assertEquals(
            listOf("legacy"),
            database.fetchChildTransactions(parent.id).filter { it.categoryId == "rent" }.map { it.payeeId },
        )
        assertEquals(1, database.fetchPayees().count { it.name == "Transfer: Savings" })
    }

    @Test
    fun aPayeelessChildKeepsNoPayeeUntilTheParentIsEdited() = withDatabase { database ->
        val service = formService(database, "payeeless")
        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Store", date = 20260910,
            splits = listOf(
                ActualSplitLineForm(categoryId = "grocery", amount = "6"),
                ActualSplitLineForm(categoryId = "rent", amount = "4"),
            ),
        ))
        val parent = database.fetchTransactions("checking").single { it.isParent }
        val children = database.fetchChildTransactions(parent.id)
        val payeeless = children.single { it.categoryId == "grocery" }
        val inherited = children.single { it.categoryId == "rent" }
        // A child with no payee, e.g. one another client cleared.
        ActualTransactionWriter(database).mutate(updates = listOf(payeeless to payeeless.copy(payeeId = null)))
        // The form leaves both blank, as ActuaRepository.toTransaction does.
        fun lines(groceries: String, rent: String) = listOf(
            ActualSplitLineForm(childId = payeeless.id, categoryId = "grocery", amount = groceries),
            ActualSplitLineForm(childId = inherited.id, categoryId = "rent", amount = rent),
        )
        fun child(id: String) = database.fetchChildTransactions(parent.id).single { it.id == id }
        val before = transactionMessages(database).size

        // Only child lines change: the payee-less child keeps no payee and no description is written.
        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Store", date = 20260910, splits = lines("7", "3"),
        ), requireNotNull(database.fetchTransaction(parent.id)))
        assertNull(child(payeeless.id).payeeId)
        assertTrue(transactionMessages(database).drop(before).none { it.row == payeeless.id && it.column == "description" })

        // The parent payee changes: loot-core updateTransaction + makeChild fill it with the new
        // parent payee, and the child that matched the old parent payee follows too.
        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Market", date = 20260910, splits = lines("7", "3"),
        ), requireNotNull(database.fetchTransaction(parent.id)))
        val editedParent = requireNotNull(database.fetchTransaction(parent.id))
        assertEquals(editedParent.payeeId, child(payeeless.id).payeeId)
        assertEquals(editedParent.payeeId, child(inherited.id).payeeId)
    }

    @Test
    fun movingAStandardTransactionOffBudgetClearsItsCategory() = withDatabase { database ->
        val offBudget = ActualEntityWriter(database, idFactory = { "off-${UUID.randomUUID()}" })
            .createAccount("Brokerage", offBudget = true, startingBalanceCents = 0)
        val service = formService(database, "move")
        val original = requireNotNull(database.fetchTransaction("ordinary"))
        assertEquals("grocery", original.categoryId)

        service.save(ActualTransactionForm(
            accountId = offBudget, type = ActualTransactionType.EXPENSE,
            amount = "10", payeeName = "Store", categoryId = "grocery", date = 20260901,
        ), original)

        val moved = requireNotNull(database.fetchTransaction("ordinary"))
        assertEquals(offBudget, moved.accountId)
        assertNull(moved.categoryId)
        assertEquals("0:", transactionMessages(database).last { it.row == "ordinary" && it.column == "category" }.value)
    }

    private fun formService(database: ActualBudgetDatabase, prefix: String): ActualTransactionFormService {
        var next = 0
        val ids = { "$prefix-${++next}" }
        return ActualTransactionFormService(database, ActualTransactionWriter(database, idFactory = ids), idFactory = ids)
    }

    private fun transactionMessages(database: ActualBudgetDatabase): List<CrdtMessage> =
        database.getMessagesSince(HlcTimestamp.ZERO.toString()).filter { it.dataset == "transactions" }

    @Test
    fun aNewTransfersOtherLegIsUncleared() = withDatabase { database ->
        formService(database, "cleared").save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "25", transferToAccountId = "savings", date = 20260910, cleared = true,
        ))

        val source = database.fetchTransactions("checking").single { it.transferId != null }
        val target = requireNotNull(database.fetchTransaction(requireNotNull(source.transferId)))
        assertTrue(source.cleared)
        // addTransfer inserts the other leg with `cleared: false`.
        assertFalse(target.cleared)
    }

    @Test
    fun editingOneLegKeepsTheOtherLegsClearedReconciledAndDate() = withDatabase { database ->
        val (source, target) = reconciledTransfer(database)
        val before = transactionMessages(database).size

        formService(database, "edit").save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "30", transferToAccountId = "savings", notes = "moved", date = 20260915, cleared = false,
        ), source)

        val edited = requireNotNull(database.fetchTransaction(source.id))
        val other = requireNotNull(database.fetchTransaction(target.id))
        assertEquals(20260915, edited.date)
        assertFalse(edited.cleared)
        // updateTransfer copies account, payee, notes, amount and schedule only.
        assertEquals(3_000L, other.amountCents)
        assertEquals("moved", other.notes)
        assertEquals(20260910, other.date)
        assertTrue(other.cleared)
        assertTrue(other.reconciled)
        val otherColumns = transactionMessages(database).drop(before).filter { it.row == target.id }.map { it.column }
        assertEquals(setOf("amount", "notes"), otherColumns.toSet())
    }

    @Test
    fun aReconciledRowStaysClearedAndMovingItUnreconcilesIt() = withDatabase { database ->
        val writer = ActualTransactionWriter(database)
        val ordinary = requireNotNull(database.fetchTransaction("ordinary"))
        writer.mutate(updates = listOf(ordinary to ordinary.copy(cleared = true, reconciled = true)))
        val reconciled = requireNotNull(database.fetchTransaction("ordinary"))
        val service = formService(database, "reconciled")
        val before = transactionMessages(database).size

        // The editor never writes cleared = 0 on a row that stays reconciled.
        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.EXPENSE,
            amount = "12", payeeName = "Store", categoryId = "grocery", date = 20260901, cleared = false,
        ), reconciled)
        val edited = requireNotNull(database.fetchTransaction("ordinary"))
        assertTrue(edited.cleared)
        assertTrue(edited.reconciled)
        val columns = transactionMessages(database).drop(before).filter { it.row == "ordinary" }.map { it.column }
        assertTrue("amount" in columns)
        assertFalse("cleared" in columns || "reconciled" in columns)

        // Moving it to another account writes reconciled = 0 (desktop TransactionsTable onUpdate).
        service.save(ActualTransactionForm(
            accountId = "savings", type = ActualTransactionType.EXPENSE,
            amount = "12", payeeName = "Store", categoryId = "grocery", date = 20260901, cleared = true,
        ), edited)
        val moved = requireNotNull(database.fetchTransaction("ordinary"))
        assertEquals("savings", moved.accountId)
        assertFalse(moved.reconciled)
        assertTrue(moved.cleared)
    }

    @Test
    fun fetchReconciledIdsFindsTheReconciledOtherLeg() = withDatabase { database ->
        val (source, target) = reconciledTransfer(database)
        assertEquals(setOf(target.id), database.fetchReconciledIds(listOf(source.id, target.id, "missing")))
        assertEquals(emptySet<String>(), database.fetchReconciledIds(emptyList()))
    }

    @Test
    fun syncTransferDatePreferenceMovesTheOtherLegsDate() = withDatabase(syncTransferDate = true) { database ->
        val (source, target) = reconciledTransfer(database)

        formService(database, "sync").save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "25", transferToAccountId = "savings", date = 20260915, cleared = true,
        ), source)

        val other = requireNotNull(database.fetchTransaction(target.id))
        assertEquals(20260915, other.date)
        assertTrue(other.cleared && other.reconciled)
    }

    @Test
    fun linkingAScheduleToOneTransferLegLinksBothLegs() = withDatabase { database ->
        val (source, target) = reconciledTransfer(database)
        val writer = ActualTransactionWriter(database)

        writer.setScheduleLink(source, "rent-schedule")
        assertEquals("rent-schedule", database.fetchTransaction(source.id)?.scheduleId)
        assertEquals("rent-schedule", database.fetchTransaction(target.id)?.scheduleId)

        writer.setScheduleLink(requireNotNull(database.fetchTransaction(target.id)), null)
        assertNull(database.fetchTransaction(source.id)?.scheduleId)
        assertNull(database.fetchTransaction(target.id)?.scheduleId)
    }

    @Test
    fun newOnOffBudgetTransfersCategorizeOnlyTheOnBudgetLeg() = withDatabase { database ->
        val brokerage = offBudgetAccount(database)
        val service = formService(database, "mixed-new")

        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "40", transferToAccountId = brokerage, categoryId = "grocery", date = 20260910,
        ))
        val outgoing = database.fetchTransactions("checking").single { it.transferId != null }
        assertEquals("grocery", outgoing.categoryId)
        assertNull(database.fetchTransaction(requireNotNull(outgoing.transferId))?.categoryId)

        service.save(ActualTransactionForm(
            accountId = brokerage, type = ActualTransactionType.TRANSFER,
            amount = "15", transferToAccountId = "checking", categoryId = "rent", date = 20260911,
        ))
        val incoming = database.fetchTransactions("checking").single { it.transferId != null && it.date == 20260911 }
        assertEquals("rent", incoming.categoryId)
        assertNull(database.fetchTransaction(requireNotNull(incoming.transferId))?.categoryId)

        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "5", transferToAccountId = "savings", categoryId = "grocery", date = 20260912,
        ))
        val internal = database.fetchTransactions("checking").single { it.transferId != null && it.date == 20260912 }
        assertNull(internal.categoryId)
        assertNull(database.fetchTransaction(requireNotNull(internal.transferId))?.categoryId)
    }

    @Test
    fun editingAnOnOffBudgetTransferKeepsItsCategory() = withDatabase { database ->
        val brokerage = offBudgetAccount(database)
        val service = formService(database, "mixed-edit")
        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "40", transferToAccountId = brokerage, categoryId = "grocery", date = 20260910,
        ))
        val onBudget = database.fetchTransactions("checking").single { it.transferId != null }
        val offBudgetLeg = requireNotNull(database.fetchTransaction(requireNotNull(onBudget.transferId)))

        // From the on-budget leg the editor sends the (unchanged) category back.
        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "45", transferToAccountId = brokerage, categoryId = "grocery", date = 20260910,
        ), onBudget)
        assertEquals("grocery", database.fetchTransaction(onBudget.id)?.categoryId)

        // From the off-budget leg the editor has no category field, so it sends none.
        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "50", transferToAccountId = brokerage, date = 20260910,
        ), requireNotNull(database.fetchTransaction(offBudgetLeg.id)))
        assertEquals("grocery", database.fetchTransaction(onBudget.id)?.categoryId)
        assertEquals(-5_000L, database.fetchTransaction(onBudget.id)?.amountCents)
        assertNull(database.fetchTransaction(offBudgetLeg.id)?.categoryId)
    }

    @Test
    fun uncategorizedFilterIncludesTransfersLeavingTheBudgetOnly() = withDatabase { database ->
        val brokerage = offBudgetAccount(database)
        val service = formService(database, "uncategorized")
        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "40", transferToAccountId = brokerage, date = 20260910,
        ))
        service.save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "5", transferToAccountId = "savings", date = 20260911,
        ))

        val uncategorized = database.fetchTransactions(statusFilter = TransactionStatusFilter.UNCATEGORIZED)
        val leaving = database.fetchTransactions("checking").single { it.date == 20260910 }
        val internal = database.fetchTransactions("checking").single { it.date == 20260911 }
        assertTrue(uncategorized.any { it.id == leaving.id })
        assertTrue(uncategorized.none { it.id == internal.id || it.id == internal.transferId })
        // The off-budget leg never needs a category.
        assertTrue(uncategorized.none { it.accountId == brokerage })
    }

    private fun offBudgetAccount(database: ActualBudgetDatabase): String =
        ActualEntityWriter(database, idFactory = { "off-${UUID.randomUUID()}" })
            .createAccount("Brokerage", offBudget = true, startingBalanceCents = 0)

    /** A checking → savings transfer on 2026-09-10 whose savings leg is cleared and reconciled. */
    private fun reconciledTransfer(database: ActualBudgetDatabase): Pair<ActualTransaction, ActualTransaction> {
        val writer = ActualTransactionWriter(database)
        ActualTransactionFormService(database, writer, idFactory = { UUID.randomUUID().toString() }).save(ActualTransactionForm(
            accountId = "checking", type = ActualTransactionType.TRANSFER,
            amount = "25", transferToAccountId = "savings", date = 20260910,
        ))
        val source = database.fetchTransactions("checking").single { it.transferId != null }
        val target = requireNotNull(database.fetchTransaction(requireNotNull(source.transferId)))
        writer.mutate(updates = listOf(target to target.copy(cleared = true, reconciled = true)))
        return source to requireNotNull(database.fetchTransaction(target.id))
    }

    private fun withDatabase(
        syncTransferDate: Boolean = false,
        extraSql: List<String> = emptyList(),
        block: (ActualBudgetDatabase) -> Unit,
    ) {
        val file = createDatabaseFile(syncTransferDate, extraSql)
        try {
            ActualBudgetDatabase.open(file).use(block)
        } finally {
            file.delete()
        }
    }

    private fun createDatabaseFile(syncTransferDate: Boolean, extraSql: List<String>): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "transaction-parity-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, type TEXT, offbudget INTEGER, closed INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE category_groups (id TEXT PRIMARY KEY, name TEXT, is_income INTEGER, hidden INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT, cat_group TEXT, is_income INTEGER, hidden INTEGER, tombstone INTEGER, sort_order REAL)")
            db.execSQL("CREATE TABLE category_mapping (id TEXT PRIMARY KEY, transferId TEXT)")
            db.execSQL("CREATE TABLE payees (id TEXT PRIMARY KEY, name TEXT, transfer_acct TEXT, tombstone INTEGER)")
            db.execSQL("CREATE TABLE payee_mapping (id TEXT PRIMARY KEY, targetId TEXT)")
            db.execSQL("CREATE TABLE transactions (id TEXT PRIMARY KEY, isParent INTEGER, isChild INTEGER, acct TEXT, category TEXT, amount INTEGER, description TEXT, notes TEXT, date INTEGER, imported_description TEXT, transferred_id TEXT, cleared INTEGER, reconciled INTEGER, sort_order REAL, tombstone INTEGER, parent_id TEXT, financial_id TEXT, pending INTEGER DEFAULT 0, raw_synced_data TEXT)")
            db.execSQL("CREATE TABLE zero_budgets (id TEXT PRIMARY KEY, month INTEGER, category TEXT, amount INTEGER, carryover INTEGER)")
            db.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            db.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, `column` TEXT NOT NULL, value BLOB NOT NULL)")
            db.execSQL("CREATE TABLE preferences (id TEXT PRIMARY KEY, value TEXT)")
            db.execSQL("CREATE TABLE rules (id TEXT PRIMARY KEY, stage TEXT, conditions_op TEXT, conditions TEXT, actions TEXT, tombstone INTEGER)")
            db.execSQL("CREATE TABLE schedules (id TEXT PRIMARY KEY, rule TEXT, name TEXT, posts_transaction INTEGER, completed INTEGER, custom_upcoming_length TEXT, tombstone INTEGER)")
            db.execSQL("CREATE TABLE schedules_next_date (id TEXT PRIMARY KEY, schedule_id TEXT, local_next_date INTEGER, local_next_date_ts INTEGER, base_next_date INTEGER, base_next_date_ts INTEGER)")

            db.execSQL("INSERT INTO accounts VALUES ('checking','Checking','checking',0,0,0,1), ('savings','Savings','savings',0,0,0,2)")
            db.execSQL("INSERT INTO category_groups VALUES ('essential','Essentials',0,0,0,1)")
            db.execSQL("INSERT INTO categories VALUES ('grocery','Groceries','essential',0,0,0,1), ('rent','Rent','essential',0,0,0,2)")
            db.execSQL("INSERT INTO category_mapping VALUES ('grocery','grocery'), ('rent','rent')")
            db.execSQL("INSERT INTO payees VALUES ('store','Store',NULL,0), ('transfer-savings','','savings',0), ('transfer-checking','','checking',0)")
            db.execSQL("INSERT INTO payee_mapping VALUES ('store','store'), ('transfer-savings','transfer-savings'), ('transfer-checking','transfer-checking')")
            db.execSQL(
                "INSERT INTO transactions VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                arrayOf<Any?>("ordinary", 0, 0, "checking", "grocery", -1000, "store", null, 20260901, null, null, 1, 0, 1.0, 0, null, null, 0, null),
            )
            if (syncTransferDate) db.execSQL("INSERT INTO preferences VALUES ('sync-transfer-date','true')")
            extraSql.forEach(db::execSQL)
        }
        return file
    }
}
