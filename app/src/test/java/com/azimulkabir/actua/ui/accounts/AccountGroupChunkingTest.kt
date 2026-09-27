package com.azimulkabir.actua.ui.accounts

import com.azimulkabir.actua.model.Account
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountGroupChunkingTest {
    private fun account(id: String, groupId: String? = null, groupName: String? = null, groupSortOrder: Double = 0.0) =
        Account(name = id, balance = 0, type = "Checking", id = id, groupId = groupId, groupName = groupName, groupSortOrder = groupSortOrder)

    @Test fun `ungrouped accounts stay in a single untouched chunk`() {
        val accounts = listOf(account("a"), account("b"), account("c"))
        val chunks = accounts.chunkedByGroup()
        assertEquals(listOf(AccountGroupChunk(null, accounts)), chunks)
    }

    @Test fun `grouped accounts are bucketed by group ordered by group sort order`() {
        val accounts = listOf(
            account("a", groupId = "biz", groupName = "Business", groupSortOrder = 2.0),
            account("b", groupId = "personal", groupName = "Personal", groupSortOrder = 1.0),
            account("c", groupId = "biz", groupName = "Business", groupSortOrder = 2.0),
        )
        val chunks = accounts.chunkedByGroup()
        assertEquals(
            listOf(
                AccountGroupChunk("Personal", listOf(accounts[1])),
                AccountGroupChunk("Business", listOf(accounts[0], accounts[2])),
            ),
            chunks,
        )
    }

    @Test fun `accounts without a group land in a trailing ungrouped chunk`() {
        val accounts = listOf(
            account("a", groupId = "personal", groupName = "Personal", groupSortOrder = 1.0),
            account("b"),
        )
        val chunks = accounts.chunkedByGroup()
        assertEquals(
            listOf(
                AccountGroupChunk("Personal", listOf(accounts[0])),
                AccountGroupChunk(null, listOf(accounts[1])),
            ),
            chunks,
        )
    }
}
