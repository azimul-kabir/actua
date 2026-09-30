package com.azimulkabir.actua.data.bank

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class BankSyncDownloadFailureTest {
    @Test fun readTimeoutIsReportedAsTimedOutLikeActual() {
        val failure = BankSyncService.downloadFailure(SocketTimeoutException("Read timed out"))

        assertEquals("timed-out", failure.status)
        assertEquals("the bank took too long to respond. Try syncing again.", failure.problem)
    }

    @Test fun wrappedReadTimeoutIsStillATimeout() {
        assertEquals("timed-out", BankSyncService.downloadFailure(IOException("sync", SocketTimeoutException())).status)
    }

    @Test fun otherErrorsKeepTheirMessageAndAreStoredAsFailed() {
        val failure = BankSyncService.downloadFailure(IllegalStateException("This Actual server does not support GoCardless bank sync."))

        assertEquals("failed", failure.status)
        assertEquals("This Actual server does not support GoCardless bank sync.", failure.problem)
    }
}
