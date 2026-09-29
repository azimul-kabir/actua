package com.azimulkabir.actua.data.sync

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.budget.ActualBudgetWriter
import com.azimulkabir.actua.data.budget.ActualEntityWriter
import com.azimulkabir.actua.data.budget.ActualTagWriter
import com.azimulkabir.actua.data.budget.ActualTransactionWriter
import com.azimulkabir.actua.data.budget.BlankBudgetFactory
import com.azimulkabir.actua.data.location.Coordinates
import com.azimulkabir.actua.data.location.PayeeLocationWriter
import com.azimulkabir.actua.data.schedules.ActualScheduleWriter
import com.azimulkabir.actua.data.schedules.DayDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Regression for #691: a writer built before sync receives a message from a device whose clock
 * runs fast (within Actual's drift allowance) must still stamp its next local edit later than
 * that message, or the local value loses on every other client.
 */
class WriterClockHighWaterTest {
    @Test fun transactionWriter() = assertNextEditIsNewest { database, _ ->
        val writer = ActualTransactionWriter(database, nodeId = NODE)
        receiveFastRemoteMessage(database)
        writer.resolveOrCreatePayee("Local payee")
    }

    @Test fun entityWriter() = assertNextEditIsNewest { database, categoryId ->
        val writer = ActualEntityWriter(database, nodeId = NODE)
        receiveFastRemoteMessage(database)
        writer.renameCategory(categoryId, "Local name")
    }

    @Test fun budgetWriter() = assertNextEditIsNewest { database, categoryId ->
        val writer = ActualBudgetWriter(database, nodeId = NODE)
        receiveFastRemoteMessage(database)
        writer.setAmount("2026-09", categoryId, 12_345)
    }

    @Test fun scheduleWriter() = assertNextEditIsNewest { database, _ ->
        val writer = ActualScheduleWriter(database, nodeId = NODE)
        receiveFastRemoteMessage(database)
        writer.advance("next-date-1", DayDate(2026, 10, 1), null)
    }

    @Test fun tagWriter() = assertNextEditIsNewest { database, _ ->
        val writer = ActualTagWriter(database, nodeId = NODE, idFactory = { "tag-1" })
        receiveFastRemoteMessage(database)
        writer.create("local")
    }

    @Test fun payeeLocationWriter() = assertNextEditIsNewest { database, _ ->
        val payee = ActualTransactionWriter(database).resolveOrCreatePayee("Located payee")
        val writer = PayeeLocationWriter(database, nodeId = NODE)
        receiveFastRemoteMessage(database)
        assertTrue(writer.record(payee.id, Coordinates(40.0, -74.0)) != null)
    }

    private fun receiveFastRemoteMessage(database: ActualBudgetDatabase) {
        // Two minutes ahead is inside Actual's five-minute drift allowance.
        val remote = HlcTimestamp(System.currentTimeMillis() + 120_000, 0, REMOTE_NODE)
        database.receiveMessages(listOf(
            CrdtMessage(remote, "payees", "remote-payee", "name", CrdtValue.serialize("Remote payee")),
        ))
        assertEquals(remote.toString(), database.maxMessageTimestamp())
    }

    private fun assertNextEditIsNewest(edit: (ActualBudgetDatabase, categoryId: String) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "writer-clock-${UUID.randomUUID()}.sqlite")
        try {
            BlankBudgetFactory.create(file)
            val categoryId = SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery("SELECT id FROM categories WHERE tombstone = 0 ORDER BY sort_order LIMIT 1", null)
                    .use { it.moveToFirst(); it.getString(0) }
            }
            ActualBudgetDatabase.open(file).use { database ->
                edit(database, categoryId)
                val newest = HlcTimestamp.parse(database.maxMessageTimestamp()!!)!!
                assertEquals(NODE, newest.node)
            }
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                db.rawQuery(
                    "SELECT timestamp FROM messages_crdt WHERE timestamp LIKE ? ORDER BY timestamp",
                    arrayOf("%-$REMOTE_NODE"),
                ).use { remote ->
                    assertTrue(remote.moveToFirst())
                    val remoteTimestamp = remote.getString(0)
                    db.rawQuery(
                        "SELECT COUNT(*) FROM messages_crdt WHERE timestamp LIKE ? AND timestamp < ?",
                        arrayOf("%-$NODE", remoteTimestamp),
                    ).use { older ->
                        older.moveToFirst()
                        assertEquals("local edits older than the received message", 0, older.getInt(0))
                    }
                }
            }
        } finally {
            file.delete()
        }
    }

    private companion object {
        const val NODE = "aaaaaaaaaaaaaaaa"
        const val REMOTE_NODE = "0000000000000001"
    }
}
