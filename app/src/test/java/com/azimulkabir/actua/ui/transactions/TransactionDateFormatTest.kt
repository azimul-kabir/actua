package com.azimulkabir.actua.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.Test
import com.azimulkabir.actua.ui.components.parseStoredDate
import com.azimulkabir.actua.ui.components.storageDate
import java.time.LocalDate

class TransactionDateFormatTest {
    @Test fun formatsActualCompactDate() {
        assertEquals("05-Sep-26", formatTransactionDate("20260905"))
    }

    @Test fun formatsIsoDate() {
        assertEquals("05-Sep-26", formatTransactionDate("2026-09-05"))
    }

    @Test fun preservesHumanFriendlyFallbacks() {
        assertEquals("Today", formatTransactionDate("Today"))
    }

    @Test fun displayFormatRoundTripsToActualStorageDate() {
        val date = parseStoredDate("05-Sep-26")
        assertEquals(LocalDate.of(2026, 9, 5), date)
        assertEquals("20260905", storageDate(date!!))
    }
}
