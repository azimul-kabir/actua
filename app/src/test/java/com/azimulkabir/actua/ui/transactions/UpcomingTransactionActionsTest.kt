package com.azimulkabir.actua.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Test

class UpcomingTransactionActionsTest {
    @Test fun offersActualsThreeScheduledTransactionActionsInOrder() {
        assertEquals(
            listOf("Post transaction", "Post transaction today", "Skip next scheduled date"),
            UpcomingTransactionAction.entries.map { it.label },
        )
    }
}
