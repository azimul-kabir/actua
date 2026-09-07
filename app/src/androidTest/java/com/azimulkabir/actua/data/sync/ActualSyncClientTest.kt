package com.azimulkabir.actua.data.sync

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.network.ActualHttpResponse
import com.azimulkabir.actua.data.network.ActualServerClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.UUID

class ActualSyncClientTest {
    @Test
    fun freshDownloadUsesSnapshotHighWaterMarkAndDoesNotRepushHistory() {
        withDatabase { database ->
            val snapshot = message("2024-01-01T00:00:00.000Z", "aaaaaaaaaaaaaaaa", "acct-1", "S:Checking")
            database.insertMessages(listOf(snapshot))
            val server = FakeSyncServer(listOf(snapshot))
            val client = client(database, server.client)

            val outcome = client.sync()

            assertEquals(snapshot.timestamp.toString(), server.requests.single().since)
            assertTrue(server.requests.single().messages.isEmpty())
            assertEquals(1, outcome.attempts)
            assertTrue(database.loadClock()?.timestamp?.isNotBlank() == true)
        }
    }

    @Test
    fun clientsExchangeMessagesAndConverge() {
        withDatabase { database ->
            val remote = message("2024-01-02T00:00:00.000Z", "bbbbbbbbbbbbbbbb", "acct-2", "S:Savings")
            val server = FakeSyncServer(listOf(remote))
            val client = client(database, server.client)
            val local = message("2024-01-03T00:00:00.000Z", "aaaaaaaaaaaaaaaa", "acct-1", "S:Checking")
            database.receiveMessages(listOf(local))

            val outcome = client.sync()

            assertEquals(1, outcome.sentMessages)
            assertEquals(2, outcome.receivedMessages) // remote change plus the server echo of our local message
            assertEquals(server.merkle(), database.deriveMerkleFromMessageLog())
            assertEquals(2, database.getMessagesSince(HlcTimestamp.ZERO.toString()).size)
        }
    }

    @Test
    fun permanentlyDishonestMerkleIsBounded() {
        withDatabase { database ->
            val badMerkle = "{\"hash\":1}"
            val server = ActualServerClient { ActualHttpResponse(200, SyncProtocol.encodeResponse(SyncResponsePayload(emptyList(), badMerkle))) }
            assertThrows(ActualSyncException.OutOfSync::class.java) { client(database, server).sync() }
        }
    }

    private fun client(database: ActualBudgetDatabase, server: ActualServerClient) = ActualSyncClient(
        serverUrl = "https://actual.test",
        token = "token",
        server = server,
        database = database,
        fileId = "file",
        groupId = "group",
        nodeId = "cccccccccccccccc",
    )

    private fun message(iso: String, node: String, row: String, value: String) = CrdtMessage(
        HlcTimestamp.parse("$iso-0000-$node")!!,
        "accounts",
        row,
        "name",
        value,
    )

    private fun withDatabase(block: (ActualBudgetDatabase) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "client-${UUID.randomUUID()}.sqlite")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { database ->
            database.execSQL("CREATE TABLE accounts (id TEXT PRIMARY KEY, name TEXT, offbudget INTEGER DEFAULT 0, closed INTEGER DEFAULT 0, tombstone INTEGER DEFAULT 0)")
            listOf("categories", "category_groups", "payee_mapping", "payees", "transactions", "zero_budgets")
                .forEach { database.execSQL("CREATE TABLE $it (id TEXT PRIMARY KEY)") }
            database.execSQL("CREATE TABLE messages_clock (id INTEGER PRIMARY KEY, clock TEXT)")
            database.execSQL("CREATE TABLE messages_crdt (id INTEGER PRIMARY KEY, timestamp TEXT NOT NULL UNIQUE, dataset TEXT NOT NULL, row TEXT NOT NULL, column TEXT NOT NULL, value BLOB NOT NULL)")
        }
        try {
            ActualBudgetDatabase.open(file).use(block)
        } finally {
            file.delete()
        }
    }

    private class FakeSyncServer(initial: List<CrdtMessage>) {
        private val log = initial.associateByTo(mutableMapOf()) { it.timestamp.toString() }
        val requests = mutableListOf<SyncRequestPayload>()
        val client = ActualServerClient { request ->
            val decoded = SyncProtocol.decodeRequest(request.body ?: byteArrayOf())
            requests += decoded
            decoded.messages.forEach { envelope ->
                val inner = SyncProtocol.decodeMessage(envelope.content)
                log[envelope.timestamp] = CrdtMessage(
                    HlcTimestamp.parse(envelope.timestamp)!!,
                    inner.dataset,
                    inner.row,
                    inner.column,
                    inner.value,
                )
            }
            val outgoing = log.values
                .filter { it.timestamp.toString() > decoded.since }
                .map { MessageEnvelope(it.timestamp.toString(), false, SyncProtocol.encodeMessage(it)) }
            ActualHttpResponse(
                200,
                SyncProtocol.encodeResponse(SyncResponsePayload(outgoing, MerkleJson.encode(merkle().root))),
            )
        }

        fun merkle(): MerkleTree = log.values.fold(MerkleTree()) { tree, message -> tree.inserting(message.timestamp) }.pruned()
    }
}
