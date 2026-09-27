package com.azimulkabir.actua.ui.accounts

import com.azimulkabir.actua.data.budget.model.ActualAccount
import com.azimulkabir.actua.data.budget.model.ActualAccountGroup
import com.azimulkabir.actua.data.budget.model.ActualAccountType
import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderAccountsChunkingTest {
    private fun account(
        id: String,
        offBudget: Boolean = false,
        closed: Boolean = false,
        groupId: String? = null,
    ) = ActualAccount(
        id, id, ActualAccountType.CHECKING, offBudget = offBudget, closed = closed, sortOrder = 0.0, balanceCents = 0,
        groupId = groupId,
    )

    @Test fun `accounts are split into on budget, off budget and closed sections`() {
        val onBudget = account("checking")
        val offBudget = account("investment", offBudget = true)
        val closed = account("old", closed = true)
        val chunks = buildReorderChunks(listOf(onBudget, offBudget, closed), emptyList())

        assertEquals(
            listOf(
                ReorderChunk("On budget", null, listOf(onBudget)),
                ReorderChunk("Off budget", null, listOf(offBudget)),
                ReorderChunk("Closed accounts", null, listOf(closed)),
            ),
            chunks,
        )
    }

    @Test fun `empty sections are omitted`() {
        val onBudget = account("checking")
        val chunks = buildReorderChunks(listOf(onBudget), emptyList())
        assertEquals(listOf(ReorderChunk("On budget", null, listOf(onBudget))), chunks)
    }

    @Test fun `a section's accounts are split by group, ordered by group sort order, with an ungrouped chunk last`() {
        val business = account("biz-checking", groupId = "biz")
        val personal = account("personal-checking", groupId = "personal")
        val ungrouped = account("misc")
        val groups = listOf(
            ActualAccountGroup("biz", "Business", sortOrder = 2.0),
            ActualAccountGroup("personal", "Personal", sortOrder = 1.0),
        )
        val chunks = buildReorderChunks(listOf(business, personal, ungrouped), groups)

        assertEquals(
            listOf(
                ReorderChunk("On budget", "Personal", listOf(personal)),
                ReorderChunk("On budget", "Business", listOf(business)),
                ReorderChunk("On budget", null, listOf(ungrouped)),
            ),
            chunks,
        )
    }

    @Test fun `a section with no grouped accounts stays a single ungrouped chunk`() {
        val a = account("a")
        val b = account("b")
        val chunks = buildReorderChunks(listOf(a, b), emptyList())
        assertEquals(listOf(ReorderChunk("On budget", null, listOf(a, b))), chunks)
    }

    @Test fun `serials number accounts 1-based per section, continuing across that section's groups`() {
        val business = account("biz-checking", groupId = "biz")
        val personal = account("personal-checking", groupId = "personal")
        val ungrouped = account("misc")
        val closed = account("old", closed = true)
        val groups = listOf(
            ActualAccountGroup("biz", "Business", sortOrder = 2.0),
            ActualAccountGroup("personal", "Personal", sortOrder = 1.0),
        )
        val chunks = buildReorderChunks(listOf(business, personal, ungrouped, closed), groups)

        assertEquals(
            mapOf(
                "personal-checking" to 1,
                "biz-checking" to 2,
                "misc" to 3,
                "old" to 1,
            ),
            serialsBySection(chunks),
        )
    }
}
