package com.azimulkabir.actua.ui.navigation

import com.azimulkabir.actua.model.Transaction
import com.azimulkabir.actua.model.TransactionStatusFilter
import org.junit.Assert.assertSame
import org.junit.Test

class LoadedDataTest {
    private fun tx(id: String) = Transaction(
        id = id, date = "2026-01-01", payee = "P", category = "C", account = "A", amount = 1, cleared = true,
    )

    private val all = listOf(tx("1"), tx("2"))
    private val narrowed = listOf(tx("2"))

    @Test
    fun allFilterReusesTheLoadedHistory() {
        assertSame(all, transactionsForStatusFilter(TransactionStatusFilter.ALL, all, narrowed))
    }

    @Test
    fun narrowingFilterUsesItsOwnList() {
        TransactionStatusFilter.entries.filter { it != TransactionStatusFilter.ALL }.forEach {
            assertSame(narrowed, transactionsForStatusFilter(it, all, narrowed))
        }
    }
}
