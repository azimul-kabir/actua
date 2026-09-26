package com.azimulkabir.actua.data.bank

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BankSyncMatcherTest {
    @Test fun matchesManualTransactionPostedOnADifferentDate() {
        val row = BankSyncMatchRow(financialId = "bank-1", date = 20260915, payeeId = "coffee-shop")
        val manual = BankSyncMatchCandidate(id = "manual-1", date = 20260912, payeeId = "coffee-shop", reconciled = false)

        val result = BankSyncMatcher.match(listOf(row), mapOf("bank-1" to listOf(manual)))

        assertEquals(manual, result["bank-1"])
    }

    @Test fun prefersExactPayeeMatchOverACloserDateWithADifferentPayee() {
        val row = BankSyncMatchRow(financialId = "bank-1", date = 20260915, payeeId = "coffee-shop")
        val closerButWrongPayee = BankSyncMatchCandidate("closer", date = 20260914, payeeId = "grocery-store", reconciled = false)
        val samePayeeFartherAway = BankSyncMatchCandidate("farther", date = 20260910, payeeId = "coffee-shop", reconciled = false)

        val result = BankSyncMatcher.match(
            listOf(row),
            mapOf("bank-1" to listOf(closerButWrongPayee, samePayeeFartherAway)),
        )

        assertEquals(samePayeeFartherAway, result["bank-1"])
    }

    @Test fun fallsBackToTheClosestDateWhenNoPayeeMatches() {
        val row = BankSyncMatchRow(financialId = "bank-1", date = 20260915, payeeId = "coffee-shop")
        val far = BankSyncMatchCandidate("far", date = 20260909, payeeId = "unknown", reconciled = false)
        val near = BankSyncMatchCandidate("near", date = 20260914, payeeId = "unknown", reconciled = false)

        val result = BankSyncMatcher.match(listOf(row), mapOf("bank-1" to listOf(far, near)))

        assertEquals(near, result["bank-1"])
    }

    @Test fun aPayeeMatchForOneRowBeatsAFallbackMatchProcessedEarlier() {
        // "fallback" would normally be the only candidate available to "no-payee-match", but
        // "payee-match" needs it via an exact payee hit — payee passes always run before any
        // row falls back, mirroring upstream Actual's two-pass ordering.
        val noPayeeMatch = BankSyncMatchRow(financialId = "bank-1", date = 20260915, payeeId = "unknown-payee")
        val payeeMatch = BankSyncMatchRow(financialId = "bank-2", date = 20260916, payeeId = "coffee-shop")
        val onlyCandidate = BankSyncMatchCandidate("only", date = 20260915, payeeId = "coffee-shop", reconciled = false)

        val result = BankSyncMatcher.match(
            listOf(noPayeeMatch, payeeMatch),
            mapOf("bank-1" to listOf(onlyCandidate), "bank-2" to listOf(onlyCandidate)),
        )

        assertEquals(onlyCandidate, result["bank-2"])
        assertNull(result["bank-1"])
    }

    @Test fun aCandidateIsNeverClaimedByTwoRows() {
        val first = BankSyncMatchRow(financialId = "bank-1", date = 20260915, payeeId = "coffee-shop")
        val second = BankSyncMatchRow(financialId = "bank-2", date = 20260915, payeeId = "coffee-shop")
        val onlyCandidate = BankSyncMatchCandidate("only", date = 20260915, payeeId = "coffee-shop", reconciled = false)

        val result = BankSyncMatcher.match(
            listOf(first, second),
            mapOf("bank-1" to listOf(onlyCandidate), "bank-2" to listOf(onlyCandidate)),
        )

        assertEquals(1, listOfNotNull(result["bank-1"], result["bank-2"]).size)
    }

    @Test fun noCandidatesLeavesTheRowUnmatched() {
        val row = BankSyncMatchRow(financialId = "bank-1", date = 20260915, payeeId = "coffee-shop")

        val result = BankSyncMatcher.match(listOf(row), emptyMap())

        assertNull(result["bank-1"])
    }

    @Test fun aReconciledCandidateCanStillBeMatchedSoTheCallerCanTreatItAsAlreadyAccountedFor() {
        val row = BankSyncMatchRow(financialId = "bank-1", date = 20260915, payeeId = "coffee-shop")
        val locked = BankSyncMatchCandidate("locked", date = 20260915, payeeId = "coffee-shop", reconciled = true)

        val result = BankSyncMatcher.match(listOf(row), mapOf("bank-1" to listOf(locked)))

        assertEquals(locked, result["bank-1"])
        assertEquals(true, result["bank-1"]?.reconciled)
    }

    @Test fun dayDistanceAcrossAMonthBoundaryIsComputedCorrectly() {
        val row = BankSyncMatchRow(financialId = "bank-1", date = 20260201, payeeId = "unknown")
        val oneDayBefore = BankSyncMatchCandidate("close", date = 20260131, payeeId = "unknown", reconciled = false)
        val sameNumericGapButFarther = BankSyncMatchCandidate("far", date = 20250201, payeeId = "unknown", reconciled = false)

        val result = BankSyncMatcher.match(
            listOf(row),
            mapOf("bank-1" to listOf(sameNumericGapButFarther, oneDayBefore)),
        )

        assertEquals(oneDayBefore, result["bank-1"])
    }
}
