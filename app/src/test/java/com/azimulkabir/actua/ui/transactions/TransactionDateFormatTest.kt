package com.azimulkabir.actua.ui.transactions

import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import com.azimulkabir.actua.ui.components.DateDisplay
import com.azimulkabir.actua.ui.components.parseStoredDate
import com.azimulkabir.actua.ui.components.storageDate
import java.time.LocalDate

class TransactionDateFormatTest {
    @Before fun useExplicitDisplayFormat() { DateDisplay.format = "DD/MM/YYYY" }
    @After fun resetDisplayFormat() { DateDisplay.format = "System default" }

    @Test fun formatsActualCompactDate() {
        assertEquals("05/09/2026", formatTransactionDate("20260905"))
    }

    @Test fun formatsIsoDate() {
        assertEquals("05/09/2026", formatTransactionDate("2026-09-05"))
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
