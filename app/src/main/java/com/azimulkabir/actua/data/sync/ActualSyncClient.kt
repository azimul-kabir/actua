package com.azimulkabir.actua.data.sync

import com.azimulkabir.actua.data.budget.ActualBudgetDatabase
import com.azimulkabir.actua.data.network.ActualServerClient

sealed class ActualSyncException(message: String) : Exception(message) {
    /** Actual's `out-of-sync`: the Merkle loop stopped converging. The next run rebuilds the tree from the log. */
    data object OutOfSync : ActualSyncException("Unable to converge with the server")
    /** Actual's `clock-drift`: a received message is more than five minutes ahead of this device's clock. */
    data object ClockDrift : ActualSyncException(
        "This device's clock is more than 5 minutes behind the budget's other devices. " +
            "Turn on automatic date and time in Android settings, then sync again. Your local changes are kept.",
    )
    /** Actual's `decrypt-failure`: the saved encryption key can't read the server's messages. */
    data object DecryptionFailed : ActualSyncException(
        "Actua couldn't decrypt this budget's sync data with its saved encryption key. Check the budget's " +
            "encryption in Actual's web app. Syncing is paused and your local changes are kept.",
    )
}

/**
 * Actual's `_fullSync` loop limit: give up after 10 passes that return the same Merkle diff time,
 * or after 100 passes in total. A pass during which local messages were written resets the count,
 * so a user who keeps editing never trips it.
 */
internal class SyncLoopLimit {
    private var count = 0
    private var previousDiffTime: Long? = null

    /** Records a pass that ended with [diffTime]; throws [ActualSyncException.OutOfSync] when Actual would. */
    fun recordDivergence(diffTime: Long, localChanged: Boolean) {
        if ((count >= SAME_DIFF_LIMIT && diffTime == previousDiffTime) || count >= TOTAL_LIMIT) {
            throw ActualSyncException.OutOfSync
        }
        count = if (localChanged) 0 else count + 1
        previousDiffTime = diffTime
    }

    companion object {
        const val SAME_DIFF_LIMIT = 10
        const val TOTAL_LIMIT = 100
    }
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
        val messageHighWater = recoverableTimestamp(database.maxMessageTimestamp())
        val persistedTimestamp = recoverableTimestamp(storedClock?.timestamp)
        clock = HybridLogicalClock(node = nodeId)
        messageHighWater?.let(HlcTimestamp::parse)?.let(clock::advance)
        persistedTimestamp?.let(HlcTimestamp::parse)?.let(clock::advance)
        merkle = database.deriveMerkleFromMessageLog()
        // Keep a valid older sync boundary: newer log entries may be unsent edits.
        // Invalid legacy state falls back to the log; Merkle reconciliation will
        // recover any differences, including writes committed before a restart.
        lastSyncedTimestamp = persistedTimestamp ?: messageHighWater
        downloadBaselineTimestamp = messageHighWater
    }

    @Synchronized
    fun sync(): SyncOutcome {
        val totals = MutableTotals()
        val limit = SyncLoopLimit()
        var since: String? = null
        while (true) {
            val divergence = syncPass(since, totals) ?: break
            limit.recordDivergence(divergence.diffTime, divergence.localChanged)
            since = HlcTimestamp(divergence.diffTime, 0, "0").toString()
        }
        lastSyncedTimestamp = clock.current().toString()
        database.saveClock(ActualBudgetDatabase.ClockRecord(lastSyncedTimestamp.orEmpty(), merkle.root))
        return SyncOutcome(totals.sent, totals.received, totals.attempts, lastSyncedTimestamp.orEmpty())
    }

    /** One `/sync` round trip; returns where the trees still differ, or null once in sync. */
    private fun syncPass(since: String?, totals: MutableTotals): Divergence? {
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
        // Actual snapshots its clock here; writers log every local edit, so the log's high-water mark
        // moving during the round trip means the user edited meanwhile.
        val localHighWater = database.maxMessageTimestamp()

        val request = encoder.encode(localMessages, fileId, groupId, encryptionKeyId, sinceTimestamp)
        val responseBytes = server.postSync(serverUrl, token, request)
        val response = try {
            encoder.decode(responseBytes)
        } catch (_: SyncEncryptionException.DecryptionFailed) {
            throw ActualSyncException.DecryptionFailed
        }
        totals.received += response.messages.size
        val localChanged = database.maxMessageTimestamp() != localHighWater

        try {
            response.messages.forEach { clock.receive(it.timestamp) }
        } catch (_: HlcException.ClockDrift) {
            throw ActualSyncException.ClockDrift
        }
        val received = database.receiveMessages(response.messages)
        received.insertedMessages.forEach { merkle = merkle.inserting(it.timestamp) }
        if (received.insertedMessages.isNotEmpty()) merkle = merkle.pruned()

        return merkle.diff(MerkleTree(response.merkle))?.let { Divergence(it, localChanged) }
    }

    private data class Divergence(val diffTime: Long, val localChanged: Boolean)

    private data class MutableTotals(var sent: Int = 0, var received: Int = 0, var attempts: Int = 0)

    companion object {
        private fun recoverableTimestamp(value: String?): String? = value?.takeIf {
            !it.startsWith("1970-") && HlcTimestamp.parse(it) != null
        }
    }
}
