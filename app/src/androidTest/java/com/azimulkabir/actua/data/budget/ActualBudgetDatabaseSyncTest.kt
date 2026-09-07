package com.azimulkabir.actua.data.budget

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.sync.CrdtMessage
import com.azimulkabir.actua.data.sync.HlcTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class ActualBudgetDatabaseSyncTest {
    @Test
    fun incomingMessagesAreOrderedDeduplicatedAndApplied() {
        withDatabase { database, file ->
            val earlier = message(1_700_000_000_000, value = "S:Old")
            val later = message(1_700_000_000_001, value = "S:New")

            val received = database.receiveMessages(listOf(later, earlier))
            assertEquals(2, received.appliedMessages.size)
            assertEquals(2, received.insertedMessages.size)
            assertEquals("New", queryString(file, "SELECT name FROM accounts WHERE id = 'acct-1'"))

            val echo = database.receiveMessages(listOf(earlier, later))
            assertTrue(echo.appliedMessages.isEmpty())
            assertTrue(echo.insertedMessages.isEmpty())
            assertEquals(2, database.getMessagesSince(HlcTimestamp.ZERO.toString()).size)
            assertEquals(later.timestamp.toString(), database.maxMessageTimestamp())
        }
    }

    @Test
    fun unknownAndHostileIdentifiersAreLoggedButNeverExecuted() {
        withDatabase { database, file ->
            val hostile = message(
                1_700_000_000_000,
                dataset = "accounts; DROP TABLE accounts;--",
                value = "S:Evil",
            )
            val unknownColumn = message(
                1_700_000_000_001,
                column = "name = 'x' WHERE 1=1; --",
                value = "S:Evil",
            )
            val valid = message(1_700_000_000_002, value = "S:Checking")

            val result = database.receiveMessages(listOf(hostile, unknownColumn, valid))
            assertEquals(3, result.insertedMessages.size)
            assertEquals("Checking", queryString(file, "SELECT name FROM accounts WHERE id = 'acct-1'"))
            assertTrue(tableExists(file, "accounts"))
        }
    }

    @Test
    fun twoClientsConvergeWithOverlapAndReverseDelivery() {
        val first = createDatabaseFile()
        val second = createDatabaseFile()
        try {
            ActualBudgetDatabase.open(first).use { clientA ->
                ActualBudgetDatabase.open(second).use { clientB ->
                    val fromA = listOf(
                        message(1_705_312_800_000, row = "acct-1", column = "name", value = "S:Checking"),
                        message(1_705_312_801_000, row = "acct-1", column = "offbudget", value = "N:0"),
                        message(1_705_397_400_000, row = "acct-2", column = "name", value = "S:Savings"),
                    )
                    val fromB = listOf(
                        message(1_705_579_200_000, node = "bbbbbbbbbbbbbbbb", row = "acct-1", column = "name", value = "S:Everyday Checking"),
                        message(1_705_579_201_000, node = "bbbbbbbbbbbbbbbb", row = "acct-3", column = "name", value = "S:Brokerage"),
                    )
                    clientA.receiveMessages(fromA)
                    clientB.receiveMessages(fromB)
                    clientA.receiveMessages(fromB + fromA.first())
                    clientB.receiveMessages(fromA.reversed())

                    assertEquals(clientA.deriveMerkleFromMessageLog(), clientB.deriveMerkleFromMessageLog())
                    assertEquals(accountRows(first), accountRows(second))
                    assertEquals("Everyday Checking", queryString(first, "SELECT name FROM accounts WHERE id = 'acct-1'"))
                }
            }
        } finally {
            first.delete()
            second.delete()
        }
    }

    private fun message(
        millis: Long,
        node: String = "aaaaaaaaaaaaaaaa",
        dataset: String = "accounts",
        row: String = "acct-1",
        column: String = "name",
        value: String,
    ) = CrdtMessage(HlcTimestamp(millis, 0, node), dataset, row, column, value)

    private fun withDatabase(block: (ActualBudgetDatabase, File) -> Unit) {
        val file = createDatabaseFile()
        try {
            ActualBudgetDatabase.open(file).use { block(it, file) }
        } finally {
            file.delete()
        }
    }

    private fun createDatabaseFile(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "sync-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, offbudget INTEGER DEFAULT 0, closed INTEGER DEFAULT 0, tombstone INTEGER DEFAULT 0)")
            listOf("categories", "category_groups", "payee_mapping", "payees", "transactions", "zero_budgets")
                .forEach { database.execSQL("CREATE TABLE $it (id TEXT PRIMARY KEY)") }
            database.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            database.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, column TEXT NOT NULL, value BLOB NOT NULL)")
        }
        return file
    }

    private fun queryString(file: File, sql: String): String? =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery(sql, null).use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getString(0) else null
            }
        }

    private fun tableExists(file: File, table: String): Boolean =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(table))
                .use { it.moveToFirst() }
        }

    private fun accountRows(file: File): List<String> =
        SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { database ->
            database.rawQuery("SELECT id || ':' || COALESCE(name, '') || ':' || offbudget FROM accounts ORDER BY id", null)
                .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        }
}
