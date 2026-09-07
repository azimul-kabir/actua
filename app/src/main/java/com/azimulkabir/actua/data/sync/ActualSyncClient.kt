package com.azimulkabir.actua.data.sync

import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.network.ActualServerClient

sealed class ActualSyncException(message: String) : Exception(message) {
    data object OutOfSync : ActualSyncException("Unable to converge with the server")
}

data class SyncOutcome(
    val sentMessages: Int,
    val receivedMessages: Int,
    val attempts: Int,
    val timestamp: String,
)

/** Actual's offline-first, Merkle-guided CRDT synchronization loop. */
class ActualSyncClient(
    private val serverUrl: String,
    private val token: String,
    private val server: ActualServerClient,
    private val database: ActualBudgetDatabase,
    private val fileId: String,
    private val groupId: String,
    keyId: String? = null,
    cipher: MessageCipher? = null,
    nodeId: String = HybridLogicalClock.generateNodeId(),
) {
    private val encoder = SyncEncoder(cipher)
    private val encryptionKeyId = keyId
    private val clock: HybridLogicalClock
    private var merkle: MerkleTree
    private var lastSyncedTimestamp: String?
    private val downloadBaselineTimestamp: String?

    init {
        val storedClock = database.loadClock()
        clock = HybridLogicalClock(node = nodeId)
        database.maxMessageTimestamp()?.let(HlcTimestamp::parse)?.let(clock::advance)
        storedClock?.timestamp?.takeIf(String::isNotBlank)?.let(HlcTimestamp::parse)?.let(clock::advance)
        merkle = database.deriveMerkleFromMessageLog()
        lastSyncedTimestamp = storedClock?.timestamp?.takeIf(String::isNotBlank)
        downloadBaselineTimestamp = database.maxMessageTimestamp()
    }

    @Synchronized
    fun sync(): SyncOutcome {
        val totals = MutableTotals()
        fullSync(null, 0, totals)
        return SyncOutcome(totals.sent, totals.received, totals.attempts, lastSyncedTimestamp.orEmpty())
    }

    private fun fullSync(since: String?, attempt: Int, totals: MutableTotals) {
        if (attempt >= MAX_ATTEMPTS) throw ActualSyncException.OutOfSync
        totals.attempts++

        // The local log is the source of truth; never adopt a remote tree we
        // have not earned through received messages.
        merkle = database.deriveMerkleFromMessageLog()
        val effectiveLastSynced = lastSyncedTimestamp?.takeIf(String::isNotBlank)
        val sinceTimestamp = since
            ?: effectiveLastSynced
            ?: downloadBaselineTimestamp?.takeIf(String::isNotBlank)
            ?: HlcTimestamp.ZERO.toString()

        val localMessages = if (since != null || effectiveLastSynced != null) {
            database.getMessagesSince(sinceTimestamp)
        } else {
            database.getMessagesSince(downloadBaselineTimestamp.orEmpty())
        }
        totals.sent += localMessages.size

        val request = encoder.encode(localMessages, fileId, groupId, encryptionKeyId, sinceTimestamp)
        val response = encoder.decode(server.postSync(serverUrl, token, request))
        totals.received += response.messages.size

        response.messages.forEach { clock.receive(it.timestamp) }
        val received = database.receiveMessages(response.messages)
        received.insertedMessages.forEach { merkle = merkle.inserting(it.timestamp) }
        if (received.insertedMessages.isNotEmpty()) merkle = merkle.pruned()

        val divergence = merkle.diff(MerkleTree(response.merkle))
        if (divergence != null) {
            fullSync(HlcTimestamp(divergence, 0, "0").toString(), attempt + 1, totals)
            return
        }

        lastSyncedTimestamp = clock.current().toString()
        database.saveClock(ActualBudgetDatabase.ClockRecord(lastSyncedTimestamp.orEmpty(), merkle.root))
    }

    private data class MutableTotals(var sent: Int = 0, var received: Int = 0, var attempts: Int = 0)

    companion object {
        private const val MAX_ATTEMPTS = 10
    }
}
