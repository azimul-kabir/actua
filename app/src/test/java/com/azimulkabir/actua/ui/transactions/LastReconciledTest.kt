package com.azimulkabir.actua.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Actual stores `last_reconciled` as `Date.now().toString()` (#994). */
class LastReconciledTest {
    @Test
    fun readsActualsEpochMillisecondString() {
        assertEquals(1_760_000_000_000L, lastReconciledMillis("1760000000000"))
    }

    @Test
    fun missingOrUnreadableValuesMeanNotYetReconciled() {
        assertNull(lastReconciledMillis(null))
        assertNull(lastReconciledMillis(""))
        assertNull(lastReconciledMillis("0"))
        assertNull(lastReconciledMillis("yesterday"))
    }
}
