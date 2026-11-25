package com.azimulkabir.actua.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The server rejects a `/sync` request over 20 MB, so a large backlog is sent in batches (actual#9480). */
class SyncBatchesTest {
    private fun message(index: Int, valueBytes: Int) = CrdtMessage(
        timestamp = HlcTimestamp(1_700_000_000_000L + index, 0, "0000000000000001"),
        dataset = "notes", row = "note-$index", column = "note", value = "x".repeat(valueBytes),
    )

    @Test fun `nothing to send is still one empty request`() {
        assertEquals(listOf(emptyList<CrdtMessage>()), SyncBatches.split(emptyList()))
    }

    @Test fun `a small backlog is one request`() {
        val messages = (0 until 50).map { message(it, 100) }
        assertEquals(listOf(messages), SyncBatches.split(messages))
    }

    @Test fun `a large backlog is split in order without losing or repeating a message`() {
        val messages = (0 until 6).map { message(it, 1_500_000) }
        val batches = SyncBatches.split(messages)

        assertEquals(3, batches.size)
        assertEquals(messages, batches.flatten())
        assertTrue(batches.all { it.size == 2 })
    }

    @Test fun `a message over the limit gets a batch of its own`() {
        val big = message(0, SyncBatches.MAX_BYTES + 10)
        val small = message(1, 10)
        assertEquals(listOf(listOf(big), listOf(small)), SyncBatches.split(listOf(big, small)))
    }

    @Test fun `size counts bytes, not characters`() {
        val multibyte = (0 until 3).map { i ->
            CrdtMessage(HlcTimestamp(1_700_000_000_000L + i, 0, "0000000000000001"), "notes", "n$i", "note", "€".repeat(500_000))
        }
        // 500,000 euro signs are 1.5 MB, so two fit in one 4 MB batch and the third does not.
        assertEquals(listOf(2, 1), SyncBatches.split(multibyte).map { it.size })
    }
}
