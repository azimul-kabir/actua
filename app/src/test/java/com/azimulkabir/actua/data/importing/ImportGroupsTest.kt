package com.azimulkabir.actua.data.importing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportGroupsTest {
    private fun candidate(row: Int, cents: Long = -1000) = ImportCandidate(row, 20261012, "Payee $row", "", cents)

    @Test
    fun `rows are grouped per account in review order with addAsNew indexes per group`() {
        val result = groupImportRows(listOf(
            ReviewedCandidate(candidate(1), "card"),
            ReviewedCandidate(candidate(2), "checking", addAsNew = true),
            ReviewedCandidate(candidate(3), "card", addAsNew = true),
            ReviewedCandidate(candidate(4), "checking"),
        ))

        assertEquals(listOf("card", "checking"), result.groups.map { it.accountId })
        assertEquals(listOf(1, 3), result.groups[0].candidates.map { it.sourceRow })
        assertEquals(setOf(1), result.groups[0].addAsNew)
        assertEquals(listOf(2, 4), result.groups[1].candidates.map { it.sourceRow })
        assertEquals(setOf(0), result.groups[1].addAsNew)
        assertEquals(4, result.importCount)
        assertTrue(result.unassigned.isEmpty())
    }

    @Test
    fun `rows without an account are left out and returned`() {
        val waiting = ReviewedCandidate(candidate(2), null)
        val result = groupImportRows(listOf(ReviewedCandidate(candidate(1), "card"), waiting))

        assertEquals(1, result.importCount)
        assertEquals(listOf(waiting), result.unassigned)
    }

    @Test
    fun `nothing to import when no row has an account`() {
        val result = groupImportRows(listOf(ReviewedCandidate(candidate(1), null)))
        assertTrue(result.groups.isEmpty())
        assertEquals(0, result.importCount)
    }
}
