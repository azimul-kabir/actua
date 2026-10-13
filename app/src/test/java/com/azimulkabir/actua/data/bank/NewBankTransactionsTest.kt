package com.azimulkabir.actua.data.bank

import org.junit.Assert.assertEquals
import org.junit.Test

class NewBankTransactionsTest {
    @Test
    fun `added ids join each account's unseen ids in order`() {
        val merged = mergeNewBankTransactions(
            current = mapOf("checking" to setOf("a", "b")),
            added = mapOf("checking" to listOf("b", "c"), "visa" to listOf("v")),
        )
        assertEquals(mapOf("checking" to listOf("a", "b", "c"), "visa" to listOf("v")), merged.mapValues { it.value.toList() })
    }

    @Test
    fun `each account keeps only its newest ids`() {
        val merged = mergeNewBankTransactions(
            current = mapOf("checking" to setOf("1", "2", "3")),
            added = mapOf("checking" to listOf("4", "5")),
            limit = 3,
        )
        assertEquals(listOf("3", "4", "5"), merged.getValue("checking").toList())
    }

    @Test
    fun `accounts without ids are dropped`() {
        assertEquals(emptyMap<String, Set<String>>(), mergeNewBankTransactions(emptyMap(), mapOf("checking" to emptyList())))
    }
}
