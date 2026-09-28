package com.azimulkabir.actua.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Vectors ported verbatim from Actual's `packages/crdt/src/crdt/timestamp.test.ts` and
 * `merkle.test.ts` at 59fe126f. Upstream's `Timestamp.init({ node: '1' })` pads the node to
 * `0000000000000001` and starts the clock at millis 0, counter 0.
 */
class SyncUpstreamVectorTest {
    private var now = 0L
    private fun clock() = HybridLogicalClock(node = "0000000000000001", nowMillis = { now })
    private fun ts(value: String) = requireNotNull(HlcTimestamp.parse(value)) { value }

    private fun assertSends(clock: HybridLogicalClock, at: Long, expected: String) {
        now = at
        assertEquals(expected, clock.send().toString())
    }

    private fun assertReceives(clock: HybridLogicalClock, at: Long, remote: String, expected: String) {
        now = at
        assertEquals(expected, clock.receive(ts(remote)).toString())
    }

    @Test
    fun sendIsMonotonicWithMonotonicStutteringAndRegressingClocks() {
        clock().let {
            assertSends(it, 10, "1970-01-01T00:00:00.010Z-0000-0000000000000001")
            assertSends(it, 11, "1970-01-01T00:00:00.011Z-0000-0000000000000001")
            assertSends(it, 12, "1970-01-01T00:00:00.012Z-0000-0000000000000001")
        }
        clock().let {
            assertSends(it, 20, "1970-01-01T00:00:00.020Z-0000-0000000000000001")
            assertSends(it, 20, "1970-01-01T00:00:00.020Z-0001-0000000000000001")
            assertSends(it, 20, "1970-01-01T00:00:00.020Z-0002-0000000000000001")
            assertSends(it, 21, "1970-01-01T00:00:00.021Z-0000-0000000000000001")
        }
        clock().let {
            assertSends(it, 30, "1970-01-01T00:00:00.030Z-0000-0000000000000001")
            assertSends(it, 29, "1970-01-01T00:00:00.030Z-0001-0000000000000001")
            assertSends(it, 29, "1970-01-01T00:00:00.030Z-0002-0000000000000001")
            assertSends(it, 31, "1970-01-01T00:00:00.031Z-0000-0000000000000001")
        }
    }

    @Test
    fun sendFailsWithCounterOverflowAndClockDrift() {
        now = 40
        val overflowing = clock()
        repeat(65_536) { overflowing.send() }
        assertThrows(HlcException.CounterOverflow::class.java) { overflowing.send() }

        now = -(5 * 60 * 1_000L + 1)
        assertThrows(HlcException.ClockDrift::class.java) { clock().send() }
    }

    @Test
    fun receiveIsMonotonicWithGlobalMonotonicAndStutteringClocks() {
        clock().let {
            assertReceives(it, 52, "1970-01-01T00:00:00.051Z-0000-0000000000000002", "1970-01-01T00:00:00.052Z-0000-0000000000000001")
            assertReceives(it, 54, "1970-01-01T00:00:00.053Z-0000-0000000000000002", "1970-01-01T00:00:00.054Z-0000-0000000000000001")
            assertReceives(it, 56, "1970-01-01T00:00:00.055Z-0000-0000000000000002", "1970-01-01T00:00:00.056Z-0000-0000000000000001")
        }
        clock().let {
            assertReceives(it, 61, "1970-01-01T00:00:00.062Z-0000-0000000000000002", "1970-01-01T00:00:00.062Z-0001-0000000000000001")
            assertReceives(it, 62, "1970-01-01T00:00:00.062Z-0001-0000000000000002", "1970-01-01T00:00:00.062Z-0002-0000000000000001")
            assertReceives(it, 62, "1970-01-01T00:00:00.062Z-0002-0000000000000002", "1970-01-01T00:00:00.062Z-0003-0000000000000001")
            assertReceives(it, 63, "1970-01-01T00:00:00.062Z-0004-0000000000000002", "1970-01-01T00:00:00.063Z-0000-0000000000000001")
        }
    }

    @Test
    fun receiveIsMonotonicWithLocalAndRemoteStutteringClocks() {
        clock().let {
            assertReceives(it, 73, "1970-01-01T00:00:00.071Z-0000-0000000000000002", "1970-01-01T00:00:00.073Z-0000-0000000000000001")
            assertReceives(it, 73, "1970-01-01T00:00:00.072Z-0000-0000000000000002", "1970-01-01T00:00:00.073Z-0001-0000000000000001")
            assertReceives(it, 74, "1970-01-01T00:00:00.073Z-0000-0000000000000002", "1970-01-01T00:00:00.074Z-0000-0000000000000001")
        }
        clock().let {
            assertReceives(it, 81, "1970-01-01T00:00:00.083Z-0000-0000000000000002", "1970-01-01T00:00:00.083Z-0001-0000000000000001")
            assertReceives(it, 82, "1970-01-01T00:00:00.083Z-0001-0000000000000002", "1970-01-01T00:00:00.083Z-0002-0000000000000001")
            assertReceives(it, 83, "1970-01-01T00:00:00.083Z-0002-0000000000000002", "1970-01-01T00:00:00.083Z-0003-0000000000000001")
            assertReceives(it, 84, "1970-01-01T00:00:00.083Z-0003-0000000000000002", "1970-01-01T00:00:00.084Z-0000-0000000000000001")
        }
    }

    @Test
    fun receiveIsMonotonicWithLocalAndRemoteRegressingClocks() {
        clock().let {
            assertReceives(it, 93, "1970-01-01T00:00:00.091Z-0000-0000000000000002", "1970-01-01T00:00:00.093Z-0000-0000000000000001")
            assertReceives(it, 92, "1970-01-01T00:00:00.092Z-0000-0000000000000002", "1970-01-01T00:00:00.093Z-0001-0000000000000001")
            assertReceives(it, 91, "1970-01-01T00:00:00.093Z-0000-0000000000000002", "1970-01-01T00:00:00.093Z-0002-0000000000000001")
        }
        clock().let {
            assertReceives(it, 101, "1970-01-01T00:00:00.103Z-0000-0000000000000002", "1970-01-01T00:00:00.103Z-0001-0000000000000001")
            assertReceives(it, 102, "1970-01-01T00:00:00.102Z-0000-0000000000000002", "1970-01-01T00:00:00.103Z-0002-0000000000000001")
            assertReceives(it, 103, "1970-01-01T00:00:00.101Z-0000-0000000000000002", "1970-01-01T00:00:00.103Z-0003-0000000000000001")
        }
    }

    @Test
    fun receiveFailsWithClockDrift() {
        now = 103
        assertThrows(HlcException.ClockDrift::class.java) {
            clock().receive(ts("1980-01-01T00:00:00.101Z-0000-0000000000000002"))
        }
    }

    @Test
    fun merkleInsertMatchesUpstreamSnapshotHashes() {
        val first = ts("2018-11-12T13:21:40.122Z-0000-0123456789ABCDEF")
        val second = ts("2018-11-13T13:21:40.122Z-0000-0123456789ABCDEF")
        val trie = MerkleTree().inserting(first).inserting(second)
        assertEquals(565_800_531, trie.root.hash)
        assertEquals(1_983_295_247, first.hash())
        assertEquals(1_469_038_940, second.hash())
    }

    @Test
    fun merkleDiffWithEmptyTrieReturnsEpoch() {
        val trie = MerkleTree().inserting(ts("2009-01-02T10:17:37.789Z-0000-0000testinguuid1"))
        assertEquals(0L, MerkleTree().diff(trie))
    }

    @Test
    fun merkleDiffReturnsUpstreamTimeDifference() {
        // Upstream overrides each timestamp's hash; each entry is in its own minute bucket.
        val first = linkedMapOf(
            millis("2018-11-13T13:20:40.122Z") to 1000,
            millis("2018-11-14T13:05:35.122Z") to 1100,
            millis("2018-11-15T22:19:00.122Z") to 1200,
        )
        val second = linkedMapOf(
            millis("2018-11-20T13:19:40.122Z") to 1300,
            millis("2018-11-25T13:19:40.122Z") to 1400,
        )
        val trie1 = MerkleTree.building(first)
        val trie2 = MerkleTree.building(second)
        assertEquals(788, trie1.root.hash)
        assertEquals(108, trie2.root.hash)
        assertEquals("2018-11-02T17:15:00Z", iso(trie1.diff(trie2)))

        val merged1 = MerkleTree.building(first + second)
        val merged2 = MerkleTree.building(second + first)
        assertEquals(888, merged1.root.hash)
        assertEquals(merged1.root.hash, merged2.root.hash)
    }

    @Test
    fun merklePruningKeepsUpstreamHashes() {
        val trie = MerkleTree.building(twelveMessages)
        assertEquals(2496, trie.root.hash)
        val pruned = trie.pruned()
        assertEquals(2496, pruned.root.hash)
        assertTrue(maxFanOut(pruned.root) <= 2)
    }

    @Test
    fun merkleDiffOfDifferentlyShapedAndPrunedTriesMatchesUpstream() {
        val trie = MerkleTree.building(twelveMessages)
        assertEquals("1970-01-01T00:00:00Z", iso(MerkleTree().diff(trie)))
        assertEquals("1970-01-01T00:00:00Z", iso(trie.diff(MerkleTree())))

        // Case 1: an older message changes the first of three branches, which pruning drops.
        val trie1 = MerkleTree.building(twelveMessages + (millis("2018-11-01T00:59:00.000Z") to 900))
        assertEquals("2018-11-01T00:54:00Z", iso(trie1.diff(trie)))
        assertEquals("2018-11-01T00:45:00Z", iso(trie1.pruned().diff(trie)))
        assertEquals("2018-11-01T00:45:00Z", iso(trie1.diff(trie.pruned())))
        assertEquals("2018-11-01T00:45:00Z", iso(trie1.pruned().diff(trie.pruned())))

        // Case 2: a second message changes the second key at the same level.
        val trie2 = MerkleTree.building(
            twelveMessages + (millis("2018-11-01T00:59:00.000Z") to 900) + (millis("2018-11-01T01:15:00.000Z") to 1422),
        )
        assertEquals("2018-11-01T00:54:00Z", iso(trie2.diff(trie)))
        assertEquals("2018-11-01T00:45:00Z", iso(trie2.pruned().diff(trie)))
        assertEquals("2018-11-01T00:45:00Z", iso(trie2.diff(trie.pruned())))
        assertEquals("2018-11-01T01:12:00Z", iso(trie2.pruned().diff(trie.pruned())))
    }

    private val twelveMessages = linkedMapOf(
        millis("2018-11-01T01:00:00.000Z") to 1000,
        millis("2018-11-01T01:09:00.000Z") to 1100,
        millis("2018-11-01T01:18:00.000Z") to 1200,
        millis("2018-11-01T01:27:00.000Z") to 1300,
        millis("2018-11-01T01:36:00.000Z") to 1400,
        millis("2018-11-01T01:45:00.000Z") to 1500,
        millis("2018-11-01T01:54:00.000Z") to 1600,
        millis("2018-11-01T02:03:00.000Z") to 1700,
        millis("2018-11-01T02:10:00.000Z") to 1800,
        millis("2018-11-01T02:19:00.000Z") to 1900,
        millis("2018-11-01T02:28:00.000Z") to 2000,
        millis("2018-11-01T02:37:00.000Z") to 2100,
    )

    private fun millis(iso: String) = Instant.parse(iso).toEpochMilli()
    private fun iso(millis: Long?) = Instant.ofEpochMilli(requireNotNull(millis)).toString()
    private fun maxFanOut(node: MerkleNode): Int =
        maxOf(node.children.size, node.children.values.maxOfOrNull(::maxFanOut) ?: 0)
}
