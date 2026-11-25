package com.azimulkabir.actua.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/** Actual restores its sync clock when a received batch fails to apply (actualbudget/actual#8904). */
class HybridLogicalClockRestoreTest {
    private val now = 1_700_000_000_000L

    @Test
    fun `restoring puts the clock back to its value before a receive`() {
        val clock = HybridLogicalClock(node = "0000000000000001", initial = HlcTimestamp(now, 3, "0000000000000001"), nowMillis = { now })
        val before = clock.current()

        clock.receive(HlcTimestamp(now + 60_000, 7, "0000000000000002"))
        clock.restore(before)

        assertEquals(before, clock.current())
    }

    @Test
    fun `a send after a restore does not reuse the received time`() {
        val clock = HybridLogicalClock(node = "0000000000000001", nowMillis = { now })
        val before = clock.current()
        clock.receive(HlcTimestamp(now + 60_000, 0, "0000000000000002"))
        clock.restore(before)

        assertEquals(now, clock.send().millis)
    }
}
