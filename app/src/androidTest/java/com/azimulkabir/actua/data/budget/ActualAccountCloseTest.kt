package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.model.ActualTransaction
import com.azimulkabir.actua.data.schedules.DayDate
import com.azimulkabir.actua.data.sync.CrdtValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Actual's `closeAccount` (loot-core `accounts/app.ts`): unlink, delete when empty, force close,
 * or close with a balance transfer, each as one message batch.
 */
class ActualAccountCloseTest {
    @Test fun accountWithoutTransactionsIsDeleted() = withBudget { database, file ->
        val writer = ActualEntityWriter(database)
        val account = writer.createAccount("Empty", offBudget = false, startingBalanceCents = 0)
        val before = messageCount(file)

        assertEquals(ActualEntityWriter.CloseOutcome.DELETED, writer.closeAccount(account))

        assertEquals(listOf(Cell("accounts", account, "tombstone", 1L)), messagesAfter(file, before))
        assertTrue(database.fetchAccounts().none { it.id == account })
    }

    @Test fun nonZeroBalanceRequiresADifferentTransferAccount() = withBudget { database, file ->
        val writer = ActualEntityWriter(database)
        val account = writer.createAccount("Wallet", offBudget = false, startingBalanceCents = 5_000)
        val before = messageCount(file)

        assertThrows(IllegalArgumentException::class.java) { writer.closeAccount(account) }
        assertThrows(IllegalArgumentException::class.java) { writer.closeAccount(account, transferAccountId = account) }

        assertEquals("a rejected close writes nothing", before, messageCount(file))
        assertEquals(false, database.fetchAccounts().single { it.id == account }.closed)
    }

    @Test fun balanceMovesToTheTransferAccountAsAClosingTransfer() = withBudget { database, file ->
        val writer = ActualEntityWriter(database)
        val closing = writer.createAccount("Wallet", offBudget = false, startingBalanceCents = 12_345)
        val target = writer.createAccount("Checking", offBudget = false, startingBalanceCents = 100)
        val before = messageCount(file)

        assertEquals(ActualEntityWriter.CloseOutcome.CLOSED, writer.closeAccount(closing, transferAccountId = target))

        val accounts = database.fetchAccounts().associateBy { it.id }
        assertEquals(true, accounts.getValue(closing).closed)
        assertEquals(0L, accounts.getValue(closing).balanceCents)
        assertEquals(12_445L, accounts.getValue(target).balanceCents)

        val messages = messagesAfter(file, before)
        assertEquals(Cell("accounts", closing, "closed", 1L), messages.first())
        val legs = messages.filter { it.dataset == "transactions" }.groupBy { it.row }
            .mapValues { (_, cells) -> cells.associate { it.column to it.value } }
        assertEquals(2, legs.size)
        val source = legs.values.single { it["acct"] == closing }
        val counterpart = legs.values.single { it["acct"] == target }
        val sourceId = legs.entries.single { it.value === source }.key
        val counterpartId = legs.entries.single { it.value === counterpart }.key
        assertEquals(-12_345L, source["amount"]); assertEquals(12_345L, counterpart["amount"])
        assertEquals(database.transferPayeeId(target), source["description"])
        assertEquals(database.transferPayeeId(closing), counterpart["description"])
        assertEquals(counterpartId, source["transferred_id"]); assertEquals(sourceId, counterpart["transferred_id"])
        assertEquals("Closing account", source["notes"]); assertEquals("Closing account", counterpart["notes"])
        assertEquals(DayDate.today().yyyymmdd.toLong(), source["date"])
        assertNull("on-budget to on-budget transfers are uncategorized", source["category"])
        assertNull(counterpart["category"])
        assertEquals(1L, source["cleared"]); assertEquals(0L, counterpart["cleared"])
    }

    @Test fun onBudgetToOffBudgetTransferRequiresACategory() = withBudget { database, _ ->
        val writer = ActualEntityWriter(database)
        val group = writer.createCategoryGroup("Everyday")
        val category = writer.createCategory("Adjustments", group)
        val closing = writer.createAccount("Wallet", offBudget = false, startingBalanceCents = 2_000)
        val target = writer.createAccount("Brokerage", offBudget = true, startingBalanceCents = 0)

        assertThrows(IllegalArgumentException::class.java) { writer.closeAccount(closing, transferAccountId = target) }
        writer.closeAccount(closing, transferAccountId = target, categoryId = category)

        val closingLeg = database.fetchTransactions().single { it.accountId == closing && it.notes == "Closing account" }
        val targetLeg = database.fetchTransactions().single { it.accountId == target && it.notes == "Closing account" }
        assertEquals(category, closingLeg.categoryId)
        assertNull(targetLeg.categoryId)
        assertEquals(-2_000L, closingLeg.amountCents)
    }

    @Test fun zeroBalanceWithTransactionsOnlyCloses() = withBudget { database, file ->
        val writer = ActualEntityWriter(database)
        val account = writer.createAccount("Old", offBudget = false, startingBalanceCents = 700)
        ActualTransactionWriter(database).createTransaction(transaction(account, -700), applyRules = false)
        val before = messageCount(file)

        assertEquals(ActualEntityWriter.CloseOutcome.CLOSED, writer.closeAccount(account))

        assertEquals(listOf(Cell("accounts", account, "closed", 1L)), messagesAfter(file, before))
        assertEquals("closing an already-closed account does nothing", null, writer.closeAccount(account))
        assertEquals(before + 1, messageCount(file))
    }

    @Test fun forceCloseDeletesTransactionsAndDetachesTransferLegs() = withBudget { database, file ->
        val writer = ActualEntityWriter(database)
        val closing = writer.createAccount("Wallet", offBudget = false, startingBalanceCents = 900)
        val other = writer.createAccount("Checking", offBudget = false, startingBalanceCents = 0)
        val out = transaction(closing, -300).copy(payeeId = database.transferPayeeId(other))
        val into = transaction(other, 300).copy(payeeId = database.transferPayeeId(closing))
        ActualTransactionWriter(database).createTransfer(out.copy(transferId = into.id), into.copy(transferId = out.id))
        val closingPayee = database.transferPayeeId(closing)!!
        val closingRows = database.fetchTransactions().filter { it.accountId == closing }.map { it.id }.toSet()
        val before = messageCount(file)

        assertEquals(ActualEntityWriter.CloseOutcome.FORCE_DELETED, writer.closeAccount(closing, forced = true))

        val messages = messagesAfter(file, before).toSet()
        closingRows.forEach { assertTrue(Cell("transactions", it, "tombstone", 1L) in messages) }
        assertTrue(Cell("transactions", into.id, "description", null) in messages)
        assertTrue(Cell("transactions", into.id, "transferred_id", null) in messages)
        assertTrue(Cell("accounts", closing, "tombstone", 1L) in messages)
        assertTrue(Cell("payees", closingPayee, "tombstone", 1L) in messages)
        assertEquals(closingRows.size * 1 + 2 + 2, messages.size)

        assertTrue(database.fetchAccounts().none { it.id == closing })
        assertTrue(database.fetchPayees().none { it.id == closingPayee })
        val kept = database.fetchTransactions().single { it.id == into.id }
        assertNull(kept.transferId); assertNull(kept.payeeId)
        assertEquals(300L, database.fetchAccounts().single { it.id == other }.balanceCents)
    }

    @Test fun closingUnlinksBankSyncInTheSameBatch() = withBudget { database, file ->
        val writer = ActualEntityWriter(database)
        val account = writer.createAccount("Card", offBudget = false, startingBalanceCents = 0)
        writer.linkBankAccount(account, "gc-1", "goCardless", "requisition-1")
        writer.recordBankSyncStatus(account, "synced")
        val before = messageCount(file)

        writer.closeAccount(account)

        val cells = messagesAfter(file, before).filter { it.dataset == "accounts" && it.row == account }
            .associate { it.column to it.value }
        listOf("account_id", "bank", "account_sync_source", "bank_sync_status").forEach {
            assertTrue("$it is cleared", it in cells); assertNull(cells[it])
        }
        assertEquals(1L, cells["tombstone"])
        assertTrue(database.fetchBankSyncAccounts().none { it.id == account })
    }

    private data class Cell(val dataset: String, val row: String, val column: String, val value: Any?)

    private fun transaction(account: String, amount: Long) = ActualTransaction(
        id = UUID.randomUUID().toString(), accountId = account, date = 20260101, amountCents = amount,
        payeeId = null, payeeName = null, categoryId = null, categoryName = null, notes = null,
        cleared = false, reconciled = false, transferId = null, isParent = false, parentId = null,
        tombstone = false, sortOrder = 1.0, importedPayee = null, scheduleId = null, transferAccountId = null,
    )

    private fun withBudget(block: (ActualBudgetDatabase, File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "close-account-${UUID.randomUUID()}.sqlite").also(BlankBudgetFactory::create)
        try { ActualBudgetDatabase.open(file).use { block(it, file) } } finally { file.delete() }
    }

    private fun messageCount(file: File): Int = readOnly(file) { raw ->
        raw.rawQuery("SELECT COUNT(*) FROM messages_crdt", null).use { it.moveToFirst(); it.getInt(0) }
    }

    private fun messagesAfter(file: File, count: Int): List<Cell> = readOnly(file) { raw ->
        raw.rawQuery(
            "SELECT dataset, row, `column`, value FROM messages_crdt ORDER BY timestamp LIMIT -1 OFFSET ?",
            arrayOf(count.toString()),
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Cell(
                    cursor.getString(0), cursor.getString(1), cursor.getString(2),
                    when (val value = CrdtValue.deserialize(cursor.getString(3))) {
                        CrdtValue.Null -> null
                        is CrdtValue.Integer -> value.value
                        is CrdtValue.Decimal -> value.value
                        is CrdtValue.Text -> value.value
                    },
                ))
            }
        }
    }

    private fun <T> readOnly(file: File, block: (SQLiteDatabase) -> T): T =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use(block)
}
