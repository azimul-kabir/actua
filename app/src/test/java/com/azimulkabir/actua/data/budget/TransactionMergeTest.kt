package com.azimulkabir.actua.data.budget

import com.azimulkabir.actua.data.budget.model.ActualTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** loot-core `merge.ts` / `shared/merge.ts` at 59fe126f, case by case. */
class TransactionMergeTest {
    @Test
    fun invalidPairsAreRejectedWithActualsReasons() {
        val a = row("a")
        assertEquals("One of the provided transactions does not exist", TransactionMerge.invalidReason(a, null))
        assertEquals(
            "Cannot merge transactions from different accounts",
            TransactionMerge.invalidReason(a, row("b", accountId = "savings")),
        )
        assertEquals(
            "Cannot merge transactions with different amounts",
            TransactionMerge.invalidReason(a, row("b", amountCents = -999)),
        )
        assertEquals(
            "Cannot merge transfers to different accounts",
            TransactionMerge.invalidReason(
                row("a", transferId = "x", payeeId = "to-savings"),
                row("b", transferId = "y", payeeId = "to-brokerage"),
            ),
        )
        assertNull(TransactionMerge.invalidReason(a, row("b")))
        // A transfer and a non-transfer can merge whatever their payees.
        assertNull(TransactionMerge.invalidReason(row("a", transferId = "x", payeeId = "to-savings"), row("b", payeeId = "store")))
    }

    @Test
    fun invalidPairsThrowBeforeAnythingIsPlanned() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            plan(row("a"), row("b", amountCents = 5))
        }
        assertEquals("Cannot merge transactions with different amounts", error.message)
        assertThrows(IllegalArgumentException::class.java) { plan(row("a"), row("a")) }
        assertThrows(IllegalArgumentException::class.java) { plan(row("a", parentId = "p"), row("b")) }
    }

    @Test
    fun keepsTheImportedRowThenTheImportedPayeeThenTheEarlierDate() {
        val manual = row("manual", date = 20260901)
        val imported = row("imported", date = 20260905, financialId = "bank-1")
        assertEquals("imported", TransactionMerge.keepDrop(manual, imported).first.id)
        assertEquals("imported", TransactionMerge.keepDrop(imported, manual).first.id)

        val withPayee = row("with-payee", date = 20260905, importedPayee = "STORE 123")
        assertEquals("with-payee", TransactionMerge.keepDrop(manual, withPayee).first.id)
        // imported_id wins over imported_payee.
        assertEquals("imported", TransactionMerge.keepDrop(withPayee, imported).first.id)

        val later = row("later", date = 20260910)
        assertEquals("manual", TransactionMerge.keepDrop(later, manual).first.id)
        // Same date: the second selected row is kept.
        val sameDay = row("same-day", date = 20260901)
        assertEquals("same-day", TransactionMerge.keepDrop(manual, sameDay).first.id)
    }

    @Test
    fun theKeptRowFillsEmptyFieldsFromTheDroppedOne() {
        val manual = row(
            "manual", date = 20260901, payeeId = "store", categoryId = "groceries",
            notes = "weekly shop", cleared = true, scheduleId = "schedule-1",
        )
        val imported = row("imported", date = 20260902, financialId = "bank-1", notes = "")

        val plan = plan(manual, imported)

        assertEquals("imported", plan.keptId)
        assertEquals(listOf("manual"), plan.tombstoneIds)
        val kept = plan.updated("imported")
        assertEquals("store", kept.payeeId)
        assertEquals("groceries", kept.categoryId)
        assertEquals("weekly shop", kept.notes)
        assertTrue(kept.cleared)
        assertFalse(kept.reconciled)
        assertEquals("schedule-1", kept.scheduleId)
        assertEquals("bank-1", kept.financialId)
        assertEquals(20260902, kept.date)
    }

    @Test
    fun theKeptRowKeepsItsOwnValuesAndIsReconciledIfEitherWas() {
        val kept = row("kept", financialId = "bank-1", payeeId = "bank-payee", categoryId = "fuel", notes = "pump 4")
        val dropped = row("dropped", payeeId = "store", categoryId = "groceries", notes = "shop", reconciled = true, cleared = true)

        val merged = plan(kept, dropped).updated("kept")

        assertEquals("bank-payee", merged.payeeId)
        assertEquals("fuel", merged.categoryId)
        assertEquals("pump 4", merged.notes)
        assertTrue(merged.reconciled)
        assertTrue(merged.cleared)
    }

    @Test
    fun aDroppedSplitsLinesMoveToTheKeptRow() {
        val imported = row("imported", financialId = "bank-1", categoryId = "groceries")
        val split = row("split", isParent = true)
        val lines = listOf(row("line-1", parentId = "split", amountCents = -600), row("line-2", parentId = "split", amountCents = -400))

        val plan = plan(imported, split, children = mapOf("split" to lines))

        assertEquals(listOf("split"), plan.tombstoneIds)
        val parent = plan.updated("imported")
        assertTrue(parent.isParent)
        assertNull(parent.categoryId)
        assertEquals(listOf("imported", "imported"), listOf("line-1", "line-2").map { plan.updated(it).parentId })
    }

    @Test
    fun whenBothAreSplitsTheDroppedSplitAndItsLinesAreDeleted() {
        val kept = row("kept", isParent = true, financialId = "bank-1")
        val dropped = row("dropped", isParent = true)
        val children = mapOf(
            "kept" to listOf(row("kept-line", parentId = "kept")),
            "dropped" to listOf(row("dropped-line", parentId = "dropped")),
        )

        val plan = plan(kept, dropped, children = children)

        assertEquals(listOf("dropped", "dropped-line"), plan.tombstoneIds)
        assertTrue(plan.updates.none { it.first.id == "kept-line" })
    }

    @Test
    fun aDeletedSplitLineThatIsATransferTakesItsOtherLegWithIt() {
        val kept = row("kept", isParent = true, financialId = "bank-1")
        val dropped = row("dropped", isParent = true)
        val transferLine = row("dropped-line", parentId = "dropped", transferId = "savings-leg")
        val savingsLeg = row("savings-leg", accountId = "savings", amountCents = 1000, transferId = "dropped-line")
        val children = mapOf("kept" to listOf(row("kept-line", parentId = "kept")), "dropped" to listOf(transferLine))

        val plan = plan(kept, dropped, children = children, extra = listOf(savingsLeg))

        assertEquals(listOf("dropped", "dropped-line", "savings-leg"), plan.tombstoneIds)
        assertNull(plan.updated("dropped-line").transferId)
    }

    @Test
    fun aTransferMergedWithAnOrdinaryRowKeepsTheTransferLinkAndPayee() {
        val transfer = row("transfer", date = 20260901, transferId = "savings-leg", payeeId = "to-savings")
        val savingsLeg = row("savings-leg", accountId = "savings", amountCents = 1000, transferId = "transfer", payeeId = "to-checking")
        val imported = row("imported", date = 20260902, financialId = "bank-1", payeeId = "bank-payee", categoryId = "groceries")

        val plan = plan(transfer, imported, extra = listOf(savingsLeg), onBudgetTransferPayees = setOf("to-savings"))

        assertEquals("imported", plan.keptId)
        assertEquals(listOf("transfer"), plan.tombstoneIds)
        val kept = plan.updated("imported")
        assertEquals("savings-leg", kept.transferId)
        assertEquals("to-savings", kept.payeeId)
        // An on-budget transfer has no category.
        assertNull(kept.categoryId)
        assertEquals("imported", plan.updated("savings-leg").transferId)
        // The dropped leg is unlinked before it is deleted, so its old partner survives.
        assertNull(plan.updated("transfer").transferId)
        assertFalse("savings-leg" in plan.tombstoneIds)
    }

    @Test
    fun aTransferToAnOffBudgetAccountKeepsTheMergedCategory() {
        val transfer = row("transfer", date = 20260901, transferId = "brokerage-leg", payeeId = "to-brokerage")
        val brokerageLeg = row("brokerage-leg", accountId = "brokerage", amountCents = 1000, transferId = "transfer")
        val imported = row("imported", date = 20260902, financialId = "bank-1", categoryId = "investing")

        val kept = plan(transfer, imported, extra = listOf(brokerageLeg), onBudgetTransferPayees = emptySet()).updated("imported")

        assertEquals("investing", kept.categoryId)
        assertEquals("to-brokerage", kept.payeeId)
    }

    @Test
    fun twoTransfersToTheSameAccountMergeBothLegs() {
        val a = row("a", date = 20260901, transferId = "a-leg", payeeId = "to-savings")
        val b = row("b", date = 20260902, financialId = "bank-1", transferId = "b-leg", payeeId = "to-savings")
        val aLeg = row("a-leg", accountId = "savings", date = 20260901, amountCents = 1000, transferId = "a", payeeId = "to-checking")
        val bLeg = row("b-leg", accountId = "savings", date = 20260902, amountCents = 1000, transferId = "b", payeeId = "to-checking")

        val plan = plan(a, b, extra = listOf(aLeg, bLeg), onBudgetTransferPayees = setOf("to-savings"))

        assertEquals("b", plan.keptId)
        // The earlier leg is kept in the other account.
        assertEquals(setOf("a", "b-leg"), plan.tombstoneIds.toSet())
        assertEquals("a-leg", plan.updated("b").transferId)
        assertEquals("b", plan.updated("a-leg").transferId)
    }

    @Test
    fun aTransferWhoseOtherLegIsMissingIsNotMerged() {
        val transfer = row("transfer", transferId = "gone", payeeId = "to-savings")

        assertThrows(ActualTransactionFormException.TransferPartnerMissing::class.java) {
            plan(transfer, row("other"))
        }
    }

    private fun TransactionMerge.Plan.updated(id: String): ActualTransaction =
        updates.single { it.first.id == id }.second

    private fun plan(
        a: ActualTransaction,
        b: ActualTransaction,
        children: Map<String, List<ActualTransaction>> = emptyMap(),
        extra: List<ActualTransaction> = emptyList(),
        onBudgetTransferPayees: Set<String> = emptySet(),
    ): TransactionMerge.Plan {
        val rows = (listOf(a, b) + extra + children.values.flatten()).associateBy { it.id }
        return TransactionMerge.plan(
            a, b,
            rowOf = rows::get,
            childrenOf = { children[it].orEmpty() },
            onBudgetTransferPayee = { it in onBudgetTransferPayees },
        )
    }

    private fun row(
        id: String,
        accountId: String = "checking",
        date: Int = 20260901,
        amountCents: Long = -1000,
        payeeId: String? = null,
        categoryId: String? = null,
        notes: String? = null,
        cleared: Boolean = false,
        reconciled: Boolean = false,
        transferId: String? = null,
        isParent: Boolean = false,
        parentId: String? = null,
        importedPayee: String? = null,
        scheduleId: String? = null,
        financialId: String? = null,
    ) = ActualTransaction(
        id = id, accountId = accountId, date = date, amountCents = amountCents,
        payeeId = payeeId, payeeName = null, categoryId = categoryId, categoryName = null,
        notes = notes, cleared = cleared, reconciled = reconciled, transferId = transferId,
        isParent = isParent, parentId = parentId, tombstone = false, sortOrder = null,
        importedPayee = importedPayee, scheduleId = scheduleId, transferAccountId = null,
        financialId = financialId,
    )
}
