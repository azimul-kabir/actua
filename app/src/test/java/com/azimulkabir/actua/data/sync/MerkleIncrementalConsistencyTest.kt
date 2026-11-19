package com.azimulkabir.actua.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

/** Investigation for #1600: compare the complete retained trie, not just its XOR root. */
class MerkleIncrementalConsistencyTest {
    @Test fun existingTimestampVectorsAgreeAfterEveryPrunedInsert() {
        // Real timestamps from SyncCoreFixtureTest's insert/diff and bulk-build vectors.
        val timestamps = listOf(
            "2018-11-13T13:20:40.122Z-0000-0123456789ABCDEF",
            "2018-11-14T13:05:35.122Z-0000-0123456789ABCDEF",
            "2018-11-15T22:19:00.122Z-0000-0123456789ABCDEF",
            "2018-11-20T13:19:40.122Z-0000-0123456789ABCDEF",
            "2018-11-25T13:19:40.122Z-0000-0123456789ABCDEF",
            "2018-11-01T00:47:12.000Z-0000-0123456789ABCDEF",
            "2018-11-01T00:47:39.000Z-0001-0123456789ABCDEF",
            "2018-11-01T01:15:00.000Z-0000-0123456789ABCDEF",
        ).map { requireNotNull(HlcTimestamp.parse(it)) }
        for (order in listOf(timestamps, timestamps.sorted(), timestamps.reversed())) {
            assertBatchesMatch(order.map(::listOf))
        }
    }

    @Test fun seededBatchesAgreeInChronologicalReverseAndShuffledOrder() {
        repeat(12) { seed ->
            val random = Random(seed)
            val timestamps = List(320) { index ->
                // Mix same-minute messages with a multi-year spread and ternary branch boundaries.
                val minute = when (index % 4) {
                    0 -> 30_000_000L
                    1 -> 30_000_000L + index
                    2 -> 30_000_000L + random.nextLong(1_000_000)
                    else -> random.nextLong(1, 60_000_000)
                }
                HlcTimestamp(minute * 60_000 + random.nextInt(60_000), index, "0000000000000001")
            }
            for (order in listOf(timestamps.sorted(), timestamps.sortedDescending(), timestamps.shuffled(random))) {
                val batches = mutableListOf<List<HlcTimestamp>>()
                var offset = 0
                while (offset < order.size) {
                    val end = minOf(order.size, offset + random.nextInt(1, 33))
                    batches += order.subList(offset, end)
                    offset = end
                }
                assertBatchesMatch(batches)
            }
        }
    }

    @Test fun lateMessagesInDroppedBranchesStillAgreeWithRebuild() {
        val start = 30_000_000L
        val times = (0..80).map { HlcTimestamp((start + it) * 60_000, 0, "1") }
        assertBatchesMatch(listOf(times.takeLast(20), times.take(20), times.subList(20, 61)))
    }

    @Test fun duplicateDeliveryMustBeDeduplicatedBeforeIncrementalInsertion() {
        val timestamp = HlcTimestamp(1_700_000_000_000, 0, "1")
        val once = MerkleTree().inserting(timestamp).pruned()
        // SQLite rejects repeated log timestamps. Feeding the delivery batch instead of only
        // inserted rows to a candidate incremental updater would XOR the timestamp out again.
        assertNotEquals(once, once.inserting(timestamp).pruned())
        assertEquals(once, rebuild(listOf(timestamp, timestamp).distinct()))
    }

    private fun assertBatchesMatch(batches: List<List<HlcTimestamp>>) {
        val log = mutableListOf<HlcTimestamp>()
        var incremental = MerkleTree()
        batches.forEachIndexed { index, batch ->
            log += batch
            batch.forEach { incremental = incremental.inserting(it) }
            incremental = incremental.pruned()
            // Clock persistence serializes this shape. Exercise a reopen between every batch.
            incremental = MerkleTree(MerkleJson.decode(MerkleJson.encode(incremental.root)))
            val rebuilt = rebuild(log)
            assertEquals("batch $index (${log.size} messages)", rebuilt, incremental)
            assertNull(rebuilt.diff(incremental))
            assertNull(incremental.diff(rebuilt))
        }
    }

    private fun rebuild(log: List<HlcTimestamp>): MerkleTree {
        // Same minute aggregation as ActualBudgetDatabase.deriveMerkleFromMessageLog.
        val buckets = mutableMapOf<Long, Int>()
        log.forEach { timestamp ->
            val raw = timestamp.toString()
            val minute = requireNotNull(HlcTimestamp.minutesSinceEpoch(raw)) * 60_000
            buckets[minute] = (buckets[minute] ?: 0) xor MurmurHash3.hash(raw).toInt()
        }
        return MerkleTree.building(buckets).pruned()
    }
}
