package com.azimulkabir.actua.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCoreFixtureTest {
    @Test
    fun murmurHashMatchesUpstreamVectors() {
        val vectors = mapOf(
            "" to 0u,
            "abc" to 3_017_643_002u,
            "abcd" to 1_139_631_978u,
            "abcde" to 3_902_511_862u,
            "café" to 605_818_632u,
            "日本語" to 2_779_017_879u,
            "2018-11-12T13:21:40.122Z-0000-0123456789ABCDEF" to 1_983_295_247u,
            "9999-12-31T23:59:59.999Z-FFFF-FFFFFFFFFFFFFFFF" to 1_359_285_735u,
        )
        vectors.forEach { (input, expected) -> assertEquals(input, expected, MurmurHash3.hash(input)) }
    }

    @Test
    fun timestampParsesFormatsAndOrdersLikeUpstream() {
        val inputs = listOf(
            "1970-01-01T00:00:00.000Z-0000-0000000000000000",
            "2015-04-24T22:23:42.123Z-1000-0123456789ABCDEF",
            "9999-12-31T23:59:59.999Z-FFFF-FFFFFFFFFFFFFFFF",
        )
        inputs.forEach { assertEquals(it, HlcTimestamp.parse(it)?.toString()) }
        assertEquals(1_429_914_222_123, HlcTimestamp.parse(inputs[1])?.millis)
        assertTrue(HlcTimestamp.ZERO < HlcTimestamp.parse(inputs[1])!!)
        assertTrue(HlcTimestamp.parse(inputs[1])!! < HlcTimestamp.MAX)
        assertEquals(
            "2018-11-12T13:21:40.122Z-1000-0000000000000ABC",
            HlcTimestamp(1_542_028_900_122, 0x1000, "ABC").toString(),
        )
    }

    @Test
    fun timestampRejectsInvalidUpstreamVectors() {
        listOf(
            "", " ", "invalid",
            "1969-01-01T00:00:00.000Z-0000-0000000000000000",
            "10000-01-01T00:00:00.000Z-FFFF-FFFFFFFFFFFFFFFF",
            "9999-12-31T23:59:59.999Z-10000-FFFFFFFFFFFFFFFF",
            "9999-12-31T23:59:59.999Z-FFFF-10000000000000000",
        ).forEach { assertNull(it, HlcTimestamp.parse(it)) }
    }

    @Test
    fun minuteArithmeticMatchesTimestampParsing() {
        listOf(
            "1970-01-01T00:00:59.999Z-FFFF-0123456789ABCDEF",
            "2000-02-29T12:34:56.789Z-0000-0123456789ABCDEF",
            "2026-07-22T10:00:00.000Z-0000-a1b2c3d4e5f60718",
            "2100-03-01T00:00:00.000Z-0000-0123456789ABCDEF",
        ).forEach {
            assertEquals(HlcTimestamp.parse(it)!!.millis / 60_000, HlcTimestamp.minutesSinceEpoch(it))
        }
    }

    @Test
    fun merkleInsertAndDiffMatchUpstream() {
        fun timestamp(value: String) = HlcTimestamp.parse(value)!!
        var left = MerkleTree()
        left = left.inserting(timestamp("2018-11-13T13:20:40.122Z-0000-0123456789ABCDEF"))
        left = left.inserting(timestamp("2018-11-14T13:05:35.122Z-0000-0123456789ABCDEF"))
        left = left.inserting(timestamp("2018-11-15T22:19:00.122Z-0000-0123456789ABCDEF"))
        assertEquals(1_562_158_574, left.root.hash)

        var right = MerkleTree()
        right = right.inserting(timestamp("2018-11-20T13:19:40.122Z-0000-0123456789ABCDEF"))
        right = right.inserting(timestamp("2018-11-25T13:19:40.122Z-0000-0123456789ABCDEF"))
        assertEquals(-1_230_958_401, right.root.hash)
        assertEquals(1_541_178_900_000, left.diff(right))
        assertEquals(0L, MerkleTree().diff(left))
        assertNull(MerkleTree().diff(MerkleTree()))
    }

    @Test
    fun bulkMerkleBuildMatchesIncrementalInsertion() {
        val timestamps = listOf(
            "2018-11-01T00:47:12.000Z-0000-0123456789ABCDEF",
            "2018-11-01T00:47:39.000Z-0001-0123456789ABCDEF",
            "2018-11-01T01:15:00.000Z-0000-0123456789ABCDEF",
        ).map { HlcTimestamp.parse(it)!! }
        var incremental = MerkleTree()
        val buckets = mutableMapOf<Long, Int>()
        timestamps.forEach {
            incremental = incremental.inserting(it)
            val minute = it.millis / 60_000 * 60_000
            buckets[minute] = (buckets[minute] ?: 0) xor it.hash()
        }
        val bulk = MerkleTree.building(buckets)
        assertEquals(incremental, bulk)
        assertNull(bulk.diff(incremental))
    }

    @Test
    fun clockPreservesMonotonicityAndDetectsOverflow() {
        val now = 1_700_000_000_000L
        val clock = HybridLogicalClock(
            node = "0000000000000000",
            initial = HlcTimestamp(now + 60_000, 7, "ffffffffffffffff"),
            nowMillis = { now },
        )
        val sent = clock.send()
        assertEquals(now + 60_000, sent.millis)
        assertEquals(8, sent.counter)

        val overflow = HybridLogicalClock(
            node = "aaaaaaaaaaaaaaaa",
            initial = HlcTimestamp(now + 60_000, 0xffff, "aaaaaaaaaaaaaaaa"),
            nowMillis = { now },
        )
        assertThrows(HlcException.CounterOverflow::class.java) { overflow.send() }
    }
}
